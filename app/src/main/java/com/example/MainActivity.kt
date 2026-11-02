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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.LotDetailSheet
import com.example.ui.screens.MapScreen
import com.example.ui.screens.ReverseGeocodeScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.WmsInfoScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GeocodingViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: GeocodingViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                        1 -> "Mapa WMS • Cadastro DF"
                                        2 -> "Geocodificação Reversa"
                                        3 -> "OGC WMS • IDE DF"
                                        4 -> "Lotes Salvos"
                                        else -> "GeoDF"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
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
                                        imageVector = if (uiState.currentTab == 1) Icons.Filled.Map else Icons.Outlined.Map,
                                        contentDescription = "Mapa WMS"
                                    )
                                },
                                label = { Text("Mapa WMS") },
                                modifier = Modifier.testTag("nav_tab_map")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 2,
                                onClick = { viewModel.setTab(2) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 2) Icons.Filled.PinDrop else Icons.Outlined.PinDrop,
                                        contentDescription = "Reversa"
                                    )
                                },
                                label = { Text("Reversa") },
                                modifier = Modifier.testTag("nav_tab_reverse")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 3,
                                onClick = {
                                    viewModel.setTab(3)
                                    viewModel.loadWmsCapabilities()
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 3) Icons.Filled.Language else Icons.Outlined.Language,
                                        contentDescription = "WMS Info"
                                    )
                                },
                                label = { Text("WMS Info") },
                                modifier = Modifier.testTag("nav_tab_wms")
                            )

                            NavigationBarItem(
                                selected = uiState.currentTab == 4,
                                onClick = { viewModel.setTab(4) },
                                icon = {
                                    Icon(
                                        imageVector = if (uiState.currentTab == 4) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                        contentDescription = "Salvos"
                                    )
                                },
                                label = { Text("Salvos") },
                                modifier = Modifier.testTag("nav_tab_favorites")
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
                                    viewModel.setTab(1)
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

                            2 -> ReverseGeocodeScreen(
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

                            3 -> WmsInfoScreen(
                                uiState = uiState,
                                onRefreshCapabilities = viewModel::loadWmsCapabilities
                            )

                            4 -> FavoritesScreen(
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
                                viewModel.setTab(1)
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
