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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.data.model.CadastreLot
import com.example.data.model.KmdfRecord
import com.example.ui.theme.CadastreBlue
import com.example.ui.viewmodel.GeocodingUiState
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DigitalCartographyScreen(
    uiState: GeocodingUiState,
    onSelectLot: (CadastreLot) -> Unit,
    onMapCoordinateTapped: (Double, Double) -> Unit,
    onSelectKmdfRecord: (KmdfRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeBaseLayer by remember { mutableStateOf("topographic") } // topographic, satellite, streets
    var showResidencesLayer by remember { mutableStateOf(true) }
    var showKmdfLayer by remember { mutableStateOf(true) }
    var showRaBoundaries by remember { mutableStateOf(true) }
    var showLayersMenu by remember { mutableStateOf(false) }

    var selectedCoordinateLat by remember { mutableStateOf(-15.7942) }
    var selectedCoordinateLon by remember { mutableStateOf(-47.8822) }
    var selectedUtmX by remember { mutableStateOf(191240.0) }
    var selectedUtmY by remember { mutableStateOf(8252110.0) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Convert sample residences and KMDF to JSON for Leaflet layers
    val residencesJson = remember(uiState.searchResults) {
        val array = JSONArray()
        uiState.searchResults.take(150).forEach { lot ->
            if (lot.latitude != 0.0 && lot.longitude != 0.0) {
                val obj = JSONObject()
                obj.put("id", lot.objectId)
                obj.put("ciu", lot.ciu)
                obj.put("endereco", lot.displayAddress)
                obj.put("ra", lot.ra)
                obj.put("cep", lot.formattedCep)
                obj.put("area", lot.formattedArea)
                obj.put("lat", lot.latitude)
                obj.put("lon", lot.longitude)
                array.put(obj)
            }
        }
        array.toString()
    }

    val kmdfJson = remember(uiState.kmdfRecords) {
        val array = JSONArray()
        uiState.kmdfRecords.take(200).forEach { km ->
            val obj = JSONObject()
            obj.put("id", km.id)
            obj.put("rodovia", km.rodovia)
            obj.put("km", km.kmDisplay)
            obj.put("trecho", km.trecho)
            obj.put("ra", km.ra)
            obj.put("lat", km.latitude)
            obj.put("lon", km.longitude)
            array.put(obj)
        }
        array.toString()
    }

    // Administrative Regions list for rapid fly-to
    val raCenters = remember {
        listOf(
            "PLANO PILOTO" to Pair(-15.7942, -47.8822),
            "TAGUATINGA" to Pair(-15.8331, -48.0567),
            "CEILÂNDIA" to Pair(-15.8208, -48.1130),
            "ÁGUAS CLARAS" to Pair(-15.8399, -48.0260),
            "SAMAMBAIA" to Pair(-15.8752, -48.0863),
            "SOBRADINHO" to Pair(-15.6528, -47.7892),
            "GAMA" to Pair(-16.0178, -48.0645),
            "LAGO SUL" to Pair(-15.8560, -47.8720),
            "LAGO NORTE" to Pair(-15.7330, -47.8540),
            "GUARA" to Pair(-15.8190, -47.9820),
            "VICENTE PIRES" to Pair(-15.8070, -48.0310),
            "SANTA MARIA" to Pair(-16.0210, -47.9980),
            "PARK WAY" to Pair(-15.8920, -47.9540)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("digital_cartography_container")
    ) {
        // High-Resolution Interactive Cartographic Leaflet Engine via AndroidView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("cartography_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewRef = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    webChromeClient = WebChromeClient()

                    // JavaScript interface for 2-way touch communication
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onCoordinateClicked(lat: Double, lon: Double) {
                            scope.launch {
                                selectedCoordinateLat = lat
                                selectedCoordinateLon = lon
                                val (ux, uy) = latLonToUtm23s(lat, lon)
                                selectedUtmX = ux
                                selectedUtmY = uy
                                onMapCoordinateTapped(lat, lon)
                            }
                        }

                        @JavascriptInterface
                        fun onResidenceSelected(id: Long) {
                            scope.launch {
                                uiState.searchResults.find { it.objectId == id }?.let {
                                    onSelectLot(it)
                                }
                            }
                        }

                        @JavascriptInterface
                        fun onKmdfSelected(id: String) {
                            scope.launch {
                                uiState.kmdfRecords.find { it.id == id }?.let {
                                    onSelectKmdfRecord(it)
                                }
                            }
                        }
                    }, "AndroidCarto")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Initialize data layers
                            view?.evaluateJavascript(
                                "loadCartographyData($residencesJson, $kmdfJson);",
                                null
                            )
                        }
                    }

                    loadDataWithBaseURL(
                        "https://ide.df.gov.br",
                        generateCartographyHtml(),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                webViewRef = webView
                webView.evaluateJavascript("loadCartographyData($residencesJson, $kmdfJson);", null)
            }
        )

        // Top Overlay: Cartography Title & Layer Selector
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
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
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
                                Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Cartografia Digital • Distrito Federal",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "SIRGAS 2000 / UTM 23S • WGS84 • Escala Geográfica",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        IconButton(
                            onClick = { showLayersMenu = !showLayersMenu },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Camadas Cartográficas",
                                tint = if (showLayersMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Quick Jump to Administrative Regions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        raCenters.forEach { (name, coords) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    webViewRef?.evaluateJavascript(
                                        "flyToRegion(${coords.first}, ${coords.second}, 14);",
                                        null
                                    )
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Expandable Layer Configuration Panel
                    AnimatedVisibility(visible = showLayersMenu) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Base Cartográfica:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = activeBaseLayer == "topographic",
                                    onClick = {
                                        activeBaseLayer = "topographic"
                                        webViewRef?.evaluateJavascript("switchBaseLayer('topographic');", null)
                                    },
                                    label = { Text("Topografia", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = activeBaseLayer == "satellite",
                                    onClick = {
                                        activeBaseLayer = "satellite"
                                        webViewRef?.evaluateJavascript("switchBaseLayer('satellite');", null)
                                    },
                                    label = { Text("Ortofoto / Satélite", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = activeBaseLayer == "streets",
                                    onClick = {
                                        activeBaseLayer = "streets"
                                        webViewRef?.evaluateJavascript("switchBaseLayer('streets');", null)
                                    },
                                    label = { Text("Vias e Quadras", style = MaterialTheme.typography.labelSmall) }
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Camadas Temáticas:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = showResidencesLayer,
                                    onClick = {
                                        showResidencesLayer = !showResidencesLayer
                                        webViewRef?.evaluateJavascript("toggleResidences($showResidencesLayer);", null)
                                    },
                                    label = { Text("Residências (${uiState.searchResults.size})", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = showKmdfLayer,
                                    onClick = {
                                        showKmdfLayer = !showKmdfLayer
                                        webViewRef?.evaluateJavascript("toggleKmdf($showKmdfLayer);", null)
                                    },
                                    label = { Text("Rodovias KM-DF", style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = showRaBoundaries,
                                    onClick = {
                                        showRaBoundaries = !showRaBoundaries
                                        webViewRef?.evaluateJavascript("toggleRaBoundaries($showRaBoundaries);", null)
                                    },
                                    label = { Text("Limites RAs", style = MaterialTheme.typography.labelSmall) }
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
                onClick = { webViewRef?.evaluateJavascript("mapZoomIn();", null) },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Aproximar")
            }

            SmallFloatingActionButton(
                onClick = { webViewRef?.evaluateJavascript("mapZoomOut();", null) },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Afastar")
            }

            FloatingActionButton(
                onClick = {
                    webViewRef?.evaluateJavascript("flyToRegion(-15.7942, -47.8822, 11);", null)
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Brasília")
            }
        }

        // Bottom Coordinate & Geodetic Inspector Card
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Coordenadas do Ponto Selecionado:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "WGS84: ${String.format(Locale.US, "%.6f, %.6f", selectedCoordinateLat, selectedCoordinateLon)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SIRGAS 2000 UTM 23S: X ${String.format(Locale.US, "%.1f", selectedUtmX)} m • Y ${String.format(Locale.US, "%.1f", selectedUtmY)} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                FilledTonalButton(
                    onClick = {
                        val text = "WGS84: $selectedCoordinateLat, $selectedCoordinateLon\nUTM 23S: X $selectedUtmX, Y $selectedUtmY"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", text))
                        Toast.makeText(context, "Coordenadas copiadas!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar")
                }
            }
        }
    }
}

// Convert Lat/Lon to UTM Zone 23S
private fun latLonToUtm23s(lat: Double, lon: Double): Pair<Double, Double> {
    val a = 6378137.0
    val f = 1 / 298.257223563
    val b = a * (1 - f)
    val e2 = 1 - (b * b) / (a * a)
    val ePrime2 = (a * a - b * b) / (b * b)
    val k0 = 0.9996
    val falseEasting = 500000.0
    val falseNorthing = 10000000.0
    val cm = -45.0 // Central meridian of Zone 23

    val latRad = Math.toRadians(lat)
    val lonRad = Math.toRadians(lon)
    val cmRad = Math.toRadians(cm)

    val deltaLon = lonRad - cmRad

    val n = a / sqrt(1 - e2 * sin(latRad) * sin(latRad))
    val t = tan(latRad) * tan(latRad)
    val c = ePrime2 * cos(latRad) * cos(latRad)
    val aa = cos(latRad) * deltaLon

    val m = a * ((1 - e2 / 4 - 3 * e2 * e2 / 64 - 5 * e2 * e2 * e2 / 256) * latRad
            - (3 * e2 / 8 + 3 * e2 * e2 / 32 + 45 * e2 * e2 * e2 / 1024) * sin(2 * latRad)
            + (15 * e2 * e2 / 256 + 45 * e2 * e2 * e2 / 1024) * sin(4 * latRad)
            - (35 * e2 * e2 * e2 / 3072) * sin(6 * latRad))

    val easting = falseEasting + k0 * n * (aa + (1 - t + c) * aa * aa * aa / 6.0
            + (5 - 18 * t + t * t + 72 * c - 58 * ePrime2) * aa * aa * aa * aa * aa / 120.0)

    val northing = falseNorthing + k0 * (m + n * tan(latRad) * (aa * aa / 2.0
            + (5 - t + 9 * c + 4 * c * c) * aa * aa * aa * aa / 24.0
            + (61 - 58 * t + t * t + 600 * c - 330 * ePrime2) * aa * aa * aa * aa * aa * aa / 720.0))

    return Pair(easting, northing)
}

// Generate self-contained HTML for Leaflet Digital Cartography with offline raster fallback
private fun generateCartographyHtml(): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    html, body, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #1a1a24; }
    .custom-label {
      background: rgba(15, 23, 42, 0.85);
      border: 1px solid #38bdf8;
      border-radius: 4px;
      color: #f8fafc;
      font-size: 11px;
      font-weight: bold;
      padding: 2px 6px;
      white-space: nowrap;
    }
    .leaflet-popup-content-wrapper {
      background: #0f172a;
      color: #f8fafc;
      border-radius: 10px;
      border: 1px solid #38bdf8;
    }
    .leaflet-popup-tip { background: #0f172a; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    var map = L.map('map', {
      center: [-15.7942, -47.8822],
      zoom: 11,
      zoomControl: false,
      attributionControl: false
    });

    // Base Layers
    var topoLayer = L.tileLayer('https://{s}.tile.opentopomap.org/{z}/{x}/{y}.png', { maxZoom: 17 });
    var satelliteLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', { maxZoom: 18 });
    var streetsLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 19 });

    topoLayer.addTo(map);
    var currentBase = topoLayer;

    var residencesLayer = L.layerGroup().addTo(map);
    var kmdfLayer = L.layerGroup().addTo(map);
    var raLayer = L.layerGroup().addTo(map);

    // RA Boundaries & Labels (Approximate official centroids of the 35 RAs)
    var ras = [
      { name: "Plano Piloto", lat: -15.7942, lon: -47.8822 },
      { name: "Taguatinga", lat: -15.8331, lon: -48.0567 },
      { name: "Ceilândia", lat: -15.8208, lon: -48.1130 },
      { name: "Águas Claras", lat: -15.8399, lon: -48.0260 },
      { name: "Samambaia", lat: -15.8752, lon: -48.0863 },
      { name: "Sobradinho", lat: -15.6528, lon: -47.7892 },
      { name: "Gama", lat: -16.0178, lon: -48.0645 },
      { name: "Lago Sul", lat: -15.8560, lon: -47.8720 },
      { name: "Lago Norte", lat: -15.7330, lon: -47.8540 },
      { name: "Guará", lat: -15.8190, lon: -47.9820 },
      { name: "Vicente Pires", lat: -15.8070, lon: -48.0310 },
      { name: "Santa Maria", lat: -16.0210, lon: -47.9980 },
      { name: "São Sebastião", lat: -15.9080, lon: -47.7710 },
      { name: "Recanto das Emas", lat: -15.9030, lon: -48.0690 },
      { name: "Sudoeste/Octogonal", lat: -15.8020, lon: -47.9250 },
      { name: "Cruzeiro", lat: -15.7930, lon: -47.9400 },
      { name: "Brazlândia", lat: -15.6790, lon: -48.2010 },
      { name: "Planaltina", lat: -15.6190, lon: -47.6520 },
      { name: "Paranoá", lat: -15.7720, lon: -47.7760 },
      { name: "Park Way", lat: -15.8920, lon: -47.9540 },
      { name: "Jardim Botânico", lat: -15.8770, lon: -47.8180 },
      { name: "Riacho Fundo I", lat: -15.8820, lon: -48.0160 },
      { name: "Riacho Fundo II", lat: -15.9010, lon: -48.0380 },
      { name: "Sobradinho II", lat: -15.6370, lon: -47.8170 },
      { name: "Itapoã", lat: -15.7530, lon: -47.7650 },
      { name: "SIA", lat: -15.8110, lon: -47.9570 },
      { name: "SCIA/Estrutural", lat: -15.7770, lon: -47.9910 },
      { name: "Varjão", lat: -15.7140, lon: -47.8760 },
      { name: "Candangolândia", lat: -15.8520, lon: -47.9510 },
      { name: "Fercal", lat: -15.5890, lon: -47.8740 },
      { name: "Sol Nascente", lat: -15.8270, lon: -48.1480 },
      { name: "Arniqueira", lat: -15.8580, lon: -48.0190 },
      { name: "Arapoanga", lat: -15.6260, lon: -47.6250 },
      { name: "Água Quente", lat: -15.9720, lon: -48.1710 }
    ];

    ras.forEach(function(ra) {
      L.circleMarker([ra.lat, ra.lon], {
        radius: 6,
        color: '#38bdf8',
        fillColor: '#0284c7',
        fillOpacity: 0.7
      }).bindTooltip(ra.name, { permanent: true, direction: 'center', className: 'custom-label' }).addTo(raLayer);
    });

    // Click handler for coordinates
    map.on('click', function(e) {
      if (window.AndroidCarto) {
        window.AndroidCarto.onCoordinateClicked(e.latlng.lat, e.latlng.lng);
      }
    });

    // Functions called from Android Kotlin
    function switchBaseLayer(name) {
      map.removeLayer(currentBase);
      if (name === 'satellite') {
        currentBase = satelliteLayer;
      } else if (name === 'streets') {
        currentBase = streetsLayer;
      } else {
        currentBase = topoLayer;
      }
      currentBase.addTo(map);
    }

    function toggleResidences(show) {
      if (show) map.addLayer(residencesLayer); else map.removeLayer(residencesLayer);
    }

    function toggleKmdf(show) {
      if (show) map.addLayer(kmdfLayer); else map.removeLayer(kmdfLayer);
    }

    function toggleRaBoundaries(show) {
      if (show) map.addLayer(raLayer); else map.removeLayer(raLayer);
    }

    function flyToRegion(lat, lon, zoom) {
      map.flyTo([lat, lon], zoom, { duration: 1.2 });
    }

    function mapZoomIn() { map.zoomIn(); }
    function mapZoomOut() { map.zoomOut(); }

    function loadCartographyData(residences, kmdf) {
      residencesLayer.clearLayers();
      if (residences && residences.length > 0) {
        residences.forEach(function(r) {
          var marker = L.circleMarker([r.lat, r.lon], {
            radius: 7,
            color: '#f59e0b',
            fillColor: '#fbbf24',
            fillOpacity: 0.9,
            weight: 2
          });
          marker.bindPopup("<b>" + r.endereco + "</b><br/>CIU: " + r.ciu + "<br/>RA: " + r.ra + "<br/>Área: " + r.area);
          marker.on('click', function() {
            if (window.AndroidCarto) window.AndroidCarto.onResidenceSelected(r.id);
          });
          marker.addTo(residencesLayer);
        });
      }

      kmdfLayer.clearLayers();
      if (kmdf && kmdf.length > 0) {
        kmdf.forEach(function(k) {
          var marker = L.circleMarker([k.lat, k.lon], {
            radius: 6,
            color: '#3b82f6',
            fillColor: '#60a5fa',
            fillOpacity: 0.85,
            weight: 2
          });
          marker.bindPopup("<b>" + k.rodovia + " " + k.km + "</b><br/>" + k.trecho + " (" + k.ra + ")");
          marker.on('click', function() {
            if (window.AndroidCarto) window.AndroidCarto.onKmdfSelected(k.id);
          });
          marker.addTo(kmdfLayer);
        });
      }
    }
  </script>
</body>
</html>
    """.trimIndent()
}
