package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.GoogleMapsScreen
import com.example.ui.screens.KmdfTableScreen
import com.example.ui.screens.LotDetailSheet
import com.example.ui.screens.MapScreen
import com.example.ui.screens.ReverseGeocodeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.WmsInfoScreen
import com.example.ui.theme.CadastreYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GeocodingViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GeocodingViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        coil.Coil.setImageLoader(com.example.data.network.NetworkClient.createImageLoader(applicationContext))
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                // BackHandler to close sheet or return to Search tab
                BackHandler(enabled = uiState.selectedLot != null || uiState.currentTab != 0) {
                    if (uiState.selectedLot != null) {
                        viewModel.clearSelectedLot()
                    } else if (uiState.currentTab != 0) {
                        viewModel.setTab(0)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = when (uiState.currentTab) {
                                        0 -> "GeoDF • Geocodificação"
                                        1 -> "Mapa WMS • IDE DF"
                                        2 -> "Google Maps SDK"
                                        3 -> "Tabela KM-DF (KMZ)"
                                        4 -> "Geocodificação Reversa"
                                        5 -> "Metadados OGC WMS"
                                        6 -> "Lotes Salvos"
                                        else -> "GeoDF"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (uiState.currentTab == 5) {
                                            viewModel.setTab(0)
                                        } else {
                                            viewModel.setTab(5)
                                            viewModel.loadWmsCapabilities()
                                        }
                                    },
                                    modifier = Modifier.testTag("top_action_wms_info")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 5) Icons.Filled.Info else Icons.Outlined.Info,
                                        contentDescription = "Metadados WMS",
                                        tint = if (uiState.currentTab == 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (uiState.currentTab == 6) viewModel.setTab(0) else viewModel.setTab(6)
                                    },
                                    modifier = Modifier.testTag("top_action_favorites")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 6) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Lotes Salvos",
                                        tint = if (uiState.currentTab == 6 || uiState.favorites.isNotEmpty()) CadastreYellow else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .testTag("main_bottom_nav")
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            NavigationBarItem(
                                selected = uiState.currentTab == 0,
                                onClick = { viewModel.setTab(0) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 0) Icons.Filled.Search else Icons.Outlined.Search,
                                        contentDescription = "Busca"
                                    )
                                },
                                label = { Text("Busca") },
                                modifier = Modifier.testTag("nav_tab_search")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 1,
                                onClick = { viewModel.setTab(1) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 1) Icons.Filled.Public else Icons.Outlined.Public,
                                        contentDescription = "WMS DF"
                                    )
                                },
                                label = { Text("WMS DF") },
                                modifier = Modifier.testTag("nav_tab_wms_map")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 2,
                                onClick = { viewModel.setTab(2) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 2) Icons.Filled.Map else Icons.Outlined.Map,
                                        contentDescription = "Google Maps"
                                    )
                                },
                                label = { Text("Google Maps") },
                                modifier = Modifier.testTag("nav_tab_google_maps")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 3,
                                onClick = { viewModel.setTab(3) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 3) Icons.Filled.TableChart else Icons.Outlined.TableChart,
                                        contentDescription = "KM-DF"
                                    )
                                },
                                label = { Text("KM-DF") },
                                modifier = Modifier.testTag("nav_tab_kmdf")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 4,
                                onClick = { viewModel.setTab(4) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 4) Icons.Filled.PinDrop else Icons.Outlined.PinDrop,
                                        contentDescription = "Reversa"
                                    )
                                },
                                label = { Text("Reversa") },
                                modifier = Modifier.testTag("nav_tab_reverse")
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (uiState.currentTab) {
                            0 -> SearchScreen(
                                uiState = uiState,
                                raList = viewModel.administrativeRegions,
                                onQueryChanged = viewModel::onSearchQueryChanged,
                                onSearch = { q -> viewModel.performSearch(q) },
                                onRaSelected = viewModel::onRaFilterSelected,
                                onSelectLot = viewModel::selectLot,
                                onViewOnMap = { lot ->
                                    viewModel.selectLot(lot)
                                    viewModel.setTab(2) // Jump to Google Maps to view lot!
                                },
                                onToggleFavorite = viewModel::toggleFavorite
                            )

                            1 -> MapScreen(
                                uiState = uiState,
                                onZoomIn = viewModel::zoomIn,
                                onZoomOut = viewModel::zoomOut,
                                onRecenterBrasilia = viewModel::recenterOnBrasilia,
                                onToggleMapEngine = viewModel::toggleMapEngine,
                                onToggleLayer = viewModel::toggleLayer,
                                onMapTapped = viewModel::onMapTappedCoordinate,
                                onSelectLot = viewModel::selectLot,
                                onPan = viewModel::pan
                            )

                            2 -> GoogleMapsScreen(
                                uiState = uiState,
                                onSelectLot = viewModel::selectLot,
                                onSelectKmdfRecord = viewModel::selectKmdfRecord,
                                onMapClickCoordinate = viewModel::onMapTappedCoordinate,
                                onReverseGeocodeKm = viewModel::reverseGeocodeKmdfRecord
                            )

                            3 -> KmdfTableScreen(
                                uiState = uiState,
                                onQueryChanged = viewModel::onKmdfSearchQueryChanged,
                                onRodoviaSelected = viewModel::onRodoviaFilterSelected,
                                onCustomUrlChanged = viewModel::onCustomKmzUrlChanged,
                                onDownloadKmz = viewModel::loadKmzFromUrl,
                                onLoadFileUri = viewModel::loadKmzFromUri,
                                onViewOnMap = { record ->
                                    viewModel.viewKmdfOnMap(record)
                                    viewModel.setTab(2) // Open Google Maps directly with KMDF marker!
                                },
                                onReverseGeocodeKm = viewModel::reverseGeocodeKmdfRecord
                            )

                            4 -> ReverseGeocodeScreen(
                                uiState = uiState,
                                onLatChanged = viewModel::onReverseLatChanged,
                                onLonChanged = viewModel::onReverseLonChanged,
                                onReverseGeocode = { lat, lon -> viewModel.performReverseGeocode(lat, lon) },
                                onSelectLot = viewModel::selectLot,
                                onViewOnMap = { lot ->
                                    viewModel.selectLot(lot)
                                    viewModel.setTab(2)
                                },
                                onToggleFavorite = viewModel::toggleFavorite
                            )

                            5 -> WmsInfoScreen(
                                uiState = uiState,
                                onRefreshCapabilities = viewModel::loadWmsCapabilities
                            )

                            6 -> FavoritesScreen(
                                uiState = uiState,
                                onSelectLot = viewModel::selectLot,
                                onViewOnMap = { lot ->
                                    viewModel.selectLot(lot)
                                    viewModel.setTab(2)
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onSearchSuggestion = { query ->
                                    viewModel.onSearchQueryChanged(query)
                                    viewModel.performSearch(query)
                                    viewModel.setTab(0)
                                }
                            )
                        }
                    }

                    // Lot Detail Sheet modal
                    uiState.selectedLot?.let { lot ->
                        LotDetailSheet(
                            lot = lot,
                            isFavorite = viewModel.isFavorite(lot),
                            onDismiss = viewModel::clearSelectedLot,
                            onViewOnMap = {
                                viewModel.setTab(2) // View on Google Maps!
                                viewModel.clearSelectedLot()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(lot) },
                            sheetState = sheetState
                        )
                    }
                }
            }
        }
    }
}
