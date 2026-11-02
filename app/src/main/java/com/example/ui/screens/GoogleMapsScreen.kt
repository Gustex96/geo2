package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
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
import com.example.BuildConfig
import com.example.data.model.CadastreLot
import com.example.data.model.KmdfRecord
import com.example.ui.theme.CadastreBlue
import com.example.ui.theme.CadastreYellow
import com.example.ui.viewmodel.GeocodingUiState
import com.google.android.gms.maps.CameraUpdateFactory
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
import java.util.Locale

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

    // Check if real API key is configured
    val isApiKeyConfigured = BuildConfig.MAPS_API_KEY.isNotBlank() && BuildConfig.MAPS_API_KEY != "DEFAULT_KEY"

    // Default center in Brasília / Distrito Federal
    val brasilia = remember { LatLng(-15.7942, -47.8822) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(brasilia, 11f)
    }

    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var showKmdfMarkers by remember { mutableStateOf(true) }
    var showCadastreLots by remember { mutableStateOf(true) }
    var showMapControls by remember { mutableStateOf(false) }
    var selectedRodoviaFilter by remember { mutableStateOf("TODAS") }

    // If a lot or KMDF is selected externally, animate camera to it
    LaunchedEffect(uiState.selectedLot) {
        uiState.selectedLot?.let { lot ->
            if (lot.latitude != 0.0 && lot.longitude != 0.0) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(lot.latitude, lot.longitude), 16f)
                )
            }
        }
    }

    LaunchedEffect(uiState.selectedKmdfRecord) {
        uiState.selectedKmdfRecord?.let { kmRec ->
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(kmRec.latitude, kmRec.longitude), 15f)
            )
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("google_maps_screen_container")
    ) {
        // Native Google Map SDK Component
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .testTag("google_map_view"),
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
            // 1. Draw Lot Polygons & Center Markers
            if (showCadastreLots) {
                // Highlight selected lot polygon
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

                // Markers for other search results
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

            // 2. Draw Highway KMDF Markers
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

        // Top Overlay: API Key Notice (if not yet provided in Secrets) + Layer Filters Bar
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Notice banner if MAPS_API_KEY is using default placeholder
            if (!isApiKeyConfigured) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Maps SDK Integrado",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Configure sua MAPS_API_KEY no painel Secrets do AI Studio para habilitar os mapas nativos completos. As camadas WMS/MapServer continuam ativas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Quick controls card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
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
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Google Maps • Distrito Federal",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { showMapControls = !showMapControls },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Camadas e Tipo",
                                tint = if (showMapControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Highway Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                    // Expandable Map Settings
                    if (showMapControls) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = mapType == MapType.NORMAL,
                                onClick = { mapType = MapType.NORMAL },
                                label = { Text("Normal", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = mapType == MapType.SATELLITE,
                                onClick = { mapType = MapType.SATELLITE },
                                label = { Text("Satélite", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = mapType == MapType.HYBRID,
                                onClick = { mapType = MapType.HYBRID },
                                label = { Text("Híbrido", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = mapType == MapType.TERRAIN,
                                onClick = { mapType = MapType.TERRAIN },
                                label = { Text("Relevo", style = MaterialTheme.typography.labelSmall) }
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = showKmdfMarkers,
                                onClick = { showKmdfMarkers = !showKmdfMarkers },
                                label = { Text("Marcos KMZ (${filteredKmdf.size})", style = MaterialTheme.typography.labelSmall) }
                            )
                            FilterChip(
                                selected = showCadastreLots,
                                onClick = { showCadastreLots = !showCadastreLots },
                                label = { Text("Lotes Cadastrais", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Buttons on Right Edge: Zoom & Recenter
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aproximar")
            }

            SmallFloatingActionButton(
                onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Afastar")
            }

            FloatingActionButton(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(brasilia, 11.5f)
                        )
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
                            if (lot.ra.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = lot.ra,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "Lote Cadastral",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
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
                            text = "${lot.formattedArea} • ${lot.formattedDecimalCoords}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
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
