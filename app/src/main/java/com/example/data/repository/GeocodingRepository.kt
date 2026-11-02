package com.example.data.repository

import com.example.data.model.CadastreLot
import com.example.data.model.WmsLayerInfo
import com.example.data.model.WmsServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import com.example.data.network.NetworkClient
import kotlin.math.sqrt

class GeocodingRepository(
    private val client: OkHttpClient = NetworkClient.okHttpClient
) {
    companion object {
        const val WMS_SERVER_URL = "https://geoportal.ide.df.gov.br/server/services/Publico/cadastro_territorial_geoportal/MapServer/WMSServer"
        const val MAP_SERVER_REST_URL = "https://geoportal.ide.df.gov.br/server/rest/services/Publico/cadastro_territorial_geoportal/MapServer"
        const val LAYER_LOTES_ID = "9"
    }

    suspend fun searchLots(
        query: String,
        raFilter: String? = null,
        limit: Int = 30
    ): Result<List<CadastreLot>> = withContext(Dispatchers.IO) {
        try {
            val trimmed = query.trim()
            if (trimmed.isEmpty() && raFilter.isNullOrBlank()) {
                return@withContext Result.success(emptyList())
            }

            // Build SQL where clause for ArcGIS query
            val sanitized = trimmed.replace("'", "''")
            val isNumeric = sanitized.all { it.isDigit() }

            val whereClause = buildString {
                if (sanitized.isNotBlank()) {
                    if (isNumeric && sanitized.length in 5..8) {
                        // Likely a CEP or CIPU
                        append("(cod_qdui_cep LIKE '%$sanitized%' OR cod_puvetor_cipu = $sanitized)")
                    } else if (isNumeric && sanitized.length <= 4) {
                        append("(txt_qdui_quadra LIKE '%$sanitized%' OR txt_qdui_lote LIKE '%$sanitized%' OR cod_puvetor_cipu = $sanitized)")
                    } else {
                        append("(")
                        append("UPPER(txt_qdui_end_cartorial) LIKE '%${sanitized.uppercase()}%' OR ")
                        append("UPPER(txt_qdui_end_usual) LIKE '%${sanitized.uppercase()}%' OR ")
                        append("UPPER(txt_qdui_quadra) LIKE '%${sanitized.uppercase()}%' OR ")
                        append("UPPER(txt_qdui_setor) LIKE '%${sanitized.uppercase()}%' OR ")
                        append("UPPER(cod_puvetor_ra_resolved) LIKE '%${sanitized.uppercase()}%'")
                        append(")")
                    }
                } else {
                    append("1=1")
                }

                if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                    append(" AND UPPER(cod_puvetor_ra_resolved) = '${raFilter.uppercase().replace("'", "''")}'")
                }
            }

            val encodedWhere = URLEncoder.encode(whereClause, StandardCharsets.UTF_8.toString())
            val outFields = "*"
            val url = "$MAP_SERVER_REST_URL/$LAYER_LOTES_ID/query?" +
                    "where=$encodedWhere&" +
                    "outFields=$outFields&" +
                    "returnGeometry=true&" +
                    "outSR=4326&" +
                    "resultRecordCount=$limit&" +
                    "f=json"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GeoDF-Android-App/1.0")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Erro na resposta do servidor: HTTP ${response.code}"))
                }
                val body = response.body?.string() ?: return@withContext Result.success(emptyList())
                val json = JSONObject(body)

                if (json.has("error")) {
                    val err = json.getJSONObject("error")
                    return@withContext Result.failure(Exception(err.optString("message", "Erro na consulta ao servidor")))
                }

                val features = json.optJSONArray("features") ?: JSONArray()
                val lots = mutableListOf<CadastreLot>()
                for (i in 0 until features.length()) {
                    val featObj = features.getJSONObject(i)
                    parseLot(featObj)?.let { lots.add(it) }
                }
                Result.success(lots)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun reverseGeocode(lat: Double, lon: Double): Result<List<CadastreLot>> = withContext(Dispatchers.IO) {
        try {
            // First try a tight envelope ~60m around point
            val deltaSmall = 0.0006
            var lots = queryEnvelope(
                minLon = lon - deltaSmall,
                minLat = lat - deltaSmall,
                maxLon = lon + deltaSmall,
                maxLat = lat + deltaSmall
            )

            // If nothing found, widen to ~200m
            if (lots.isEmpty()) {
                val deltaLarge = 0.002
                lots = queryEnvelope(
                    minLon = lon - deltaLarge,
                    minLat = lat - deltaLarge,
                    maxLon = lon + deltaLarge,
                    maxLat = lat + deltaLarge
                )
            }

            // Sort lots by proximity to the target point
            val sorted = lots.sortedBy { lot ->
                haversineDistance(lat, lon, lot.latitude, lot.longitude)
            }

            Result.success(sorted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryEnvelope(minLon: Double, minLat: Double, maxLon: Double, maxLat: Double): List<CadastreLot> {
        val geomEnvelope = "$minLon,$minLat,$maxLon,$maxLat"
        val url = "$MAP_SERVER_REST_URL/$LAYER_LOTES_ID/query?" +
                "geometry=$geomEnvelope&" +
                "geometryType=esriGeometryEnvelope&" +
                "inSR=4326&" +
                "spatialRel=esriSpatialRelIntersects&" +
                "outFields=*&" +
                "returnGeometry=true&" +
                "outSR=4326&" +
                "resultRecordCount=15&" +
                "f=json"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GeoDF-Android-App/1.0")
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val features = json.optJSONArray("features") ?: return emptyList()
            val result = mutableListOf<CadastreLot>()
            for (i in 0 until features.length()) {
                val featObj = features.getJSONObject(i)
                parseLot(featObj)?.let { result.add(it) }
            }
            return result
        }
    }

    suspend fun getWmsCapabilities(): Result<WmsServiceInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$WMS_SERVER_URL?request=GetCapabilities&service=WMS"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GeoDF-Android-App/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code} ao obter GetCapabilities"))
                }
                val xml = response.body?.string() ?: ""
                val info = parseWmsCapabilitiesXml(xml)
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildWmsGetMapUrl(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
        width: Int = 800,
        height: Int = 800,
        layers: List<String> = listOf("5", "2", "1") // 5: Lotes Registrados, 2: Quadras, 1: Setor
    ): String {
        val layersParam = layers.joinToString(",")
        return "$WMS_SERVER_URL?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=$layersParam&STYLES=&SRS=EPSG:4326&BBOX=$minLon,$minLat,$maxLon,$maxLat&WIDTH=$width&HEIGHT=$height&FORMAT=image/png&TRANSPARENT=TRUE"
    }

    fun buildMapServerExportUrl(
        minLon: Double,
        minLat: Double,
        maxLon: Double,
        maxLat: Double,
        width: Int = 800,
        height: Int = 800,
        layers: List<String> = listOf("9", "12", "13", "5") // 9: Lotes Registrados, 12: Quadras, 13: Setor, 5: Edificação
    ): String {
        val layersParam = layers.joinToString(",")
        return "$MAP_SERVER_REST_URL/export?bbox=$minLon,$minLat,$maxLon,$maxLat&bboxSR=4326&layers=show:$layersParam&size=$width,$height&imageSR=4326&format=png32&transparent=true&f=image"
    }

    private fun parseLot(featObj: JSONObject): CadastreLot? {
        val attrs = featObj.optJSONObject("attributes") ?: return null
        val geom = featObj.optJSONObject("geometry")

        val ringsList = mutableListOf<List<Pair<Double, Double>>>()
        var centerLat = 0.0
        var centerLon = 0.0

        if (geom != null && geom.has("rings")) {
            val ringsArray = geom.getJSONArray("rings")
            var totalPoints = 0
            var sumLat = 0.0
            var sumLon = 0.0

            for (r in 0 until ringsArray.length()) {
                val ring = ringsArray.getJSONArray(r)
                val ringPoints = mutableListOf<Pair<Double, Double>>()
                for (p in 0 until ring.length()) {
                    val pt = ring.getJSONArray(p)
                    val lon = pt.getDouble(0)
                    val lat = pt.getDouble(1)
                    ringPoints.add(Pair(lon, lat))
                    sumLon += lon
                    sumLat += lat
                    totalPoints++
                }
                ringsList.add(ringPoints)
            }

            if (totalPoints > 0) {
                centerLon = sumLon / totalPoints
                centerLat = sumLat / totalPoints
            }
        }

        return CadastreLot(
            objectId = attrs.optLong("objectid", 0L),
            cipu = attrs.optString("cod_puvetor_cipu", ""),
            ciu = attrs.optString("cod_puvetor_ciu", ""),
            projeto = attrs.optString("nom_puvetor_projeto", ""),
            setor = attrs.optString("txt_qdui_setor", ""),
            quadra = attrs.optString("txt_qdui_quadra", ""),
            conjunto = attrs.optString("txt_qdui_conjunto", ""),
            lote = attrs.optString("txt_qdui_lote", ""),
            endCartorial = attrs.optString("txt_qdui_end_cartorial", ""),
            endUsual = attrs.optString("txt_qdui_end_usual", ""),
            ra = attrs.optString("cod_puvetor_ra_resolved", ""),
            cep = attrs.optString("cod_qdui_cep", ""),
            situacao = attrs.optString("cod_qdui_situacao_resolved", ""),
            dataRegistro = attrs.optString("dat_qdui_registro", ""),
            areaProjeto = attrs.optDouble("vlr_qdui_area_projeto", 0.0),
            normaVigente = attrs.optString("nom_normap_nor_vigente", ""),
            codigoParametro = attrs.optString("cod_normap_cod_parametro", ""),
            uosParametro = attrs.optString("cat_normap_uos_parametro", ""),
            uosCores = attrs.optString("cat_normap_uso", ""),
            faixaArea = attrs.optString("txt_normap_faixa_area", ""),
            normaAnterior = attrs.optString("txt_qdui_norma_anterior", ""),
            onalt = attrs.optString("cod_qdui_onalt_resolved", ""),
            onaltNorma = attrs.optString("txt_qdui_onalt_norma", ""),
            onaltExcecao = attrs.optString("txt_qdui_onalt_excecao", ""),
            planta = attrs.optString("txt_puvetor_planta", ""),
            latitude = centerLat,
            longitude = centerLon,
            rings = ringsList
        )
    }

    private fun parseWmsCapabilitiesXml(xml: String): WmsServiceInfo {
        val defaultInfo = WmsServiceInfo()
        if (xml.isBlank()) return defaultInfo

        // Simple XML token extraction
        fun extractTag(tag: String): String {
            val start = xml.indexOf("<$tag>")
            if (start == -1) return ""
            val end = xml.indexOf("</$tag>", start)
            if (end == -1) return ""
            var content = xml.substring(start + tag.length + 2, end).trim()
            if (content.startsWith("<![CDATA[") && content.endsWith("]]>")) {
                content = content.removeSurrounding("<![CDATA[", "]]>")
            }
            return content
        }

        val title = extractTag("Title").ifBlank { defaultInfo.title }
        val abstract = extractTag("Abstract").ifBlank { defaultInfo.abstractText }

        return defaultInfo.copy(
            title = title,
            abstractText = abstract
        )
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
