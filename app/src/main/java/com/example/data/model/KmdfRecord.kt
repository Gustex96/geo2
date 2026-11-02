package com.example.data.model

import java.util.Locale

data class KmdfRecord(
    val id: String,
    val rodovia: String,
    val km: Double,
    val trecho: String,
    val ra: String,
    val latitude: Double,
    val longitude: Double,
    val descricao: String = "",
    val attributes: Map<String, String> = emptyMap()
) {
    val kmDisplay: String
        get() = if (km % 1.0 == 0.0) "KM ${km.toInt()}" else "KM $km"

    val displayTitle: String
        get() = "$rodovia $kmDisplay"

    val displaySubtitle: String
        get() = buildString {
            if (trecho.isNotBlank()) append(trecho)
            if (ra.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(ra.uppercase())
            }
        }

    val formattedDecimalCoords: String
        get() = String.format(Locale.US, "%.6f, %.6f", latitude, longitude)

    val formattedDms: String
        get() {
            if (latitude == 0.0 && longitude == 0.0) return "Indisponível"
            return "${toDms(latitude, true)} ${toDms(longitude, false)}"
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
