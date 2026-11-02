package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CadastreLot
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("geo_df_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "key_favorites_lots"
        private const val KEY_RECENT_SEARCHES = "key_recent_searches"
        private const val MAX_RECENTS = 25
    }

    fun getFavorites(): List<CadastreLot> {
        val jsonStr = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<CadastreLot>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(deserializeLot(obj))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(objectId: Long): Boolean {
        return getFavorites().any { it.objectId == objectId }
    }

    fun toggleFavorite(lot: CadastreLot): Boolean {
        val current = getFavorites().toMutableList()
        val existingIndex = current.indexOfFirst { it.objectId == lot.objectId }
        val nowFavorite = if (existingIndex >= 0) {
            current.removeAt(existingIndex)
            false
        } else {
            current.add(0, lot)
            true
        }
        saveFavorites(current)
        return nowFavorite
    }

    private fun saveFavorites(lots: List<CadastreLot>) {
        val array = JSONArray()
        for (lot in lots) {
            array.put(serializeLot(lot))
        }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    fun getRecentSearches(): List<String> {
        val jsonStr = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return defaultSuggestions()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            if (list.isEmpty()) defaultSuggestions() else list
        } catch (e: Exception) {
            defaultSuggestions()
        }
    }

    fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = getRecentSearches().filterNot { it.equals(trimmed, ignoreCase = true) }.toMutableList()
        current.add(0, trimmed)
        val limited = current.take(MAX_RECENTS)
        val array = JSONArray(limited)
        prefs.edit().putString(KEY_RECENT_SEARCHES, array.toString()).apply()
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    private fun defaultSuggestions(): List<String> {
        return listOf(
            "SQN 205",
            "SHIS QI 05",
            "QNM 18 Ceilândia",
            "Av. Castanheiras Águas Claras",
            "Esplanada dos Ministérios",
            "Setor Comercial Sul"
        )
    }

    private fun serializeLot(lot: CadastreLot): JSONObject {
        return JSONObject().apply {
            put("objectId", lot.objectId)
            put("cipu", lot.cipu)
            put("ciu", lot.ciu)
            put("projeto", lot.projeto)
            put("setor", lot.setor)
            put("quadra", lot.quadra)
            put("conjunto", lot.conjunto)
            put("lote", lot.lote)
            put("endCartorial", lot.endCartorial)
            put("endUsual", lot.endUsual)
            put("ra", lot.ra)
            put("cep", lot.cep)
            put("situacao", lot.situacao)
            put("dataRegistro", lot.dataRegistro)
            put("areaProjeto", lot.areaProjeto)
            put("normaVigente", lot.normaVigente)
            put("codigoParametro", lot.codigoParametro)
            put("uosParametro", lot.uosParametro)
            put("uosCores", lot.uosCores)
            put("faixaArea", lot.faixaArea)
            put("normaAnterior", lot.normaAnterior)
            put("onalt", lot.onalt)
            put("onaltNorma", lot.onaltNorma)
            put("onaltExcecao", lot.onaltExcecao)
            put("planta", lot.planta)
            put("latitude", lot.latitude)
            put("longitude", lot.longitude)
        }
    }

    private fun deserializeLot(obj: JSONObject): CadastreLot {
        return CadastreLot(
            objectId = obj.optLong("objectId", 0L),
            cipu = obj.optString("cipu"),
            ciu = obj.optString("ciu"),
            projeto = obj.optString("projeto"),
            setor = obj.optString("setor"),
            quadra = obj.optString("quadra"),
            conjunto = obj.optString("conjunto"),
            lote = obj.optString("lote"),
            endCartorial = obj.optString("endCartorial"),
            endUsual = obj.optString("endUsual"),
            ra = obj.optString("ra"),
            cep = obj.optString("cep"),
            situacao = obj.optString("situacao"),
            dataRegistro = obj.optString("dataRegistro"),
            areaProjeto = obj.optDouble("areaProjeto", 0.0),
            normaVigente = obj.optString("normaVigente"),
            codigoParametro = obj.optString("codigoParametro"),
            uosParametro = obj.optString("uosParametro"),
            uosCores = obj.optString("uosCores"),
            faixaArea = obj.optString("faixaArea"),
            normaAnterior = obj.optString("normaAnterior"),
            onalt = obj.optString("onalt"),
            onaltNorma = obj.optString("onaltNorma"),
            onaltExcecao = obj.optString("onaltExcecao"),
            planta = obj.optString("planta"),
            latitude = obj.optDouble("latitude", 0.0),
            longitude = obj.optDouble("longitude", 0.0),
            rings = emptyList()
        )
    }
}
