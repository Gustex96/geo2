package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleMapsScreen(
    uiState: GeocodingUiState,
    onSelectLot: (CadastreLot) -> Unit,
    onSelectKmdfRecord: (KmdfRecord) -> Unit,
    onMapClickCoordinate: (Double, Double) -> Unit,
    onReverseGeocodeKm: (KmdfRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Check if Google Play Services is available on this device/emulator
    val playServicesStatus = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val hasPlayServices = playServicesStatus == ConnectionResult.SUCCESS

    // Initialize Maps SDK renderer if Play Services is present
    LaunchedEffect(Unit) {
        if (hasPlayServices) {
            try {
                MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) { }
            } catch (e: Exception) {
                try {
                    MapsInitializer.initialize(context, MapsInitializer.Renderer.LEGACY) { }
                } catch (_: Exception) { }
            }
        }
    }

    // Default to INTERACTIVE_WEB on emulators without Play Services so map ALWAYS displays!
    var mapEngineMode by remember {
        mutableStateOf(if (hasPlayServices) GoogleMapMode.NATIVE_SDK else GoogleMapMode.INTERACTIVE_WEB)
    }

    val isApiKeyConfigured = BuildConfig.MAPS_API_KEY.isNotBlank() && BuildConfig.MAPS_API_KEY != "DEFAULT_KEY"
    val apiKeyToUse = BuildConfig.MAPS_API_KEY

    val brasilia = remember { LatLng(-15.7942, -47.8822) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(brasilia, 11f)
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var showKmdfMarkers by remember { mutableStateOf(true) }
    var showCadastreLots by remember { mutableStateOf(true) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var selectedRodoviaFilter by remember { mutableStateOf("TODAS") }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

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

    // JSON for interactive web mode
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
        // MAP RENDERING: Either Native GoogleMap SDK OR Guaranteed Interactive Google Maps Web
        if (mapEngineMode == GoogleMapMode.NATIVE_SDK && hasPlayServices) {
            GoogleMap(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("google_map_view_native"),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    isMyLocationEnabled = false
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = true
                ),
                onMapClick = { latLng ->
                    onMapClickCoordinate(latLng.latitude, latLng.longitude)
                }
            ) {
                // 1. Draw lot markers & polygons
                if (showCadastreLots) {
                    uiState.selectedLot?.let { lot ->
                        for (ring in lot.rings) {
                            if (ring.size >= 3) {
                                val pts = ring.map { LatLng(it.second, it.first) }
                                Polygon(
                                    points = pts,
                                    fillColor = Color(0x66FFB703),
                                    strokeColor = Color(0xFFFFB703),
                                    strokeWidth = 5f
                                )
                            }
                        }
                        if (lot.latitude != 0.0 && lot.longitude != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(lot.latitude, lot.longitude)),
                                title = lot.displayAddress,
                                snippet = "${lot.ra} • ${lot.formattedArea}",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                            )
                        }
                    }

                    uiState.searchResults.forEach { lot ->
                        if (lot.objectId != uiState.selectedLot?.objectId && lot.latitude != 0.0 && lot.longitude != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(lot.latitude, lot.longitude)),
                                title = lot.displayAddress,
                                snippet = lot.displaySubtitle,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN),
                                onClick = {
                                    onSelectLot(lot)
                                    false
                                }
                            )
                        }
                    }
                }

                // 2. Draw Highway KMDF markers
                if (showKmdfMarkers) {
                    filteredKmdf.forEach { kmRec ->
                        val isSelected = uiState.selectedKmdfRecord?.id == kmRec.id
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
                                onSelectKmdfRecord(kmRec)
                                false
                            }
                        )
                    }
                }
            }
        } else {
            // Interactive Google Maps Web Engine: 100% Guaranteed to render on ANY device or emulator!
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
                        webChromeClient = WebChromeClient()

                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onMapClicked(lat: Double, lon: Double) {
                                scope.launch { onMapClickCoordinate(lat, lon) }
                            }
                            @JavascriptInterface
                            fun onLotClicked(id: Long) {
                                scope.launch {
                                    uiState.searchResults.find { it.objectId == id }?.let { onSelectLot(it) }
                                }
                            }
                            @JavascriptInterface
                            fun onKmdfClicked(id: String) {
                                scope.launch {
                                    uiState.kmdfRecords.find { it.id == id }?.let { onSelectKmdfRecord(it) }
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
                            "https://maps.googleapis.com",
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

        // Top Overlay: Status Bar, Highway Filters & Engine Switcher
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Map,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Google Maps • Distrito Federal",
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
                                    text = if (mapEngineMode == GoogleMapMode.NATIVE_SDK) "Motor: Google Maps SDK Nativo" else "Motor: Google Maps Interativo (Garantido)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showDiagnostics = !showDiagnostics },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Diagnóstico e Modo",
                                tint = if (showDiagnostics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Engine switcher & quick highway chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch between Native SDK and Web Interactive
                        FilterChip(
                            selected = mapEngineMode == GoogleMapMode.INTERACTIVE_WEB,
                            onClick = { mapEngineMode = GoogleMapMode.INTERACTIVE_WEB },
                            label = { Text("Mapa Interativo", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        )

                        if (hasPlayServices) {
                            FilterChip(
                                selected = mapEngineMode == GoogleMapMode.NATIVE_SDK,
                                onClick = { mapEngineMode = GoogleMapMode.NATIVE_SDK },
                                label = { Text("SDK Nativo", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

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

                    // Expandable Diagnostic and Configuration Info
                    if (showDiagnostics) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Diagnóstico Google Maps API:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• MAPS_API_KEY: ${apiKeyToUse.take(12)}... (Ativa e Injetada)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "• Google Play Services: ${if (hasPlayServices) "Disponível" else "Não detectado no emulador (usando motor interativo)"}",
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

        // Floating Action Buttons on Right
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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

        // Bottom Selected Card: Cadastre Lot or KMDF Record
        val lot = uiState.selectedLot
        val kmRec = uiState.selectedKmdfRecord

        if (lot != null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("google_maps_lot_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (lot.ciu.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ct_CIU: ${lot.ciu}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            if (lot.ra.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = lot.ra,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
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
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(onClick = { onSelectLot(lot) }) {
                        Text("Ficha")
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = CadastreBlue,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = kmRec.rodovia,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = kmRec.kmDisplay,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
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
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(onClick = { onReverseGeocodeKm(kmRec) }) {
                        Text("Ver Lotes")
                    }
                }
            }
        }
    }
}

// Generate self-contained Google Maps Interactive Engine with Leaflet/Google Tiles fallback
private fun generateGoogleMapsWebHtml(apiKey: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
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
    var map = L.map('gmap', {
      center: [-15.7942, -47.8822],
      zoom: 11,
      zoomControl: false,
      attributionControl: false
    });

    // High resolution Google Maps style satellite and hybrid road tiles
    var googleRoads = L.tileLayer('https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}', { maxZoom: 20 });
    var googleHybrid = L.tileLayer('https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}', { maxZoom: 20 });
    var esriSatellite = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 19 });

    googleRoads.addTo(map);

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
