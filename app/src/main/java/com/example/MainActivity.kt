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
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Layers
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.screens.DigitalCartographyScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.GoogleMapsScreen
import com.example.ui.screens.KmdfTableScreen
import com.example.ui.screens.LotDetailSheet
import com.example.ui.screens.ReverseGeocodeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.CadastreYellow
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GeocodingViewModel
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.MapsInitializer

class MainActivity : ComponentActivity() {
    private val viewModel: GeocodingViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Maps SDK renderer safely
        val playServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        if (playServicesAvailable == ConnectionResult.SUCCESS) {
            try {
                MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LATEST) { }
            } catch (e: Exception) {
                try {
                    MapsInitializer.initialize(applicationContext, MapsInitializer.Renderer.LEGACY) { }
                } catch (_: Exception) { }
            }
        }

        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsState()
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val focusManager = LocalFocusManager.current

                // Clear any floating action mode / text selection when tab or modal state changes
                LaunchedEffect(uiState.currentTab, uiState.selectedLot) {
                    focusManager.clearFocus(force = true)
                }

                // BackHandler to close sheet or return to Search tab
                BackHandler(enabled = uiState.selectedLot != null || uiState.currentTab != 0) {
                    focusManager.clearFocus(force = true)
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
                                        0 -> "GeoDF • Residências Identificadas"
                                        1 -> "Cartografia Digital • DF"
                                        2 -> "Google Maps • Distrito Federal"
                                        3 -> "Tabela KM-DF (KMZ)"
                                        4 -> "Geocodificação Reversa"
                                        5 -> "Imóveis Salvos"
                                        else -> "GeoDF"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        if (uiState.currentTab == 5) viewModel.setTab(0) else viewModel.setTab(5)
                                    },
                                    modifier = Modifier.testTag("top_action_favorites")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 5) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Imóveis Salvos",
                                        tint = if (uiState.currentTab == 5 || uiState.favorites.isNotEmpty()) CadastreYellow else MaterialTheme.colorScheme.onSurfaceVariant
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
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.setTab(0)
                                },
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
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.setTab(1)
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 1) Icons.Filled.Layers else Icons.Outlined.Layers,
                                        contentDescription = "Cartografia"
                                    )
                                },
                                label = { Text("Cartografia") },
                                modifier = Modifier.testTag("nav_tab_cartography")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 2,
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.setTab(2)
                                },
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
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.setTab(3)
                                },
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
                                onClick = {
                                    focusManager.clearFocus(force = true)
                                    viewModel.setTab(4)
                                },
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
                                    viewModel.setTab(1) // Open Cartografia Digital!
                                },
                                onToggleFavorite = viewModel::toggleFavorite
                            )

                            1 -> DigitalCartographyScreen(
                                uiState = uiState,
                                onSelectLot = viewModel::selectLot,
                                onMapCoordinateTapped = viewModel::onMapTappedCoordinate,
                                onSelectKmdfRecord = viewModel::selectKmdfRecord
                            )

                            2 -> GoogleMapsScreen(
                                uiState = uiState,
                                onSelectLot = viewModel::selectLot,
                                onSelectKmdfRecord = viewModel::selectKmdfRecord,
                                onMapClickCoordinate = viewModel::onMapTappedCoordinate,
                                onReverseGeocodeKm = viewModel::reverseGeocodeKmdfRecord,
                                onClearSelectedLot = viewModel::clearSelectedLot,
                                onClearSelectedKmdfRecord = viewModel::clearSelectedKmdfRecord,
                                onSearchQuery = viewModel::performSearch
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
                                    viewModel.setTab(1) // Open in Digital Cartography
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
                                    viewModel.setTab(1)
                                },
                                onToggleFavorite = viewModel::toggleFavorite
                            )

                            5 -> FavoritesScreen(
                                uiState = uiState,
                                onSelectLot = viewModel::selectLot,
                                onViewOnMap = { lot ->
                                    viewModel.selectLot(lot)
                                    viewModel.setTab(1)
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
                                viewModel.setTab(1) // View in Digital Cartography!
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
