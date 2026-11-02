package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.model.KmdfRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class KmdfRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
) {

    suspend fun loadFromUrl(url: String): Result<List<KmdfRecord>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GeoDF-KMZ-Loader/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        return@withContext Result.failure(
                            Exception("O arquivo no link retornou HTTP 404 (Não Encontrado). Se o repositório no GitHub for privado, você pode carregar o arquivo KMDF.KMZ localmente pelo botão 'Abrir Arquivo do Dispositivo'.")
                        )
                    }
                    return@withContext Result.failure(Exception("Falha ao baixar KMZ: HTTP ${response.code}"))
                }

                val bytes = response.body?.bytes() ?: return@withContext Result.failure(Exception("Resposta vazia"))
                val records = parseKmzOrKmlBytes(bytes)
                if (records.isEmpty()) {
                    Result.failure(Exception("Nenhum registro de ponto ou tabela KM encontrado dentro do arquivo KMZ"))
                } else {
                    Result.success(records)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadFromUri(context: Context, uri: Uri): Result<List<KmdfRecord>> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val records = parseKmzOrKmlBytes(bytes)
                if (records.isEmpty()) {
                    Result.failure(Exception("Nenhum registro ou coordenada encontrado no arquivo KMZ/KML selecionado"))
                } else {
                    Result.success(records)
                }
            } ?: Result.failure(Exception("Não foi possível abrir o arquivo selecionado"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseKmzOrKmlBytes(bytes: ByteArray): List<KmdfRecord> {
        // Check if bytes start with PK (Zip archive)
        val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        return if (isZip) {
            parseKmzStream(ByteArrayInputStream(bytes))
        } else {
            parseKmlStream(ByteArrayInputStream(bytes))
        }
    }

    private fun parseKmzStream(inputStream: InputStream): List<KmdfRecord> {
        val records = mutableListOf<KmdfRecord>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name.endsWith(".kml", ignoreCase = true)) {
                    val kmlBytes = zis.readBytes()
                    records.addAll(parseKmlStream(ByteArrayInputStream(kmlBytes)))
                }
                entry = zis.nextEntry
            }
        }
        return records
    }

    fun parseKmlStream(inputStream: InputStream): List<KmdfRecord> {
        val records = mutableListOf<KmdfRecord>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var inPlacemark = false
            var currentName = ""
            var currentDesc = ""
            var currentCoords = ""
            val currentAttrs = mutableMapOf<String, String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (tagName?.lowercase()) {
                            "placemark" -> {
                                inPlacemark = true
                                currentName = ""
                                currentDesc = ""
                                currentCoords = ""
                                currentAttrs.clear()
                            }
                            "name" -> {
                                if (inPlacemark) currentName = parser.nextText() ?: ""
                            }
                            "description" -> {
                                if (inPlacemark) currentDesc = parser.nextText() ?: ""
                            }
                            "coordinates" -> {
                                if (inPlacemark) currentCoords = parser.nextText() ?: ""
                            }
                            "simpledata", "data" -> {
                                if (inPlacemark) {
                                    val attrName = parser.getAttributeValue(null, "name") ?: "attr"
                                    val attrVal = parser.nextText() ?: ""
                                    currentAttrs[attrName] = attrVal
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (tagName.equals("placemark", ignoreCase = true)) {
                            inPlacemark = false
                            val record = buildRecordFromPlacemark(
                                name = currentName,
                                description = currentDesc,
                                coordsRaw = currentCoords,
                                attrs = currentAttrs
                            )
                            if (record != null) {
                                records.add(record)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            // Log or fallback
        }
        return records
    }

    private fun buildRecordFromPlacemark(
        name: String,
        description: String,
        coordsRaw: String,
        attrs: Map<String, String>
    ): KmdfRecord? {
        val coords = parseCoordinates(coordsRaw) ?: return null

        // Extract Rodovia and KM from name or attributes
        val rodoviaAttr = attrs["RODOVIA"] ?: attrs["rodovia"] ?: attrs["VIA"] ?: ""
        val kmAttr = attrs["KM"] ?: attrs["km"] ?: attrs["QUILOMETRO"] ?: ""
        val trechoAttr = attrs["TRECHO"] ?: attrs["trecho"] ?: attrs["DESCRICAO"] ?: ""
        val raAttr = attrs["RA"] ?: attrs["ra"] ?: attrs["REGIAO"] ?: ""

        val finalRodovia = when {
            rodoviaAttr.isNotBlank() -> rodoviaAttr
            name.contains("DF-", ignoreCase = true) -> {
                val match = Regex("""(DF-\d{3})""", RegexOption.IGNORE_CASE).find(name)
                match?.value?.uppercase() ?: "DF"
            }
            else -> "DF"
        }

        val finalKm = when {
            kmAttr.toDoubleOrNull() != null -> kmAttr.toDouble()
            else -> {
                val match = Regex("""(?:KM|km)\s*(\d+(?:[.,]\d+)?)""").find(name + " " + description)
                match?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
            }
        }

        val finalTrecho = when {
            trechoAttr.isNotBlank() -> trechoAttr
            description.isNotBlank() && !description.contains("<") -> description.take(80)
            else -> name
        }

        return KmdfRecord(
            id = "km_${finalRodovia}_${finalKm}_${coords.first}_${coords.second}",
            rodovia = finalRodovia,
            km = finalKm,
            trecho = finalTrecho,
            ra = raAttr,
            latitude = coords.first,
            longitude = coords.second,
            descricao = description,
            attributes = attrs
        )
    }

    private fun parseCoordinates(coordsRaw: String): Pair<Double, Double>? {
        val trimmed = coordsRaw.trim()
        if (trimmed.isBlank()) return null
        // Format is usually lon,lat or lon,lat,alt
        val parts = trimmed.split(",", " ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            val lon = parts[0].toDoubleOrNull() ?: return null
            val lat = parts[1].toDoubleOrNull() ?: return null
            return Pair(lat, lon)
        }
        return null
    }

    fun getPreloadedKmdfTable(): List<KmdfRecord> {
        return listOf(
            // DF-001 (EPCT - Estrada Parque Contorno)
            KmdfRecord("1", "DF-001", 0.0, "Balão do Colorado / Saída Norte", "SOBRADINHO", -15.7082, -47.8890),
            KmdfRecord("2", "DF-001", 10.0, "Entroncamento DF-005 (Taquari / Lago Norte)", "LAGO NORTE", -15.7250, -47.8540),
            KmdfRecord("3", "DF-001", 20.0, "Região do Itapoã / Paranoá", "PARANOÁ", -15.7580, -47.7850),
            KmdfRecord("4", "DF-001", 30.0, "Acesso a São Sebastião e DF-473", "SÃO SEBASTIÃO", -15.8920, -47.7710),
            KmdfRecord("5", "DF-001", 45.0, "Entroncamento DF-140 (Tororó)", "SÃO SEBASTIÃO", -15.9350, -47.8380),
            KmdfRecord("6", "DF-001", 55.0, "Região de Santa Maria / DF-290", "SANTA MARIA", -16.0120, -47.9850),
            KmdfRecord("7", "DF-001", 65.0, "Trecho Gama Leste / Catetinho", "GAMA", -15.9860, -48.0420),
            KmdfRecord("8", "DF-001", 75.0, "Acesso Taguatinga Sul e Samambaia", "TAGUATINGA", -15.8650, -48.0750),
            KmdfRecord("9", "DF-001", 85.0, "Trecho Ceilândia Centro / QNL", "CEILÂNDIA", -15.8120, -48.1150),
            KmdfRecord("10", "DF-001", 100.0, "Acesso a Brazlândia / DF-430", "BRAZLÂNDIA", -15.7250, -48.1450),
            KmdfRecord("11", "DF-001", 115.0, "Água Fria / Floresta Nacional de Brasília", "TAGUATINGA", -15.6890, -48.0650),
            KmdfRecord("12", "DF-001", 132.0, "Fechamento do anel viário no Colorado", "SOBRADINHO", -15.7082, -47.8890),

            // DF-002 (Eixo Rodoviário / Eixão de Brasília)
            KmdfRecord("13", "DF-002", 0.0, "Ponte do Bragueto / Saída Norte (Asa Norte)", "PLANO PILOTO", -15.7472, -47.8894),
            KmdfRecord("14", "DF-002", 2.0, "Eixão Norte (Altura SQN 114 / 214)", "PLANO PILOTO", -15.7600, -47.8875),
            KmdfRecord("15", "DF-002", 4.0, "Eixão Norte (Altura SQN 108 / 208)", "PLANO PILOTO", -15.7750, -47.8850),
            KmdfRecord("16", "DF-002", 6.0, "Eixão Norte (Altura SQN 102 / Setor Comercial)", "PLANO PILOTO", -15.7890, -47.8830),
            KmdfRecord("17", "DF-002", 7.0, "Plataforma Rodoviária do Plano Piloto (KM Zero)", "PLANO PILOTO", -15.7942, -47.8822),
            KmdfRecord("18", "DF-002", 8.5, "Eixão Sul (Altura SQS 102 / 202)", "PLANO PILOTO", -15.8050, -47.8890),
            KmdfRecord("19", "DF-002", 11.0, "Eixão Sul (Altura SQS 108 / 208)", "PLANO PILOTO", -15.8230, -47.9050),
            KmdfRecord("20", "DF-002", 13.5, "Eixão Sul (Altura SQS 116 / 216)", "PLANO PILOTO", -15.8420, -47.9250),
            KmdfRecord("21", "DF-002", 15.0, "Trevo Sul / Acesso Aeroporto e EPAR", "PLANO PILOTO", -15.8580, -47.9350),

            // DF-003 (EPIA - Estrada Parque Indústria e Abastecimento)
            KmdfRecord("22", "DF-003", 0.0, "Trevo do Torto / Saída Norte", "SOBRADINHO", -15.7150, -47.9050),
            KmdfRecord("23", "DF-003", 5.0, "Parque Nacional / Água Mineral", "PLANO PILOTO", -15.7480, -47.9260),
            KmdfRecord("24", "DF-003", 9.0, "Setor Noroeste / SMU", "PLANO PILOTO", -15.7720, -47.9400),
            KmdfRecord("25", "DF-003", 13.0, "Rodoferroviária / EPTG / SIA", "SIA", -15.7980, -47.9520),
            KmdfRecord("26", "DF-003", 17.0, "ParkShopping / Rodoviária Interestadual", "GUARA", -15.8320, -47.9550),
            KmdfRecord("27", "DF-003", 21.0, "Candangolândia / Núcleo Bandeirante", "NÚCLEO BANDEIRANTE", -15.8620, -47.9540),
            KmdfRecord("28", "DF-003", 26.0, "Catetinho / Saída Sul", "PARK WAY", -15.9120, -47.9620),

            // DF-007 (EPTG - Estrada Parque Taguatinga)
            KmdfRecord("29", "DF-007", 0.0, "Viaduto de Interligação EPIA / SIA", "SIA", -15.8050, -47.9650),
            KmdfRecord("30", "DF-007", 4.0, "Guará I e II / Lúcio Costa", "GUARA", -15.8150, -47.9980),
            KmdfRecord("31", "DF-007", 8.0, "Vicente Pires / Águas Claras Norte", "VICENTE PIRES", -15.8250, -48.0350),
            KmdfRecord("32", "DF-007", 13.0, "Entrada de Taguatinga / Pistão Norte", "TAGUATINGA", -15.8320, -48.0620),

            // DF-075 (EPNB - Estrada Parque Núcleo Bandeirante)
            KmdfRecord("33", "DF-075", 0.0, "Interligação com EPIA e Candangolândia", "NÚCLEO BANDEIRANTE", -15.8650, -47.9580),
            KmdfRecord("34", "DF-075", 4.0, "Riacho Fundo I / ADE", "RIACHO FUNDO", -15.8820, -48.0120),
            KmdfRecord("35", "DF-075", 8.0, "Riacho Fundo II / Recanto das Emas", "RIACHO FUNDO II", -15.8950, -48.0550),
            KmdfRecord("36", "DF-075", 12.0, "Samambaia Sul / DF-001", "SAMAMBAIA", -15.8890, -48.0850),

            // DF-085 (Linha Verde)
            KmdfRecord("37", "DF-085", 0.0, "Início Linha Verde (Águas Claras)", "ÁGUAS CLARAS", -15.8380, -48.0200),
            KmdfRecord("38", "DF-085", 6.0, "Taguatinga Centro", "TAGUATINGA", -15.8350, -48.0600),
            KmdfRecord("39", "DF-085", 12.0, "Samambaia Norte", "SAMAMBAIA", -15.8650, -48.0950),

            // DF-095 (Via Estrutural)
            KmdfRecord("40", "DF-095", 0.0, "Interligação EPIA Norte / SIA", "SIA", -15.7820, -47.9550),
            KmdfRecord("41", "DF-095", 5.0, "Estrutural / Cidade do Automóvel", "SCIA", -15.7860, -47.9950),
            KmdfRecord("42", "DF-095", 10.0, "Vicente Pires Norte", "VICENTE PIRES", -15.8020, -48.0520),
            KmdfRecord("43", "DF-095", 13.5, "Pistão Norte (Taguatinga / Ceilândia)", "TAGUATINGA", -15.8110, -48.0790),

            // DF-140 (São Sebastião - Tororó)
            KmdfRecord("44", "DF-140", 0.0, "Início na DF-001 (São Sebastião)", "SÃO SEBASTIÃO", -15.9280, -47.8325),
            KmdfRecord("45", "DF-140", 5.0, "Residencial Tororó", "SÃO SEBASTIÃO", -15.9620, -47.8250),
            KmdfRecord("46", "DF-140", 10.0, "Acesso Barreiro / Divisa GO", "SÃO SEBASTIÃO", -16.0120, -47.8180),

            // DF-150 (Colorado - Fercal)
            KmdfRecord("47", "DF-150", 0.0, "Balão do Colorado", "SOBRADINHO", -15.7050, -47.8870),
            KmdfRecord("48", "DF-150", 6.0, "Grande Colorado", "SOBRADINHO", -15.6720, -47.8820),
            KmdfRecord("49", "DF-150", 12.0, "Sobradinho II", "SOBRADINHO II", -15.6250, -47.8780),
            KmdfRecord("50", "DF-150", 22.0, "Fercal / Fábricas de Cimento", "FERCAL", -15.5620, -47.8650),

            // DF-250 (Paranoá - Planaltina)
            KmdfRecord("51", "DF-250", 0.0, "Itapoã / Paranoá", "ITAPOÃ", -15.7480, -47.7720),
            KmdfRecord("52", "DF-250", 8.0, "Acesso PAD-DF e Sobradinho", "PARANOÁ", -15.7320, -47.7120),

            // DF-290 (Gama - Santa Maria)
            KmdfRecord("53", "DF-290", 0.0, "Santa Maria / BR-040", "SANTA MARIA", -16.0250, -47.9620),
            KmdfRecord("54", "DF-290", 10.0, "Gama Sul / Engenho das Lajes", "GAMA", -16.0350, -48.0450)
        )
    }
}
