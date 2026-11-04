package com.example.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.data.model.CadastreLot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        const val DATABASE_VERSION = 2
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
                prefs.edit().putInt(KEY_INSTALLED_VERSION, DATABASE_VERSION).apply()
                Log.i(TAG, "Full 28,923 CEP and residence database copied successfully to ${dbPath.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error copying database from assets", e)
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Pre-populated from assets
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureDatabaseCopied()
    }

    suspend fun search(
        query: String,
        raFilter: String? = null,
        limit: Int = 50
    ): List<CadastreLot> = withContext(Dispatchers.IO) {
        ensureDatabaseCopied()
        val results = mutableListOf<CadastreLot>()
        val db = readableDatabase ?: return@withContext emptyList()

        val trimmed = query.trim()
        val cleanDigits = trimmed.filter { it.isDigit() }
        val isCepSearch = cleanDigits.length in 5..8

        // 1. If searching by CEP, query 100% of CEP Directory first
        if (isCepSearch) {
            val cepPattern = "%$cleanDigits%"
            val cepSql = buildString {
                append("SELECT cep, endereco, setor, quadra, ra, ciu, latitude, longitude, total_unidades ")
                append("FROM cep_directory WHERE cep LIKE ? ")
                if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                    append("AND UPPER(ra) = '${raFilter.uppercase().replace("'", "''")}' ")
                }
                append("LIMIT $limit")
            }

            try {
                db.rawQuery(cepSql, arrayOf(cepPattern)).use { cursor ->
                    var virtualId = 1000000L
                    while (cursor.moveToNext()) {
                        val cepVal = cursor.getString(0) ?: ""
                        val enderecoVal = cursor.getString(1) ?: ""
                        val setorVal = cursor.getString(2) ?: ""
                        val quadraVal = cursor.getString(3) ?: ""
                        val raVal = cursor.getString(4) ?: ""
                        val ciuVal = cursor.getString(5) ?: ""
                        val latVal = cursor.getDouble(6)
                        val lonVal = cursor.getDouble(7)
                        val totalUnidades = cursor.getInt(8)

                        results.add(
                            CadastreLot(
                                objectId = virtualId++,
                                cipu = ciuVal,
                                ciu = ciuVal,
                                projeto = "Ocupações Identificadas (Base Completa)",
                                setor = setorVal,
                                quadra = quadraVal,
                                conjunto = "",
                                lote = "",
                                endCartorial = enderecoVal,
                                endUsual = enderecoVal,
                                ra = raVal,
                                cep = cepVal,
                                situacao = "CEP Oficial DF ($totalUnidades residências vinculadas)",
                                dataRegistro = "Base Oficial 28.923 CEPs",
                                areaProjeto = 0.0,
                                normaVigente = "CEP: $cepVal • $totalUnidades unidades identificadas",
                                codigoParametro = "CIU: $ciuVal",
                                uosParametro = "Residências Identificadas",
                                uosCores = "#0288D1",
                                latitude = latVal,
                                longitude = lonVal
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying cep_directory", e)
            }
        }

        // 2. Query individual residences table
        val remainingLimit = limit - results.size
        if (remainingLimit > 0) {
            val sanitized = trimmed.replace("'", "''")
            val whereClauses = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (sanitized.isNotBlank()) {
                if (isCepSearch) {
                    whereClauses.add("(cep LIKE ? OR ciu LIKE ?)")
                    args.add("%$cleanDigits%")
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

            val sql = "SELECT id, ciu, endereco, setor, quadra, conjunto, lote, cep, ra, area_construida, area_terreno, latitude, longitude " +
                    "FROM residences $whereSql ORDER BY id ASC LIMIT $remainingLimit"

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
                        val latitude = cursor.getDouble(11)
                        val longitude = cursor.getDouble(12)

                        // Avoid duplicate if already added by exact CEP
                        if (results.none { it.ciu == ciu && it.ciu.isNotBlank() }) {
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
                                    situacao = "Identificada / Cadastrada",
                                    dataRegistro = "Base Oficial DF",
                                    areaProjeto = if (areaTerreno > 0.0) areaTerreno else areaConstruida,
                                    normaVigente = if (areaConstruida > 0.0) "Área Construída: ${String.format(java.util.Locale.getDefault(), "%,.1f", areaConstruida)} m²" else "Lote Residencial",
                                    codigoParametro = "CIU: $ciu",
                                    uosParametro = "Ocupação Residencial",
                                    uosCores = "#0288D1",
                                    latitude = latitude,
                                    longitude = longitude
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying residences", e)
            }
        }

        // 3. If text query and still have space, check if query matches address in cep_directory
        if (results.size < 5 && !isCepSearch && trimmed.length >= 3) {
            val endPattern = "%${trimmed.replace("'", "''")}%"
            val cepSql = buildString {
                append("SELECT cep, endereco, setor, quadra, ra, ciu, latitude, longitude, total_unidades ")
                append("FROM cep_directory WHERE (endereco LIKE ? OR setor LIKE ? OR quadra LIKE ?) ")
                if (!raFilter.isNullOrBlank() && raFilter != "TODAS") {
                    append("AND UPPER(ra) = '${raFilter.uppercase().replace("'", "''")}' ")
                }
                append("LIMIT 20")
            }

            try {
                db.rawQuery(cepSql, arrayOf(endPattern, endPattern, endPattern)).use { cursor ->
                    var vId = 2000000L
                    while (cursor.moveToNext()) {
                        val cepVal = cursor.getString(0) ?: ""
                        val enderecoVal = cursor.getString(1) ?: ""
                        val setorVal = cursor.getString(2) ?: ""
                        val quadraVal = cursor.getString(3) ?: ""
                        val raVal = cursor.getString(4) ?: ""
                        val ciuVal = cursor.getString(5) ?: ""
                        val latVal = cursor.getDouble(6)
                        val lonVal = cursor.getDouble(7)
                        val totalUnidades = cursor.getInt(8)

                        if (results.none { it.cep == cepVal && it.endUsual == enderecoVal }) {
                            results.add(
                                CadastreLot(
                                    objectId = vId++,
                                    cipu = ciuVal,
                                    ciu = ciuVal,
                                    projeto = "Ocupações Identificadas (Base Completa)",
                                    setor = setorVal,
                                    quadra = quadraVal,
                                    conjunto = "",
                                    lote = "",
                                    endCartorial = enderecoVal,
                                    endUsual = enderecoVal,
                                    ra = raVal,
                                    cep = cepVal,
                                    situacao = "CEP Oficial DF ($totalUnidades residências)",
                                    dataRegistro = "Base Oficial 28.923 CEPs",
                                    areaProjeto = 0.0,
                                    normaVigente = "CEP: $cepVal • $totalUnidades unidades identificadas",
                                    codigoParametro = "CIU: $ciuVal",
                                    uosParametro = "Residências Identificadas",
                                    uosCores = "#0288D1",
                                    latitude = latVal,
                                    longitude = lonVal
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error querying cep_directory for address", e)
            }
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

        val delta = 0.05
        val minLat = targetLat - delta
        val maxLat = targetLat + delta
        val minLon = targetLon - delta
        val maxLon = targetLon + delta

        val sql = "SELECT id, ciu, endereco, setor, quadra, conjunto, lote, cep, ra, area_construida, area_terreno, latitude, longitude " +
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
                    val latitude = cursor.getDouble(11)
                    val longitude = cursor.getDouble(12)

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
