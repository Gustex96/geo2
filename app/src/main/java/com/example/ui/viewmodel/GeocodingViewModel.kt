package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.LocalStore
import com.example.data.model.CadastreLot
import com.example.data.model.WmsServiceInfo
import com.example.data.repository.GeocodingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MapEngine {
    MAP_SERVER,
    WMS_DIRECT
}

data class GeocodingUiState(
    val searchQuery: String = "SQN 205",
    val selectedRaFilter: String = "TODAS",
    val searchResults: List<CadastreLot> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val selectedLot: CadastreLot? = null,
    val favorites: List<CadastreLot> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    // Reverse Geocode
    val reverseLatInput: String = "-15.77165",
    val reverseLonInput: String = "-47.87985",
    val isReverseGeocoding: Boolean = false,
    val reverseGeocodeResults: List<CadastreLot> = emptyList(),
    val reverseGeocodeError: String? = null,
    // Map State
    val mapCenterLat: Double = -15.77165,
    val mapCenterLon: Double = -47.87985,
    val mapZoomDelta: Double = 0.006, // approx 600m
    val mapEngine: MapEngine = MapEngine.MAP_SERVER,
    val visibleLayers: Set<String> = setOf("9", "12", "13", "5"), // Lotes, Quadras, Setores, Edificações
    val mapTileUrl: String = "",
    val mapTileLoading: Boolean = false,
    // WMS Info
    val wmsInfo: WmsServiceInfo = WmsServiceInfo(),
    val isLoadingWmsInfo: Boolean = false,
    val wmsInfoError: String? = null,
    val currentTab: Int = 0 // 0: Busca, 1: Mapa WMS, 2: Reversa, 3: WMS Info, 4: Favoritos
)

class GeocodingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeocodingRepository()
    private val localStore = LocalStore(application)

    private val _uiState = MutableStateFlow(GeocodingUiState())
    val uiState: StateFlow<GeocodingUiState> = _uiState.asStateFlow()

    val administrativeRegions = listOf(
        "TODAS",
        "PLANO PILOTO",
        "TAGUATINGA",
        "ÁGUAS CLARAS",
        "CEILÂNDIA",
        "SAMAMBAIA",
        "GUARA",
        "SOBRADINHO",
        "LAGO SUL",
        "LAGO NORTE",
        "CRUZEIRO",
        "SUDOESTE/OCTOGONAL",
        "VICENTE PIRES",
        "GAMA",
        "SANTA MARIA"
    )

    init {
        loadLocalData()
        updateMapTileUrl()
        // Run initial sample search so user sees live data immediately
        performSearch("SQN 205")
    }

    private fun loadLocalData() {
        val favs = localStore.getFavorites()
        val recents = localStore.getRecentSearches()
        _uiState.update { it.copy(favorites = favs, recentSearches = recents) }
    }

    fun setTab(index: Int) {
        _uiState.update { it.copy(currentTab = index) }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery, searchError = null) }
    }

    fun onRaFilterSelected(ra: String) {
        _uiState.update { it.copy(selectedRaFilter = ra) }
        performSearch(_uiState.value.searchQuery)
    }

    fun performSearch(queryToSearch: String = _uiState.value.searchQuery) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isEmpty()) return

        _uiState.update {
            it.copy(
                isSearching = true,
                searchError = null,
                searchQuery = trimmed
            )
        }

        localStore.addRecentSearch(trimmed)
        _uiState.update { it.copy(recentSearches = localStore.getRecentSearches()) }

        viewModelScope.launch {
            val result = repository.searchLots(
                query = trimmed,
                raFilter = _uiState.value.selectedRaFilter.takeIf { it != "TODAS" }
            )
            result.onSuccess { lots ->
                _uiState.update {
                    it.copy(
                        searchResults = lots,
                        isSearching = false,
                        searchError = if (lots.isEmpty()) "Nenhum lote ou endereço cadastral encontrado para '$trimmed'." else null
                    )
                }
                if (lots.isNotEmpty()) {
                    val first = lots.first()
                    if (first.latitude != 0.0 && first.longitude != 0.0) {
                        centerMapOn(first.latitude, first.longitude)
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = err.localizedMessage ?: "Erro na comunicação com o Geoportal IDE DF"
                    )
                }
            }
        }
    }

    fun selectLot(lot: CadastreLot) {
        _uiState.update { it.copy(selectedLot = lot) }
        if (lot.latitude != 0.0 && lot.longitude != 0.0) {
            centerMapOn(lot.latitude, lot.longitude)
        }
    }

    fun clearSelectedLot() {
        _uiState.update { it.copy(selectedLot = null) }
    }

    fun toggleFavorite(lot: CadastreLot) {
        localStore.toggleFavorite(lot)
        _uiState.update { it.copy(favorites = localStore.getFavorites()) }
    }

    fun isFavorite(lot: CadastreLot): Boolean {
        return _uiState.value.favorites.any { it.objectId == lot.objectId }
    }

    // Reverse Geocode
    fun onReverseLatChanged(newLat: String) {
        _uiState.update { it.copy(reverseLatInput = newLat, reverseGeocodeError = null) }
    }

    fun onReverseLonChanged(newLon: String) {
        _uiState.update { it.copy(reverseLonInput = newLon, reverseGeocodeError = null) }
    }

    fun performReverseGeocode(latStr: String = _uiState.value.reverseLatInput, lonStr: String = _uiState.value.reverseLonInput) {
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()

        if (lat == null || lon == null) {
            _uiState.update { it.copy(reverseGeocodeError = "Insira coordenadas válidas em graus decimais (ex: -15.7716, -47.8798).") }
            return
        }

        _uiState.update {
            it.copy(
                isReverseGeocoding = true,
                reverseGeocodeError = null,
                reverseLatInput = latStr,
                reverseLonInput = lonStr
            )
        }

        viewModelScope.launch {
            val result = repository.reverseGeocode(lat, lon)
            result.onSuccess { lots ->
                _uiState.update {
                    it.copy(
                        reverseGeocodeResults = lots,
                        isReverseGeocoding = false,
                        reverseGeocodeError = if (lots.isEmpty()) "Nenhum lote cadastrado encontrado nas proximidades destas coordenadas." else null
                    )
                }
                centerMapOn(lat, lon)
                if (lots.isNotEmpty()) {
                    selectLot(lots.first())
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isReverseGeocoding = false,
                        reverseGeocodeError = err.localizedMessage ?: "Erro ao consultar coordenadas no Geoportal IDE DF"
                    )
                }
            }
        }
    }

    fun onMapTappedCoordinate(lat: Double, lon: Double) {
        _uiState.update {
            it.copy(
                reverseLatInput = String.format(java.util.Locale.US, "%.6f", lat),
                reverseLonInput = String.format(java.util.Locale.US, "%.6f", lon)
            )
        }
        performReverseGeocode(lat.toString(), lon.toString())
    }

    // Map Navigation
    fun zoomIn() {
        val newZoom = (_uiState.value.mapZoomDelta * 0.65).coerceAtLeast(0.0008)
        _uiState.update { it.copy(mapZoomDelta = newZoom) }
        updateMapTileUrl()
    }

    fun zoomOut() {
        val newZoom = (_uiState.value.mapZoomDelta * 1.5).coerceAtMost(0.3)
        _uiState.update { it.copy(mapZoomDelta = newZoom) }
        updateMapTileUrl()
    }

    fun pan(dLatRatio: Double, dLonRatio: Double) {
        val current = _uiState.value
        val dLat = current.mapZoomDelta * dLatRatio
        val dLon = current.mapZoomDelta * dLonRatio
        centerMapOn(current.mapCenterLat + dLat, current.mapCenterLon + dLon)
    }

    fun centerMapOn(lat: Double, lon: Double) {
        _uiState.update {
            it.copy(
                mapCenterLat = lat,
                mapCenterLon = lon
            )
        }
        updateMapTileUrl()
    }

    fun recenterOnBrasilia() {
        centerMapOn(-15.7942, -47.8822) // Plano Piloto
    }

    fun toggleMapEngine() {
        val next = if (_uiState.value.mapEngine == MapEngine.MAP_SERVER) MapEngine.WMS_DIRECT else MapEngine.MAP_SERVER
        _uiState.update { it.copy(mapEngine = next) }
        updateMapTileUrl()
    }

    fun toggleLayer(layerId: String) {
        val current = _uiState.value.visibleLayers.toMutableSet()
        if (current.contains(layerId)) {
            if (current.size > 1) current.remove(layerId)
        } else {
            current.add(layerId)
        }
        _uiState.update { it.copy(visibleLayers = current) }
        updateMapTileUrl()
    }

    private fun updateMapTileUrl() {
        val s = _uiState.value
        val halfDelta = s.mapZoomDelta / 2.0
        val minLon = s.mapCenterLon - halfDelta
        val maxLon = s.mapCenterLon + halfDelta
        val minLat = s.mapCenterLat - halfDelta
        val maxLat = s.mapCenterLat + halfDelta

        val url = if (s.mapEngine == MapEngine.MAP_SERVER) {
            repository.buildMapServerExportUrl(
                minLon = minLon,
                minLat = minLat,
                maxLon = maxLon,
                maxLat = maxLat,
                width = 900,
                height = 900,
                layers = s.visibleLayers.toList()
            )
        } else {
            // WMS layer mapping
            val wmsLayers = s.visibleLayers.map { lId ->
                when (lId) {
                    "9" -> "5" // Lotes
                    "12" -> "2" // Quadras
                    "13" -> "1" // Setor
                    "5" -> "9" // Edificação
                    else -> lId
                }
            }
            repository.buildWmsGetMapUrl(
                minLon = minLon,
                minLat = minLat,
                maxLon = maxLon,
                maxLat = maxLat,
                width = 900,
                height = 900,
                layers = wmsLayers
            )
        }
        _uiState.update { it.copy(mapTileUrl = url) }
    }

    fun loadWmsCapabilities() {
        _uiState.update { it.copy(isLoadingWmsInfo = true, wmsInfoError = null) }
        viewModelScope.launch {
            val result = repository.getWmsCapabilities()
            result.onSuccess { info ->
                _uiState.update { it.copy(wmsInfo = info, isLoadingWmsInfo = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingWmsInfo = false,
                        wmsInfoError = err.localizedMessage ?: "Erro ao carregar GetCapabilities"
                    )
                }
            }
        }
    }
}
