package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.data.model.CadastreLot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
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
        const val GZ_ASSET_NAME = "residences.db.gz"
        const val DATABASE_VERSION = 3
        private const val TAG = "ResidencesDbHelper"
        private const val PREFS_NAME = "residences_db_prefs"
        private const val KEY_INSTALLED_VERSION = "installed_db_version"

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
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_INSTALLED_VERSION, 0)

        if (!dbPath.exists() || installedVersion < DATABASE_VERSION) {
            dbPath.parentFile?.mkdirs()
            try {
                if (dbPath.exists()) {
                    dbPath.delete()
                }

                // Check if compressed .gz asset exists or uncompressed .db asset
                val hasGzAsset = try {
                    context.assets.open(GZ_ASSET_NAME).close()
                    true
                } catch (_: Exception) {
                    false
                }

                if (hasGzAsset) {
                    context.assets.open(GZ_ASSET_NAME).use { rawIn ->
                        GZIPInputStream(rawIn).use { gzIn ->
                            FileOutputStream(dbPath).use { out ->
                                val buffer = ByteArray(128 * 1024)
                                var read: Int
                                while (gzIn.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                                out.flush()
                            }
                        }
                    }
                    Log.i(TAG, "Full 660,065 residences database successfully decompressed and deployed to ${dbPath.absolutePath}")
                } else {
                    context.assets.open(DATABASE_NAME).use { input ->
                        FileOutputStream(dbPath).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }
                prefs.edit().putInt(KEY_INSTALLED_VERSION, DATABASE_VERSION).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error deploying database from assets", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Pre-populated database
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureDatabaseCopied()
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
        if (trimmed.isEmpty() && raFilter.isNullOrBlank()) {
            return@withContext emptyList()
        }

        val cleanDigits = trimmed.filter { it.isDigit() }
        val isCepSearch = cleanDigits.length in 5..8

        // Strategy 1: Search by CIU
        val ciuSql = buildString {
            append("SELECT ciu, endereco, setor, quadra, conjunto, lote, cep, ra, latitude, longitude ")
            append("FROM residences WHERE ciu LIKE ? ")
            if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                append("AND UPPER(ra) = '${raFilter.uppercase().replace("'", "''")}' ")
            }
            append("ORDER BY quadra ASC, conjunto ASC, LENGTH(lote) ASC, lote ASC LIMIT $limit")
        }

        try {
            db.rawQuery(ciuSql, arrayOf("%$trimmed%")).use { cursor ->
                var vId = 1L
                while (cursor.moveToNext()) {
                    results.add(mapCursorToLot(cursor, vId++))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in CIU search", e)
        }

        if (results.size >= limit) return@withContext results

        // Strategy 2: Search by CEP
        if (isCepSearch) {
            val cepSql = buildString {
                append("SELECT ciu, endereco, setor, quadra, conjunto, lote, cep, ra, latitude, longitude ")
                append("FROM residences WHERE cep LIKE ? ")
                if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                    append("AND UPPER(ra) = '${raFilter.uppercase().replace("'", "''")}' ")
                }
                append("ORDER BY quadra ASC, conjunto ASC, LENGTH(lote) ASC, lote ASC LIMIT ${limit - results.size}")
            }

            try {
                db.rawQuery(cepSql, arrayOf("%$cleanDigits%")).use { cursor ->
                    var vId = 1000L
                    while (cursor.moveToNext()) {
                        val lot = mapCursorToLot(cursor, vId++)
                        if (results.none { it.ciu == lot.ciu }) {
                            results.add(lot)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in CEP search", e)
            }
        }

        if (results.size >= limit) return@withContext results

        // Strategy 3: Full phrase address match (e.g. "SETOR M SUL QNM 3 CJ N LT 21")
        val addrSql = buildString {
            append("SELECT ciu, endereco, setor, quadra, conjunto, lote, cep, ra, latitude, longitude ")
            append("FROM residences WHERE (endereco LIKE ? OR quadra LIKE ? OR setor LIKE ?) ")
            if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                append("AND UPPER(ra) = '${raFilter.uppercase().replace("'", "''")}' ")
            }
            append("ORDER BY quadra ASC, conjunto ASC, LENGTH(lote) ASC, lote ASC LIMIT ${limit - results.size}")
        }

        try {
            val pat = "%$trimmed%"
            db.rawQuery(addrSql, arrayOf(pat, pat, pat)).use { cursor ->
                var vId = 10000L
                while (cursor.moveToNext()) {
                    val lot = mapCursorToLot(cursor, vId++)
                    if (results.none { it.ciu == lot.ciu }) {
                        results.add(lot)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in address phrase search", e)
        }

        if (results.size >= limit) return@withContext results

        // Strategy 4: Multi-token match across all address components
        val tokens = trimmed.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (tokens.size >= 2) {
            val whereClauses = mutableListOf<String>()
            val args = mutableListOf<String>()

            for (tok in tokens) {
                whereClauses.add("(endereco LIKE ? OR setor LIKE ? OR quadra LIKE ? OR conjunto LIKE ? OR lote LIKE ? OR ciu LIKE ? OR cep LIKE ?)")
                val tPat = "%$tok%"
                repeat(7) { args.add(tPat) }
            }

            if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                whereClauses.add("UPPER(ra) = ?")
                args.add(raFilter.uppercase())
            }

            val tokenSql = "SELECT ciu, endereco, setor, quadra, conjunto, lote, cep, ra, latitude, longitude " +
                    "FROM residences WHERE ${whereClauses.joinToString(" AND ")} " +
                    "ORDER BY quadra ASC, conjunto ASC, LENGTH(lote) ASC, lote ASC LIMIT ${limit - results.size}"

            try {
                db.rawQuery(tokenSql, args.toTypedArray()).use { cursor ->
                    var vId = 20000L
                    while (cursor.moveToNext()) {
                        val lot = mapCursorToLot(cursor, vId++)
                        if (results.none { it.ciu == lot.ciu }) {
                            results.add(lot)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in token search", e)
            }
        }

        // Sort all residences by quadra, conjunto, and house/lot number in ascending order
        results.sortWith(
            compareBy<CadastreLot> { it.quadra }
                .thenBy { it.conjunto }
                .thenBy { it.loteNumberForSort }
                .thenBy { it.lote }
                .thenBy { it.ciu }
        )

        results
    }

    suspend fun findNearest(
        targetLat: Double,
        targetLon: Double,
        limit: Int = 10
    ): List<CadastreLot> = withContext(Dispatchers.IO) {
        ensureDatabaseCopied()
        val allLots = mutableListOf<CadastreLot>()
        val db = readableDatabase ?: return@withContext emptyList()

        val delta = 0.05
        val minLat = targetLat - delta
        val maxLat = targetLat + delta
        val minLon = targetLon - delta
        val maxLon = targetLon + delta

        val sql = "SELECT ciu, endereco, setor, quadra, conjunto, lote, cep, ra, latitude, longitude " +
                "FROM residences WHERE latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? LIMIT 300"

        try {
            db.rawQuery(
                sql,
                arrayOf(minLat.toString(), maxLat.toString(), minLon.toString(), maxLon.toString())
            ).use { cursor ->
                var vId = 30000L
                while (cursor.moveToNext()) {
                    allLots.add(mapCursorToLot(cursor, vId++))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in nearest query", e)
        }

        allLots.sortedBy { lot ->
            haversine(targetLat, targetLon, lot.latitude, lot.longitude)
        }.take(limit)
    }

    private fun mapCursorToLot(cursor: android.database.Cursor, id: Long): CadastreLot {
        val ciu = cursor.getString(0) ?: ""
        val endereco = cursor.getString(1) ?: ""
        val setor = cursor.getString(2) ?: ""
        val quadra = cursor.getString(3) ?: ""
        val conjunto = cursor.getString(4) ?: ""
        val lote = cursor.getString(5) ?: ""
        val cep = cursor.getString(6) ?: ""
        val ra = cursor.getString(7) ?: ""
        val latitude = cursor.getDouble(8)
        val longitude = cursor.getDouble(9)

        return CadastreLot(
            objectId = id,
            cipu = ciu,
            ciu = ciu,
            projeto = "Ocupações Identificadas (Base Oficial 660k)",
            setor = setor,
            quadra = quadra,
            conjunto = conjunto,
            lote = lote,
            endCartorial = endereco,
            endUsual = endereco,
            ra = ra,
            cep = cep,
            situacao = "Identificada / Cadastrada",
            dataRegistro = "Base Oficial DF",
            areaProjeto = 0.0,
            normaVigente = "ct_CIU: $ciu",
            codigoParametro = "CIU: $ciu",
            uosParametro = "Ocupação Residencial",
            uosCores = "#0288D1",
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
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
