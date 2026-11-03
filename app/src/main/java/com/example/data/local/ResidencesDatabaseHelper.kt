package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.data.model.CadastreLot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class ResidencesDatabaseHelper(private val context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "residences.db"
        const val DATABASE_VERSION = 1
        private const val TAG = "ResidencesDbHelper"

        @Volatile
        private var instance: ResidencesDatabaseHelper? = null

        fun getInstance(context: Context): ResidencesDatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: ResidencesDatabaseHelper(context.applicationContext).also {
                    it.ensureDatabaseCopied()
                    instance = it
                }
            }
        }
    }

    private fun ensureDatabaseCopied() {
        val dbPath = context.getDatabasePath(DATABASE_NAME)
        if (!dbPath.exists()) {
            dbPath.parentFile?.mkdirs()
            try {
                context.assets.open(DATABASE_NAME).use { input ->
                    FileOutputStream(dbPath).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "Database successfully copied from assets to ${dbPath.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database from assets", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Database is copied pre-populated from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade needed yet
    }

    suspend fun search(
        query: String,
        raFilter: String? = null,
        limit: Int = 40
    ): List<CadastreLot> = withContext(Dispatchers.IO) {
        ensureDatabaseCopied()
        val results = mutableListOf<CadastreLot>()
        val db = readableDatabase ?: return@withContext emptyList()

        val trimmed = query.trim()
        val sanitized = trimmed.replace("'", "''")

        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<String>()

        if (sanitized.isNotBlank()) {
            val isNumeric = sanitized.all { it.isDigit() }
            if (isNumeric && sanitized.length in 5..8) {
                whereClauses.add("(cep LIKE ? OR ciu LIKE ?)")
                args.add("%$sanitized%")
                args.add("%$sanitized%")
            } else {
                whereClauses.add("(endereco LIKE ? OR ciu LIKE ? OR setor LIKE ? OR quadra LIKE ? OR lote LIKE ? OR cep LIKE ?)")
                val pattern = "%$sanitized%"
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
                args.add(pattern)
            }
        }

        if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
            whereClauses.add("UPPER(ra) = ?")
            args.add(raFilter.uppercase())
        }

        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else {
            ""
        }

        val sql = "SELECT id, ciu, endereco, setor, quadra, conjunto, lote, cep, ra, area_construida, area_terreno, perimetro, origem, latitude, longitude " +
                "FROM residences $whereSql ORDER BY id ASC LIMIT $limit"

        try {
            db.rawQuery(sql, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val ciu = cursor.getString(1) ?: ""
                    val endereco = cursor.getString(2) ?: ""
                    val setor = cursor.getString(3) ?: ""
                    val quadra = cursor.getString(4) ?: ""
                    val conjunto = cursor.getString(5) ?: ""
                    val lote = cursor.getString(6) ?: ""
                    val cep = cursor.getString(7) ?: ""
                    val ra = cursor.getString(8) ?: ""
                    val areaConstruida = cursor.getDouble(9)
                    val areaTerreno = cursor.getDouble(10)
                    val perimetro = cursor.getDouble(11)
                    val origem = cursor.getString(12) ?: ""
                    val latitude = cursor.getDouble(13)
                    val longitude = cursor.getDouble(14)

                    results.add(
                        CadastreLot(
                            objectId = id,
                            cipu = ciu,
                            ciu = ciu,
                            projeto = "Ocupações Identificadas",
                            setor = setor,
                            quadra = quadra,
                            conjunto = conjunto,
                            lote = lote,
                            endCartorial = endereco,
                            endUsual = endereco,
                            ra = ra,
                            cep = cep,
                            situacao = "Regularizada / Identificada",
                            dataRegistro = "Base Oficial DF",
                            areaProjeto = if (areaTerreno > 0.0) areaTerreno else areaConstruida,
                            normaVigente = if (areaConstruida > 0.0) "Área Construída: ${String.format(java.util.Locale.getDefault(), "%,.1f", areaConstruida)} m²" else "Origem: $origem",
                            codigoParametro = "CIU: $ciu",
                            uosParametro = if (perimetro > 0.0) "Perímetro: ${String.format(java.util.Locale.getDefault(), "%,.1f", perimetro)} m" else "Ocupação Residencial",
                            uosCores = "#0288D1",
                            faixaArea = if (areaConstruida > 0.0) "Área Constr: ${String.format(java.util.Locale.getDefault(), "%,.1f", areaConstruida)} m²" else "",
                            normaAnterior = "",
                            onalt = "",
                            onaltNorma = "",
                            onaltExcecao = "",
                            planta = "",
                            latitude = latitude,
                            longitude = longitude,
                            rings = emptyList()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying residences", e)
        }

        results
    }

    suspend fun findNearest(
        targetLat: Double,
        targetLon: Double,
        limit: Int = 5
    ): List<CadastreLot> = withContext(Dispatchers.IO) {
        ensureDatabaseCopied()
        val allLots = mutableListOf<CadastreLot>()
        val db = readableDatabase ?: return@withContext emptyList()

        // Bounding box ~5km around coordinate
        val delta = 0.05
        val minLat = targetLat - delta
        val maxLat = targetLat + delta
        val minLon = targetLon - delta
        val maxLon = targetLon + delta

        val sql = "SELECT id, ciu, endereco, setor, quadra, conjunto, lote, cep, ra, area_construida, area_terreno, perimetro, origem, latitude, longitude " +
                "FROM residences WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? LIMIT 300"

        try {
            db.rawQuery(
                sql,
                arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val ciu = cursor.getString(1) ?: ""
                    val endereco = cursor.getString(2) ?: ""
                    val setor = cursor.getString(3) ?: ""
                    val quadra = cursor.getString(4) ?: ""
                    val conjunto = cursor.getString(5) ?: ""
                    val lote = cursor.getString(6) ?: ""
                    val cep = cursor.getString(7) ?: ""
                    val ra = cursor.getString(8) ?: ""
                    val areaConstruida = cursor.getDouble(9)
                    val areaTerreno = cursor.getDouble(10)
                    val perimetro = cursor.getDouble(11)
                    val origem = cursor.getString(12) ?: ""
                    val latitude = cursor.getDouble(13)
                    val longitude = cursor.getDouble(14)

                    allLots.add(
                        CadastreLot(
                            objectId = id,
                            cipu = ciu,
                            ciu = ciu,
                            projeto = "Ocupações Identificadas",
                            setor = setor,
                            quadra = quadra,
                            conjunto = conjunto,
                            lote = lote,
                            endCartorial = endereco,
                            endUsual = endereco,
                            ra = ra,
                            cep = cep,
                            situacao = "Identificada",
                            dataRegistro = "Base Oficial DF",
                            areaProjeto = if (areaTerreno > 0.0) areaTerreno else areaConstruida,
                            normaVigente = if (areaConstruida > 0.0) "Área Construída: ${String.format(java.util.Locale.getDefault(), "%,.1f", areaConstruida)} m²" else "",
                            codigoParametro = "CIU: $ciu",
                            uosParametro = "Ocupação Residencial",
                            uosCores = "#0288D1",
                            latitude = latitude,
                            longitude = longitude
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in nearest query", e)
        }

        // Sort by Haversine distance
        allLots.sortedBy { lot ->
            haversine(targetLat, targetLon, lot.latitude, lot.longitude)
        }.take(limit)
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2) * sin(deltaLambda / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
