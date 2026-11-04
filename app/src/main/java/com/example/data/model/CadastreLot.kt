package com.example.data.model

import java.util.Locale

data class CadastreLot(
    val objectId: Long = 0,
    val cipu: String = "",
    val ciu: String = "",
    val projeto: String = "",
    val setor: String = "",
    val quadra: String = "",
    val conjunto: String = "",
    val lote: String = "",
    val endCartorial: String = "",
    val endUsual: String = "",
    val ra: String = "",
    val cep: String = "",
    val situacao: String = "",
    val dataRegistro: String = "",
    val areaProjeto: Double = 0.0,
    val normaVigente: String = "",
    val codigoParametro: String = "",
    val uosParametro: String = "",
    val uosCores: String = "",
    val faixaArea: String = "",
    val normaAnterior: String = "",
    val onalt: String = "",
    val onaltNorma: String = "",
    val onaltExcecao: String = "",
    val planta: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val rings: List<List<Pair<Double, Double>>> = emptyList()
) {
    val displayAddress: String
        get() = when {
            endUsual.isNotBlank() && endUsual != "Null" -> endUsual
            endCartorial.isNotBlank() && endCartorial != "Null" -> endCartorial
            quadra.isNotBlank() && lote.isNotBlank() -> "$setor $quadra Lote $lote".trim()
            else -> "Residência $ciu"
        }

    val displayCiu: String
        get() = if (ciu.isNotBlank()) "ct_CIU: $ciu" else "CIU não informado"

    val quadraDisplay: String
        get() = when {
            quadra.isNotBlank() && setor.isNotBlank() -> "$setor, $quadra"
            quadra.isNotBlank() -> quadra
            setor.isNotBlank() -> setor
            else -> "Não informada"
        }

    val numeroCasaDisplay: String
        get() = when {
            lote.isNotBlank() && conjunto.isNotBlank() -> "$lote, $conjunto"
            lote.isNotBlank() -> lote
            conjunto.isNotBlank() -> conjunto
            else -> "Não informado"
        }

    val displaySubtitle: String
        get() = buildString {
            if (ra.isNotBlank() && ra != "Null") append(ra.uppercase())
            if (cep.isNotBlank() && cep != "Null") {
                if (isNotEmpty()) append(" • ")
                append("CEP $formattedCep")
            }
        }

    val formattedCep: String
        get() {
            val clean = cep.filter { it.isDigit() }
            return if (clean.length == 8) {
                "${clean.substring(0, 5)}-${clean.substring(5)}"
            } else cep
        }

    val formattedArea: String
        get() = if (areaProjeto > 0.0) {
            String.format(Locale.getDefault(), "%,.2f m²", areaProjeto)
        } else {
            "Não informada"
        }

    val formattedDecimalCoords: String
        get() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    val formattedDms: String
        get() {
            if (latitude == 0.0 && longitude == 0.0) return "Indisponível"
            return "${toDms(latitude, isLat = true)} ${toDms(longitude, isLat = false)}"
        }

    private fun toDms(coordinate: Double, isLat: Boolean): String {
        val absolute = Math.abs(coordinate)
        val degrees = absolute.toInt()
        val minutesDouble = (absolute - degrees) * 60
        val minutes = minutesDouble.toInt()
        val seconds = (minutesDouble - minutes) * 60
        val direction = if (isLat) {
            if (coordinate >= 0) "N" else "S"
        } else {
            if (coordinate >= 0) "E" else "W"
        }
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", degrees, minutes, seconds, direction)
    }
}
