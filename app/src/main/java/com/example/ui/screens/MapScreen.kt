package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.CadastreLot
import com.example.ui.theme.CadastreYellow
import com.example.ui.viewmodel.GeocodingUiState
import com.example.ui.viewmodel.MapEngine
import java.util.Locale

@Composable
fun MapScreen(
    uiState: GeocodingUiState,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenterBrasilia: () -> Unit,
    onToggleMapEngine: () -> Unit,
    onToggleLayer: (String) -> Unit,
    onMapTapped: (Double, Double) -> Unit,
    onSelectLot: (CadastreLot) -> Unit,
    onPan: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLayersMenu by remember { mutableStateOf(false) }

    val halfDelta = uiState.mapZoomDelta / 2.0
    val minLon = uiState.mapCenterLon - halfDelta
    val maxLon = uiState.mapCenterLon + halfDelta
    val minLat = uiState.mapCenterLat - halfDelta
    val maxLat = uiState.mapCenterLat + halfDelta

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("map_screen_container")
    ) {
        // Map Display Area with tap coordinate detection
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val boxWidthPx = constraints.maxWidth.toFloat()
            val boxHeightPx = constraints.maxHeight.toFloat()

            // Map Image from WMS / MapServer
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(uiState.mapTileUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Mapa Cadastral WMS IDE DF",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("wms_map_image")
                    .pointerInput(uiState.mapCenterLat, uiState.mapCenterLon, uiState.mapZoomDelta) {
                        detectTapGestures { offset ->
                            if (boxWidthPx > 0 && boxHeightPx > 0) {
                                val tapRatioX = (offset.x / boxWidthPx).toDouble().coerceIn(0.0, 1.0)
                                val tapRatioY = (offset.y / boxHeightPx).toDouble().coerceIn(0.0, 1.0)
                                val tapLon = minLon + tapRatioX * (maxLon - minLon)
                                val tapLat = maxLat - tapRatioY * (maxLat - minLat)
                                onMapTapped(tapLat, tapLon)
                            }
                        }
                    },
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Renderizando camadas cadastrais...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Aguardando imagem do servidor WMS / MapServer IDE DF",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(onClick = onRecenterBrasilia) {
                                Text("Recentrar no Plano Piloto")
                            }
                        }
                    }
                }
            )

            // Canvas Overlay for Center Pin & Selected Lot Polygon
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasW = size.width
                val canvasH = size.height

                // If a lot is selected and has rings, draw the polygon highlight!
                val selected = uiState.selectedLot
                if (selected != null && selected.rings.isNotEmpty()) {
                    for (ring in selected.rings) {
                        if (ring.size >= 3) {
                            val path = Path()
                            var first = true
                            for (pt in ring) {
                                val lon = pt.first
                                val lat = pt.second
                                val x = ((lon - minLon) / (maxLon - minLon) * canvasW).toFloat()
                                val y = ((maxLat - lat) / (maxLat - minLat) * canvasH).toFloat()
                                if (first) {
                                    path.moveTo(x, y)
                                    first = false
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            path.close()
                            drawPath(
                                path = path,
                                color = CadastreYellow.copy(alpha = 0.35f)
                            )
                            drawPath(
                                path = path,
                                color = CadastreYellow,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }

                // If a KMDF record is selected, draw a highway marker
                val kmRec = uiState.selectedKmdfRecord
                if (kmRec != null) {
                    val kx = ((kmRec.longitude - minLon) / (maxLon - minLon) * canvasW).toFloat()
                    val ky = ((maxLat - kmRec.latitude) / (maxLat - minLat) * canvasH).toFloat()
                    if (kx in -50f..(canvasW + 50f) && ky in -50f..(canvasH + 50f)) {
                        drawCircle(color = Color(0xFF023E8A), radius = 26f, center = Offset(kx, ky))
                        drawCircle(color = Color.White, radius = 20f, center = Offset(kx, ky))
                        drawCircle(color = Color(0xFF00B4D8), radius = 12f, center = Offset(kx, ky))
                    }
                }

                // Crosshair at center
                val centerX = canvasW / 2f
                val centerY = canvasH / 2f

                // Target circle
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f),
                    radius = 18f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 4f)
                )
                drawCircle(
                    color = Color(0xFF00687A),
                    radius = 8f,
                    center = Offset(centerX, centerY)
                )
            }
        }

        // Top HUD Bar: Coordinates, Engine & Layer Switcher
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 12.dp, end = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.NearMe,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", uiState.mapCenterLat, uiState.mapCenterLon),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Toque no mapa para identificar o lote",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Engine Toggle
                        AssistChip(
                            onClick = onToggleMapEngine,
                            label = {
                                Text(
                                    text = if (uiState.mapEngine == MapEngine.MAP_SERVER) "MapServer" else "WMS 1.1",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            modifier = Modifier.testTag("toggle_engine_button")
                        )

                        // Layers menu button
                        IconButton(
                            onClick = { showLayersMenu = !showLayersMenu },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("layers_button")
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Camadas",
                                tint = if (showLayersMenu) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Expandable Layer Chips
                if (showLayersMenu) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Camadas Visíveis:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = uiState.visibleLayers.contains("9"),
                            onClick = { onToggleLayer("9") },
                            label = { Text("Lotes", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = uiState.visibleLayers.contains("12"),
                            onClick = { onToggleLayer("12") },
                            label = { Text("Quadras", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = uiState.visibleLayers.contains("13"),
                            onClick = { onToggleLayer("13") },
                            label = { Text("Setores", style = MaterialTheme.typography.labelSmall) }
                        )
                        FilterChip(
                            selected = uiState.visibleLayers.contains("5"),
                            onClick = { onToggleLayer("5") },
                            label = { Text("Edificações", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }

        // Map Pan Controls (D-Pad style buttons) on Left Edge
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledTonalIconButton(
                onClick = { onPan(0.3, 0.0) }, // Pan North
                modifier = Modifier.size(36.dp)
            ) {
                Text("▲", style = MaterialTheme.typography.labelMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalIconButton(
                    onClick = { onPan(0.0, -0.3) }, // Pan West
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("◀", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalIconButton(
                    onClick = { onPan(0.0, 0.3) }, // Pan East
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("▶", style = MaterialTheme.typography.labelMedium)
                }
            }
            FilledTonalIconButton(
                onClick = { onPan(-0.3, 0.0) }, // Pan South
                modifier = Modifier.size(36.dp)
            ) {
                Text("▼", style = MaterialTheme.typography.labelMedium)
            }
        }

        // Floating Action Buttons on Right Edge: Zoom and Recenter
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SmallFloatingActionButton(
                onClick = onZoomIn,
                modifier = Modifier.testTag("zoom_in_button"),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Add, contentDescription = "Aproximar")
            }

            SmallFloatingActionButton(
                onClick = onZoomOut,
                modifier = Modifier.testTag("zoom_out_button"),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Afastar")
            }

            FloatingActionButton(
                onClick = onRecenterBrasilia,
                modifier = Modifier.testTag("recenter_button"),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Plano Piloto")
            }
        }

        // Bottom Selected / Identified Lot Card
        val lot = uiState.selectedLot
        if (lot != null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("map_selected_lot_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (lot.ra.isNotBlank() && lot.ra != "Null") {
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
                            if (lot.cipu.isNotBlank() && lot.cipu != "Null") {
                                Text(
                                    text = "CIPU: ${lot.cipu}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
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
                            text = "${lot.formattedArea} • ${lot.formattedDecimalCoords}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    FilledTonalButton(
                        onClick = { onSelectLot(lot) },
                        modifier = Modifier.testTag("open_dossier_from_map_button")
                    ) {
                        Text("Ficha")
                    }
                }
            }
        }

        // Bottom Selected KMDF Record Card
        val kmRec = uiState.selectedKmdfRecord
        if (kmRec != null && uiState.selectedLot == null) {
            ElevatedCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("map_selected_kmdf_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = Color(0xFF023E8A),
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

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = { onMapTapped(kmRec.latitude, kmRec.longitude) },
                        modifier = Modifier.testTag("reverse_geocode_km_button")
                    ) {
                        Text("Ver Lotes")
                    }
                }
            }
        }
    }
}
