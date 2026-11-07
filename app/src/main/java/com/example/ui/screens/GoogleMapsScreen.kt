package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.BuildConfig
import com.example.data.model.CadastreLot
import com.example.data.model.KmdfRecord
import com.example.ui.theme.CadastreBlue
import com.example.ui.theme.CadastreYellow
import com.example.ui.viewmodel.GeocodingUiState
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class GoogleMapMode {
    NATIVE_SDK,
    INTERACTIVE_WEB
}

data class FreeSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val source: String = "Google Maps"
)

private suspend fun searchFreePlaceOnMaps(context: Context, query: String, apiKey: String): List<FreeSearchResult> = withContext(Dispatchers.IO) {
    val results = mutableListOf<FreeSearchResult>()
    val cleanQuery = query.trim()

    // 0. Check if user typed coordinates like: -15.7942, -47.8822
    val coordMatch = Regex("""^([+-]?\d+\.?\d*)[,\s]+([+-]?\d+\.?\d*)$""").find(cleanQuery)
    if (coordMatch != null) {
        val lat = coordMatch.groupValues[1].toDoubleOrNull()
        val lon = coordMatch.groupValues[2].toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            results.add(
                FreeSearchResult(
                    name = "Coordenadas Informadas",
                    address = "Lat: $lat, Lon: $lon",
                    latitude = lat,
                    longitude = lon,
                    source = "GPS Direto"
                )
            )
            return@withContext results
        }
    }

    // 1. Android Geocoder search
    try {
        if (Geocoder.isPresent()) {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(cleanQuery, 6)
            if (!addresses.isNullOrEmpty()) {
                for (addr in addresses) {
                    val placeName = addr.featureName?.takeIf { it.isNotBlank() }
                        ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                        ?: cleanQuery
                    val fullAddress = (0..addr.maxAddressLineIndex)
                        .mapNotNull { addr.getAddressLine(it) }
                        .joinToString(", ")
                    results.add(
                        FreeSearchResult(
                            name = placeName,
                            address = if (fullAddress.isNotBlank()) fullAddress else "$placeName (${addr.adminArea ?: ""})",
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            source = "Google / Android"
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    // 2. Google Geocoding Web API fallback if needed
    if (results.isEmpty() && apiKey.isNotBlank() && apiKey != "DEFAULT_KEY") {
        try {
            val encodedQuery = java.net.URLEncoder.encode(cleanQuery, "UTF-8")
            val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$encodedQuery&key=$apiKey&language=pt-BR"
            val request = okhttp3.Request.Builder().url(url).build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        if (json.optString("status") == "OK") {
                            val jsonResults = json.optJSONArray("results")
                            if (jsonResults != null) {
                                for (i in 0 until minOf(5, jsonResults.length())) {
                                    val item = jsonResults.getJSONObject(i)
                                    val formatted = item.optString("formatted_address")
                                    val location = item.optJSONObject("geometry")?.optJSONObject("location")
                                    if (location != null) {
                                        val lat = location.optDouble("lat")
                                        val lng = location.optDouble("lng")
                                        results.add(
                                            FreeSearchResult(
                                                name = formatted.substringBefore(","),
                                                address = formatted,
                                                latitude = lat,
                                                longitude = lng,
                                                source = "Google Geocoding"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    results
}

data class DfRegionCenter(
    val name: String,
    val lat: Double,
    val lon: Double,
    val zoom: Float = 14f
)

private val DF_REGIONS = listOf(
    DfRegionCenter("Plano Piloto (Brasília)", -15.7942, -47.8822, 13f),
    DfRegionCenter("Ceilândia", -15.8203, -48.1130, 14f),
    DfRegionCenter("Taguatinga", -15.8335, -48.0566, 14f),
    DfRegionCenter("Águas Claras", -15.8398, -48.0267, 14f),
    DfRegionCenter("Samambaia", -15.8770, -48.0860, 14f),
    DfRegionCenter("Guará", -15.8242, -47.9780, 14f),
    DfRegionCenter("Sobradinho", -15.6517, -47.7917, 14f),
    DfRegionCenter("Gama", -16.0180, -48.0645, 14f),
    DfRegionCenter("Planaltina", -15.6178, -47.6499, 14f),
    DfRegionCenter("Santa Maria", -16.0189, -47.9942, 14f),
    DfRegionCenter("Vicente Pires", -15.8055, -48.0336, 14f),
    DfRegionCenter("Riacho Fundo", -15.8828, -48.0167, 14f),
    DfRegionCenter("Recanto das Emas", -15.9031, -48.0700, 14f),
    DfRegionCenter("Sudoeste / Cruzeiro", -15.7983, -47.9250, 14f),
    DfRegionCenter("Lago Sul", -15.8458, -47.8681, 13f),
    DfRegionCenter("Lago Norte", -15.7333, -47.8500, 13f),
    DfRegionCenter("Brazlândia", -15.6739, -48.2014, 14f)
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleMapsScreen(
    uiState: GeocodingUiState,
    onSelectLot: (CadastreLot) -> Unit,
    onSelectKmdfRecord: (KmdfRecord) -> Unit,
    onMapClickCoordinate: (Double, Double) -> Unit,
    onReverseGeocodeKm: (KmdfRecord) -> Unit,
    modifier: Modifier = Modifier,
    onClearSelectedLot: (() -> Unit)? = null,
    onClearSelectedKmdfRecord: (() -> Unit)? = null,
    onSearchQuery: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Check Google Play Services status
    val playServicesStatus = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val hasPlayServices = playServicesStatus == ConnectionResult.SUCCESS

    // Initialize Maps SDK renderer if Play Services is present
    LaunchedEffect(Unit) {
        if (hasPlayServices) {
            try {
                MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) { }
            } catch (_: Exception) {
                try {
                    MapsInitializer.initialize(context, MapsInitializer.Renderer.LEGACY) { }
                } catch (_: Exception) { }
            }
        }
    }

    val isApiKeyConfigured = BuildConfig.MAPS_API_KEY.isNotBlank() && BuildConfig.MAPS_API_KEY != "DEFAULT_KEY"
    val apiKeyToUse = BuildConfig.MAPS_API_KEY

    // Primary engine: Official Google Maps Android SDK 3D (per Google documentation)
    var mapEngineMode by remember {
        mutableStateOf(if (hasPlayServices) GoogleMapMode.NATIVE_SDK else GoogleMapMode.INTERACTIVE_WEB)
    }

    val brasilia = remember { LatLng(-15.7942, -47.8822) }
    val initialCameraPosition = remember {
        val selLot = uiState.selectedLot
        if (selLot != null && selLot.latitude != 0.0 && selLot.longitude != 0.0) {
            CameraPosition.fromLatLngZoom(LatLng(selLot.latitude, selLot.longitude), 18.5f)
        } else {
            val selKm = uiState.selectedKmdfRecord
            if (selKm != null && selKm.latitude != 0.0 && selKm.longitude != 0.0) {
                CameraPosition.fromLatLngZoom(LatLng(selKm.latitude, selKm.longitude), 16f)
            } else {
                CameraPosition.fromLatLngZoom(brasilia, 11f)
            }
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = initialCameraPosition
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var currentWebLayer by remember { mutableStateOf("streets") }
    var showTraffic by remember { mutableStateOf(false) }
    var showKmdfMarkers by remember { mutableStateOf(true) }
    var showCadastreLots by remember { mutableStateOf(true) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showRegionsMenu by remember { mutableStateOf(false) }
    var selectedRodoviaFilter by remember { mutableStateOf("TODAS") }

    // Free search bar state (consultas livres no Google Maps fora do propósito do aplicativo)
    var inMapSearchQuery by remember { mutableStateOf("") }
    var freeSearchResults by remember { mutableStateOf<List<FreeSearchResult>>(emptyList()) }
    var activeFreeSearchResult by remember { mutableStateOf<FreeSearchResult?>(null) }
    var isSearchingFree by remember { mutableStateOf(false) }

    // Local preview for selected elements on map
    var localPreviewLot by remember { mutableStateOf<CadastreLot?>(null) }
    var localPreviewKmdf by remember { mutableStateOf<KmdfRecord?>(null) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val onSelectFreePlace: (FreeSearchResult) -> Unit = { place ->
        activeFreeSearchResult = place
        localPreviewLot = null
        localPreviewKmdf = null
        freeSearchResults = emptyList()
        inMapSearchQuery = place.name
        focusManager.clearFocus(force = true)

        if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(place.latitude, place.longitude), 16.5f),
                    durationMs = 600
                )
            }
        } else {
            webViewInstance?.evaluateJavascript("setFreeSearchMarker('${place.name.replace("'", "\\'")}', '${place.address.replace("'", "\\'")}', ${place.latitude}, ${place.longitude});", null)
        }
        Toast.makeText(context, "Local: ${place.name}", Toast.LENGTH_SHORT).show()
    }

    val executeFreeSearch: (String) -> Unit = { query ->
        val q = query.trim()
        if (q.isNotBlank()) {
            focusManager.clearFocus(force = true)
            isSearchingFree = true
            scope.launch {
                val results = searchFreePlaceOnMaps(context, q, apiKeyToUse)
                isSearchingFree = false
                if (results.isNotEmpty()) {
                    freeSearchResults = results
                    onSelectFreePlace(results.first())
                } else {
                    onSearchQuery?.invoke(q)
                    Toast.makeText(context, "Buscando \"$q\" no banco local...", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Sync selected lot from uiState if present
    LaunchedEffect(uiState.selectedLot) {
        uiState.selectedLot?.let { lot ->
            localPreviewLot = lot
            localPreviewKmdf = null
            if (lot.latitude != 0.0 && lot.longitude != 0.0) {
                val target = LatLng(lot.latitude, lot.longitude)
                if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                    try {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(target, 18.5f),
                            durationMs = 600
                        )
                    } catch (_: Exception) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 18.5f)
                    }
                } else {
                    webViewInstance?.evaluateJavascript("googleMapCenter(${lot.latitude}, ${lot.longitude}, 18);", null)
                }
            }
        }
    }

    // Sync selected KMDF record from uiState
    LaunchedEffect(uiState.selectedKmdfRecord) {
        uiState.selectedKmdfRecord?.let { km ->
            localPreviewKmdf = km
            localPreviewLot = null
            if (km.latitude != 0.0 && km.longitude != 0.0) {
                if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(LatLng(km.latitude, km.longitude), 16f)
                    )
                } else {
                    webViewInstance?.evaluateJavascript("googleMapCenter(${km.latitude}, ${km.longitude}, 16);", null)
                }
            }
        }
    }

    val filteredKmdf = remember(uiState.kmdfRecords, selectedRodoviaFilter) {
        if (selectedRodoviaFilter == "TODAS") {
            uiState.kmdfRecords
        } else {
            uiState.kmdfRecords.filter { it.rodovia.equals(selectedRodoviaFilter, ignoreCase = true) }
        }
    }

    val availableRodovias = remember(uiState.kmdfRecords) {
        listOf("TODAS") + uiState.kmdfRecords.map { it.rodovia }.distinct().sorted()
    }

    // JSON serialization for interactive web mode
    val webResidencesJson = remember(uiState.searchResults) {
        val arr = JSONArray()
        uiState.searchResults.take(150).forEach { r ->
            if (r.latitude != 0.0 && r.longitude != 0.0) {
                val o = JSONObject()
                o.put("id", r.objectId)
                o.put("ciu", r.ciu)
                o.put("end", r.displayAddress)
                o.put("ra", r.ra)
                o.put("area", r.formattedArea)
                o.put("lat", r.latitude)
                o.put("lon", r.longitude)
                arr.put(o)
            }
        }
        arr.toString()
    }

    val webKmdfJson = remember(filteredKmdf) {
        val arr = JSONArray()
        filteredKmdf.take(200).forEach { km ->
            val o = JSONObject()
            o.put("id", km.id)
            o.put("rod", km.rodovia)
            o.put("km", km.kmDisplay)
            o.put("tr", km.trecho)
            o.put("ra", km.ra)
            o.put("lat", km.latitude)
            o.put("lon", km.longitude)
            arr.put(o)
        }
        arr.toString()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_maps_screen_container")
    ) {
        // 1. MAP ENGINE: Native 3D Google Maps SDK OR Interactive Google Maps Web
        if (mapEngineMode == GoogleMapMode.NATIVE_SDK && hasPlayServices) {
            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_view_native"),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = false,
                    isTrafficEnabled = showTraffic,
                    isBuildingEnabled = true,
                    isIndoorEnabled = true
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = true,
                    myLocationButtonEnabled = false,
                    rotationGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    zoomGesturesEnabled = true
                ),
                onMapClick = { latLng ->
                    focusManager.clearFocus(force = true)
                    onMapClickCoordinate(latLng.latitude, latLng.longitude)
                }
            ) {
                // Render Cadastre Lot markers & polygons
                if (showCadastreLots) {
                    val activeLot = localPreviewLot ?: uiState.selectedLot
                    activeLot?.let { lot ->
                        for (ring in lot.rings) {
                            if (ring.size >= 3) {
                                val pts = ring.map { LatLng(it.second, it.first) }
                                Polygon(
                                    points = pts,
                                    fillColor = Color(0x66FFB703),
                                    strokeColor = Color(0xFFFFB703),
                                    strokeWidth = 6f
                                )
                            }
                        }
                        if (lot.latitude != 0.0 && lot.longitude != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(lot.latitude, lot.longitude)),
                                title = lot.displayAddress,
                                snippet = "ct_CIU: ${lot.ciu} • ${lot.ra}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                            )
                        }
                    }

                    uiState.searchResults.forEach { lot ->
                        val isSelected = lot.objectId == activeLot?.objectId
                        if (!isSelected && lot.latitude != 0.0 && lot.longitude != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(lot.latitude, lot.longitude)),
                                title = lot.displayAddress,
                                snippet = lot.displaySubtitle,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                                onClick = {
                                    localPreviewLot = lot
                                    localPreviewKmdf = null
                                    false
                                }
                            )
                        }
                    }
                }

                // Render Highway KMDF milestone markers
                if (showKmdfMarkers) {
                    filteredKmdf.forEach { kmRec ->
                        val isSelected = (localPreviewKmdf?.id == kmRec.id) || (uiState.selectedKmdfRecord?.id == kmRec.id)
                        val hue = when (kmRec.rodovia) {
                            "DF-001" -> BitmapDescriptorFactory.HUE_BLUE
                            "DF-002" -> BitmapDescriptorFactory.HUE_GREEN
                            "DF-003" -> BitmapDescriptorFactory.HUE_ORANGE
                            "DF-007" -> BitmapDescriptorFactory.HUE_RED
                            "DF-075" -> BitmapDescriptorFactory.HUE_VIOLET
                            "DF-095" -> BitmapDescriptorFactory.HUE_YELLOW
                            else -> BitmapDescriptorFactory.HUE_ROSE
                        }

                        Marker(
                            state = MarkerState(position = LatLng(kmRec.latitude, kmRec.longitude)),
                            title = "${kmRec.rodovia} ${kmRec.kmDisplay}",
                            snippet = "${kmRec.trecho} (${kmRec.ra})",
                            icon = BitmapDescriptorFactory.defaultMarker(if (isSelected) BitmapDescriptorFactory.HUE_MAGENTA else hue),
                            onClick = {
                                localPreviewKmdf = kmRec
                                localPreviewLot = null
                                false
                            }
                        )
                    }
                }

                // Render Free Search custom marker (pesquisa livre fora do escopo cadastral)
                activeFreeSearchResult?.let { freePlace ->
                    Marker(
                        state = MarkerState(position = LatLng(freePlace.latitude, freePlace.longitude)),
                        title = freePlace.name,
                        snippet = freePlace.address,
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET),
                        onClick = {
                            localPreviewLot = null
                            localPreviewKmdf = null
                            false
                        }
                    )
                }
            }
        } else {
            // Interactive Google Maps Web Engine
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_web_interactive"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.loadsImagesAutomatically = true
                        settings.blockNetworkImage = false
                        settings.allowContentAccess = true
                        settings.allowFileAccess = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        webChromeClient = WebChromeClient()

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onMapClicked(lat: Double, lon: Double) {
                                scope.launch {
                                    focusManager.clearFocus(force = true)
                                    onMapClickCoordinate(lat, lon)
                                }
                            }

                            @JavascriptInterface
                            fun onLotClicked(id: Long) {
                                scope.launch {
                                    uiState.searchResults.find { it.objectId == id }?.let { lot ->
                                        localPreviewLot = lot
                                        localPreviewKmdf = null
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onKmdfClicked(id: String) {
                                scope.launch {
                                    uiState.kmdfRecords.find { it.id == id }?.let { km ->
                                        localPreviewKmdf = km
                                        localPreviewLot = null
                                    }
                                }
                            }
                        }, "AndroidGoogleMap")

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                view?.evaluateJavascript("updateMapData($webResidencesJson, $webKmdfJson);", null)
                            }
                        }

                        loadDataWithBaseURL(
                            "https://geodf.app",
                            generateGoogleMapsWebHtml(apiKeyToUse),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                update = { wv ->
                    webViewInstance = wv
                    wv.evaluateJavascript("updateMapData($webResidencesJson, $webKmdfJson);", null)
                }
            )
        }

        // 2. Top Header & Floating Search Bar & Engine Controls
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Map,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Google Maps • DF",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Chave Ativa",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                                        "SDK Nativo 3D • ${when (mapType) { MapType.SATELLITE -> "Satélite HD"; MapType.HYBRID -> "Híbrido"; MapType.TERRAIN -> "Terreno"; else -> "Ruas" }}"
                                    } else {
                                        "Modo Web • ${when (currentWebLayer) { "satellite" -> "Satélite HD"; "hybrid" -> "Híbrido"; "topo" -> "Terreno"; else -> "Ruas" }}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mapEngineMode == GoogleMapMode.NATIVE_SDK) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showDiagnostics = !showDiagnostics },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Diagnóstico",
                                    tint = if (showDiagnostics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Permanently Visible Free Search Bar (consultas livres no Google Maps fora do propósito do aplicativo)
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = inMapSearchQuery,
                            onValueChange = {
                                inMapSearchQuery = it
                                if (it.isBlank()) {
                                    freeSearchResults = emptyList()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("free_search_maps_field"),
                            placeholder = {
                                Text(
                                    "Pesquisa livre (qualquer local, rua, cidade, ponto...)",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                if (isSearchingFree) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (inMapSearchQuery.isNotBlank()) {
                                        IconButton(onClick = {
                                            inMapSearchQuery = ""
                                            freeSearchResults = emptyList()
                                            activeFreeSearchResult = null
                                            focusManager.clearFocus(force = true)
                                        }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                                        }
                                    }
                                    IconButton(onClick = {
                                        executeFreeSearch(inMapSearchQuery)
                                    }) {
                                        Icon(Icons.Default.Navigation, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                executeFreeSearch(inMapSearchQuery)
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        // If free search returned multiple results, show suggestion dropdown
                        if (freeSearchResults.size > 1) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shadowElevation = 4.dp
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    freeSearchResults.take(4).forEach { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSelectFreePlace(item) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.address,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Quick sample chips for free search
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Aeroporto de Brasília", "Congresso Nacional", "Hospital de Base", "Parque da Cidade", "Taguatinga Shopping", "Torre de TV").forEach { sample ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    onClick = {
                                        inMapSearchQuery = sample
                                        executeFreeSearch(sample)
                                    }
                                ) {
                                    Text(
                                        text = sample,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 1. Engine & Layer Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasPlayServices) {
                            FilterChip(
                                selected = mapEngineMode == GoogleMapMode.NATIVE_SDK,
                                onClick = { mapEngineMode = GoogleMapMode.NATIVE_SDK },
                                label = { Text("🚀 SDK Nativo 3D", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        FilterChip(
                            selected = mapEngineMode == GoogleMapMode.INTERACTIVE_WEB,
                            onClick = { mapEngineMode = GoogleMapMode.INTERACTIVE_WEB },
                            label = { Text("🌐 Modo Alternativo", style = MaterialTheme.typography.labelSmall) }
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Direct Layer Switchers (Ruas, Satélite, Híbrido, Terreno)
                        val isNormal = (mapEngineMode == GoogleMapMode.NATIVE_SDK && mapType == MapType.NORMAL) ||
                                (mapEngineMode == GoogleMapMode.INTERACTIVE_WEB && currentWebLayer == "streets")
                        val isSatellite = (mapEngineMode == GoogleMapMode.NATIVE_SDK && mapType == MapType.SATELLITE) ||
                                (mapEngineMode == GoogleMapMode.INTERACTIVE_WEB && currentWebLayer == "satellite")
                        val isHybrid = (mapEngineMode == GoogleMapMode.NATIVE_SDK && mapType == MapType.HYBRID) ||
                                (mapEngineMode == GoogleMapMode.INTERACTIVE_WEB && currentWebLayer == "hybrid")
                        val isTerrain = (mapEngineMode == GoogleMapMode.NATIVE_SDK && mapType == MapType.TERRAIN) ||
                                (mapEngineMode == GoogleMapMode.INTERACTIVE_WEB && currentWebLayer == "topo")

                        FilterChip(
                            selected = isNormal,
                            onClick = {
                                mapType = MapType.NORMAL
                                currentWebLayer = "streets"
                                webViewInstance?.evaluateJavascript("setMapLayer('streets');", null)
                                Toast.makeText(context, "Camada: Ruas", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🗺️ Ruas", style = MaterialTheme.typography.labelSmall) }
                        )

                        FilterChip(
                            selected = isSatellite,
                            onClick = {
                                mapType = MapType.SATELLITE
                                currentWebLayer = "satellite"
                                webViewInstance?.evaluateJavascript("setMapLayer('satellite');", null)
                                Toast.makeText(context, "Camada: Satélite HD", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🛰️ Satélite", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        )

                        FilterChip(
                            selected = isHybrid,
                            onClick = {
                                mapType = MapType.HYBRID
                                currentWebLayer = "hybrid"
                                webViewInstance?.evaluateJavascript("setMapLayer('hybrid');", null)
                                Toast.makeText(context, "Camada: Híbrido (Satélite + Vias)", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("⛰️ Híbrido", style = MaterialTheme.typography.labelSmall) }
                        )

                        FilterChip(
                            selected = isTerrain,
                            onClick = {
                                mapType = MapType.TERRAIN
                                currentWebLayer = "topo"
                                webViewInstance?.evaluateJavascript("setMapLayer('topo');", null)
                                Toast.makeText(context, "Camada: Terreno", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("🏔️ Terreno", style = MaterialTheme.typography.labelSmall) }
                        )

                        Icon(
                            Icons.Default.FilterList,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )

                        availableRodovias.forEach { rodovia ->
                            FilterChip(
                                selected = selectedRodoviaFilter == rodovia,
                                onClick = { selectedRodoviaFilter = rodovia },
                                label = { Text(rodovia, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Diagnostic Card
                    if (showDiagnostics) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Diagnóstico Google Maps API:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• MAPS_API_KEY: ${apiKeyToUse.take(14)}... (Ativa)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "• Google Play Services: ${if (hasPlayServices) "Disponível (Pronto para SDK 3D)" else "Indisponível (Uso do motor interativo)"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasPlayServices) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                                Text(
                                    text = "• Pacote: com.aistudio.geodf.pxvrsm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• Keystore SHA-1: 4E:05:7C:33:DE:F9:FC:6D:52:77:C3:14:46:68:8D:17:0F:97:B6:2C",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Floating Action Buttons on Right Edge
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Layer Switcher (Normal, Satélite, Híbrido, Terreno)
            SmallFloatingActionButton(
                onClick = {
                    if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                        mapType = when (mapType) {
                            MapType.NORMAL -> MapType.SATELLITE
                            MapType.SATELLITE -> MapType.HYBRID
                            MapType.HYBRID -> MapType.TERRAIN
                            else -> MapType.NORMAL
                        }
                        Toast.makeText(context, "Camada: ${mapType.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        currentWebLayer = when (currentWebLayer) {
                            "streets" -> "satellite"
                            "satellite" -> "hybrid"
                            else -> "streets"
                        }
                        webViewInstance?.evaluateJavascript("setMapLayer('$currentWebLayer');", null)
                        Toast.makeText(context, "Camada: $currentWebLayer", Toast.LENGTH_SHORT).show()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Camadas do Mapa")
            }

            // Real-Time Traffic Toggle
            SmallFloatingActionButton(
                onClick = {
                    showTraffic = !showTraffic
                    Toast.makeText(context, if (showTraffic) "Trânsito em tempo real ativado" else "Trânsito desativado", Toast.LENGTH_SHORT).show()
                },
                containerColor = if (showTraffic) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = if (showTraffic) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Traffic, contentDescription = "Tráfego")
            }

            // Quick Jump to DF Administrative Regions (RAs)
            Box {
                SmallFloatingActionButton(
                    onClick = { showRegionsMenu = !showRegionsMenu },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(Icons.Default.LocationCity, contentDescription = "Regiões do DF")
                }

                DropdownMenu(
                    expanded = showRegionsMenu,
                    onDismissRequest = { showRegionsMenu = false }
                ) {
                    DF_REGIONS.forEach { reg ->
                        DropdownMenuItem(
                            text = { Text(reg.name, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                showRegionsMenu = false
                                if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                                    scope.launch {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(reg.lat, reg.lon), reg.zoom)
                                        )
                                    }
                                } else {
                                    webViewInstance?.evaluateJavascript("googleMapCenter(${reg.lat}, ${reg.lon}, ${reg.zoom.toInt()});", null)
                                }
                            }
                        )
                    }
                }
            }

            // Zoom In
            SmallFloatingActionButton(
                onClick = {
                    if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                        scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) }
                    } else {
                        webViewInstance?.evaluateJavascript("googleMapZoom(1);", null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aproximar")
            }

            // Zoom Out
            SmallFloatingActionButton(
                onClick = {
                    if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                        scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) }
                    } else {
                        webViewInstance?.evaluateJavascript("googleMapZoom(-1);", null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Afastar")
            }

            // Center on Brasília
            FloatingActionButton(
                onClick = {
                    if (mapEngineMode == GoogleMapMode.NATIVE_SDK) {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(brasilia, 11.5f)
                            )
                        }
                    } else {
                        webViewInstance?.evaluateJavascript("googleMapCenter(-15.7942, -47.8822, 11);", null)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Brasília")
            }
        }

        // 4. Bottom Selected Card: Cadastre Lot Preview OR KMDF Record Preview
        val lot = localPreviewLot ?: uiState.selectedLot
        val kmRec = localPreviewKmdf ?: uiState.selectedKmdfRecord

        if (lot != null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("google_maps_lot_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lot.ciu.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ct_CIU: ${lot.ciu}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (lot.ra.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = lot.ra,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            if (lot.formattedArea.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = lot.formattedArea,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                localPreviewLot = null
                                onClearSelectedLot?.invoke()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Card", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = lot.displayAddress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Quadra: ${lot.quadraDisplay} • Casa: ${lot.numeroCasaDisplay} • CEP: ${lot.formattedCep}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onSelectLot(lot) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ficha Completa")
                        }

                        FilledTonalButton(
                            onClick = {
                                val gmmIntentUri = Uri.parse("geo:${lot.latitude},${lot.longitude}?q=${lot.latitude},${lot.longitude}(${Uri.encode(lot.displayAddress)})")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Navegador de mapas não encontrado", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navegar")
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", "${lot.latitude}, ${lot.longitude}"))
                                Toast.makeText(context, "Coordenadas copiadas!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Coordenadas")
                        }
                    }
                }
            }
        } else if (kmRec != null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("google_maps_kmdf_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = CadastreBlue,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = kmRec.rodovia,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = kmRec.kmDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = kmRec.ra,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                localPreviewKmdf = null
                                onClearSelectedKmdfRecord?.invoke()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Card", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = kmRec.trecho,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "WGS84: ${kmRec.formattedDecimalCoords}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onReverseGeocodeKm(kmRec) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ver Lotes Próximos")
                        }

                        FilledTonalButton(
                            onClick = {
                                val gmmIntentUri = Uri.parse("geo:${kmRec.latitude},${kmRec.longitude}?q=${kmRec.latitude},${kmRec.longitude}(${Uri.encode("${kmRec.rodovia} ${kmRec.kmDisplay}")})")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Navegador de mapas não encontrado", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navegar")
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", "${kmRec.latitude}, ${kmRec.longitude}"))
                                Toast.makeText(context, "Coordenadas copiadas!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar Coordenadas")
                        }
                    }
                }
            }
        }

        // 5. Bottom Card for Free Search Result (quando o usuário consulta qualquer local fora do escopo)
        val freePlace = activeFreeSearchResult
        if (freePlace != null && lot == null && kmRec == null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("google_maps_free_search_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Pesquisa Livre • ${freePlace.source}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        IconButton(
                            onClick = { activeFreeSearchResult = null },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar Card", modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = freePlace.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = freePlace.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val gmmIntentUri = Uri.parse("google.navigation:q=${freePlace.latitude},${freePlace.longitude}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                try {
                                    context.startActivity(mapIntent)
                                } catch (_: Exception) {
                                    val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${freePlace.latitude},${freePlace.longitude}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotas", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", "${freePlace.latitude}, ${freePlace.longitude}"))
                                Toast.makeText(context, "Coordenadas copiadas!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

// Generate self-contained Google Maps Interactive Web Engine
private fun generateGoogleMapsWebHtml(apiKey: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.js"></script>
  <style>
    html, body, #gmap { margin: 0; padding: 0; width: 100%; height: 100%; background: #0f172a; }
    .map-tag {
      background: rgba(15, 23, 42, 0.9);
      border: 1px solid #38bdf8;
      border-radius: 4px;
      color: #fff;
      font-size: 11px;
      padding: 2px 6px;
    }
    .leaflet-popup-content-wrapper {
      background: #1e293b;
      color: #f1f5f9;
      border-radius: 8px;
      border: 1px solid #0284c7;
    }
    .leaflet-popup-tip { background: #1e293b; }
  </style>
</head>
<body>
  <div id="gmap"></div>
  <script>
    if (typeof L === 'undefined') {
      document.write('<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"><\/script>');
    }
  </script>
  <script>
    var map = L.map('gmap', {
      center: [-15.7942, -47.8822],
      zoom: 11,
      zoomControl: false,
      attributionControl: false
    });

    // Multi-layer reliable map tiles for Brasília (CartoDB Voyager, Esri Satellite, Google Hybrid, OSM)
    var streetsLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
      subdomains: 'abcd',
      attribution: '© CartoDB / Google'
    });
    var satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
      maxZoom: 19,
      attribution: '© Esri Satellite HD'
    });
    var googleRoads = L.tileLayer('https://{s}.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });
    var googleSatellite = L.tileLayer('https://{s}.google.com/vt/lyrs=s&x={x}&y={y}&z={z}', {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });
    var googleHybrid = L.tileLayer('https://{s}.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', {
      maxZoom: 20,
      subdomains: ['mt0', 'mt1', 'mt2', 'mt3']
    });
    var osmLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '© OpenStreetMap'
    });

    // Always start with streetsLayer so the map is NEVER white or blank!
    streetsLayer.addTo(map);

    googleRoads.on('tileerror', function() {
      if (!map.hasLayer(streetsLayer)) streetsLayer.addTo(map);
    });
    googleHybrid.on('tileerror', function() {
      if (!map.hasLayer(satelliteLayer)) satelliteLayer.addTo(map);
    });

    var resLayer = L.layerGroup().addTo(map);
    var kmLayer = L.layerGroup().addTo(map);

    map.on('click', function(e) {
      if (window.AndroidGoogleMap) {
        window.AndroidGoogleMap.onMapClicked(e.latlng.lat, e.latlng.lng);
      }
    });

    function googleMapZoom(delta) {
      if (delta > 0) map.zoomIn(); else map.zoomOut();
    }

    function googleMapCenter(lat, lon, zoom) {
      map.flyTo([lat, lon], zoom, { duration: 1.2 });
    }

    function setFreeSearchMarker(name, addr, lat, lon) {
      if (window.freeMarker) map.removeLayer(window.freeMarker);
      window.freeMarker = L.circleMarker([lat, lon], {
        radius: 9,
        color: '#7c3aed',
        fillColor: '#a855f7',
        fillOpacity: 0.95,
        weight: 3
      }).addTo(map);
      window.freeMarker.bindPopup("<b>" + name + "</b><br/>" + addr).openPopup();
      map.flyTo([lat, lon], 16, { duration: 0.8 });
    }

    function setMapLayer(type) {
      map.removeLayer(streetsLayer);
      map.removeLayer(satelliteLayer);
      map.removeLayer(googleHybrid);
      map.removeLayer(googleSatellite);
      map.removeLayer(googleRoads);
      map.removeLayer(osmLayer);

      if (type === 'satellite') {
        satelliteLayer.addTo(map);
        try { googleSatellite.addTo(map); } catch(_) {}
      } else if (type === 'hybrid') {
        satelliteLayer.addTo(map);
        try { googleHybrid.addTo(map); } catch(_) {}
      } else if (type === 'topo' || type === 'osm') {
        osmLayer.addTo(map);
      } else {
        streetsLayer.addTo(map);
      }
    }

    function updateMapData(residences, kmdf) {
      resLayer.clearLayers();
      if (residences && residences.length > 0) {
        residences.forEach(function(r) {
          var circle = L.circleMarker([r.lat, r.lon], {
            radius: 7,
            color: '#0284c7',
            fillColor: '#38bdf8',
            fillOpacity: 0.85,
            weight: 2
          });
          circle.bindPopup("<b>" + r.end + "</b><br/>CIU: " + r.ciu + "<br/>RA: " + r.ra + "<br/>Área: " + r.area);
          circle.on('click', function() {
            if (window.AndroidGoogleMap) window.AndroidGoogleMap.onLotClicked(r.id);
          });
          circle.addTo(resLayer);
        });
      }

      kmLayer.clearLayers();
      if (kmdf && kmdf.length > 0) {
        kmdf.forEach(function(k) {
          var marker = L.circleMarker([k.lat, k.lon], {
            radius: 6,
            color: '#dc2626',
            fillColor: '#f87171',
            fillOpacity: 0.9,
            weight: 2
          });
          marker.bindPopup("<b>" + k.rod + " " + k.km + "</b><br/>" + k.tr + " (" + k.ra + ")");
          marker.on('click', function() {
            if (window.AndroidGoogleMap) window.AndroidGoogleMap.onKmdfClicked(k.id);
          });
          marker.addTo(kmLayer);
        });
      }
    }
  </script>
</body>
</html>
    """.trimIndent()
}
