package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.KmdfRecord
import com.example.ui.theme.CadastreBlue
import com.example.ui.theme.CadastreYellow
import com.example.ui.viewmodel.GeocodingUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KmdfTableScreen(
    uiState: GeocodingUiState,
    onQueryChanged: (String) -> Unit,
    onRodoviaSelected: (String) -> Unit,
    onCustomUrlChanged: (String) -> Unit,
    onDownloadKmz: (String) -> Unit,
    onLoadFileUri: (Context, Uri) -> Unit,
    onViewOnMap: (KmdfRecord) -> Unit,
    onReverseGeocodeKm: (KmdfRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showImporter by remember { mutableStateOf(false) }
    var selectedRecordForDialog by remember { mutableStateOf<KmdfRecord?>(null) }
    var viewModeTab by remember { mutableIntStateOf(0) } // 0: Cartões, 1: Tabela

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onLoadFileUri(context, uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Header with Import/KMZ toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Tabela KM-DF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiState.filteredKmdfRecords.size} de ${uiState.kmdfRecords.size} marcos quilométricos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            FilledTonalButton(
                onClick = { showImporter = !showImporter },
                modifier = Modifier.testTag("toggle_kmz_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showImporter) "Ocultar KMZ" else "Importar KMZ")
            }
        }

        // Expandable KMZ Import Section
        AnimatedVisibility(visible = showImporter) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AddLink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Importar Dados KMZ / KML",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uiState.customKmzUrlInput,
                        onValueChange = onCustomUrlChanged,
                        label = { Text("Link direto KMZ (.kmz ou .kml)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kmz_url_input"),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onDownloadKmz(uiState.customKmzUrlInput) },
                            enabled = !uiState.isLoadingKmz,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("download_kmz_button")
                        ) {
                            if (uiState.isLoadingKmz) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Baixar Link")
                            }
                        }

                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pick_local_kmz_button")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Abrir Arquivo")
                        }
                    }

                    if (uiState.kmzError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.kmzError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (uiState.kmzSuccessMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.kmzSuccessMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Input
        OutlinedTextField(
            value = uiState.kmdfSearchQuery,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kmdf_search_field"),
            placeholder = { Text("Filtrar por rodovia (ex: DF-001), KM, trecho ou RA...") },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.kmdfSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rodovia Filter Chips
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
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            uiState.availableRodovias.forEach { rodovia ->
                val isSelected = uiState.selectedRodoviaFilter == rodovia
                FilterChip(
                    selected = isSelected,
                    onClick = { onRodoviaSelected(rodovia) },
                    label = { Text(rodovia, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("chip_rodovia_$rodovia")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // View Mode Toggle (Cards vs Table)
        TabRow(
            selectedTabIndex = viewModeTab,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Tab(
                selected = viewModeTab == 0,
                onClick = { viewModeTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ViewAgenda, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cartões", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
            Tab(
                selected = viewModeTab == 1,
                onClick = { viewModeTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tabela", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        if (uiState.filteredKmdfRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Nenhum marco quilométrico encontrado para o filtro atual",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else if (viewModeTab == 0) {
            // Card List View
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("kmdf_records_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = uiState.filteredKmdfRecords,
                    key = { it.id }
                ) { record ->
                    KmdfCard(
                        record = record,
                        onViewOnMap = { onViewOnMap(record) },
                        onReverseGeocode = { onReverseGeocodeKm(record) },
                        onCopyCoords = {
                            val coords = "${record.latitude}, ${record.longitude}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas KM", coords))
                            Toast.makeText(context, "Coordenadas copiadas: $coords", Toast.LENGTH_SHORT).show()
                        },
                        onOpenDetails = { selectedRecordForDialog = record }
                    )
                }
            }
        } else {
            // Table Grid View
            KmdfTableView(
                records = uiState.filteredKmdfRecords,
                onRowClick = { selectedRecordForDialog = it },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }

    // Detail Dialog when a record is clicked
    selectedRecordForDialog?.let { record ->
        AlertDialog(
            onDismissRequest = { selectedRecordForDialog = null },
            title = {
                Text(
                    text = "${record.rodovia} • ${record.kmDisplay}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Trecho: ${record.trecho}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (record.ra.isNotBlank()) {
                        Text(
                            text = "Região Administrativa: ${record.ra}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Coordenadas WGS84: ${record.formattedDecimalCoords}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Formato DMS: ${record.formattedDms}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (record.attributes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Atributos KMZ:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        record.attributes.forEach { (k, v) ->
                            Text(
                                text = "$k: $v",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val rec = selectedRecordForDialog
                        selectedRecordForDialog = null
                        rec?.let { onViewOnMap(it) }
                    }
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver no Mapa WMS")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRecordForDialog = null }) {
                    Text("Fechar")
                }
            }
        )
    }
}

@Composable
private fun KmdfCard(
    record: KmdfRecord,
    onViewOnMap: () -> Unit,
    onReverseGeocode: () -> Unit,
    onCopyCoords: () -> Unit,
    onOpenDetails: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("kmdf_card_${record.id}")
            .clickable(onClick = onOpenDetails),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Highway Badge
                    Surface(
                        color = CadastreBlue,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.rodovia,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // KM Badge
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = record.kmDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (record.ra.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(record.ra, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = record.trecho,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "WGS84: ${record.formattedDecimalCoords}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyCoords,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", style = MaterialTheme.typography.labelSmall)
                }

                FilledTonalButton(
                    onClick = onViewOnMap,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mapa WMS", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = onReverseGeocode,
                    modifier = Modifier.weight(1.2f)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lotes", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun KmdfTableView(
    records: List<KmdfRecord>,
    onRowClick: (KmdfRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Via",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(65.dp)
                )
                Text(
                    text = "KM",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(60.dp)
                )
                Text(
                    text = "Trecho / Localidade",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "RA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.width(80.dp)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Table Rows
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = records,
                    key = { it.id }
                ) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRowClick(record) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = record.rodovia,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(65.dp)
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", record.km),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(60.dp)
                        )
                        Text(
                            text = record.trecho,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = record.ra,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(80.dp)
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}
