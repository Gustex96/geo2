package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.CadastreLot
import com.example.ui.components.LotCard
import com.example.ui.viewmodel.GeocodingUiState
import java.util.Locale

@Composable
fun ReverseGeocodeScreen(
    uiState: GeocodingUiState,
    onLatChanged: (String) -> Unit,
    onLonChanged: (String) -> Unit,
    onReverseGeocode: (String, String) -> Unit,
    onSelectLot: (CadastreLot) -> Unit,
    onViewOnMap: (CadastreLot) -> Unit,
    onToggleFavorite: (CadastreLot) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchDeviceLocation(context) { lat, lon ->
                onLatChanged(String.format(Locale.US, "%.6f", lat))
                onLonChanged(String.format(Locale.US, "%.6f", lon))
                onReverseGeocode(lat.toString(), lon.toString())
            }
        } else {
            Toast.makeText(context, "Permissão de localização necessária para obter sua posição GPS", Toast.LENGTH_SHORT).show()
        }
    }

    val presets = listOf(
        "Congresso Nacional" to Pair(-15.7997, -47.8645),
        "SQN 205 (Asa Norte)" to Pair(-15.77165, -47.87985),
        "Torre de TV" to Pair(-15.7905, -47.8928),
        "Memorial JK" to Pair(-15.7838, -47.9130),
        "Praça do Relógio (Taguatinga)" to Pair(-15.8336, -48.0566),
        "Águas Claras Park" to Pair(-15.8389, -48.0261)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PinDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Geocodificação Reversa por Coordenadas",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Informe as coordenadas geográficas (WGS84) ou use seu GPS para descobrir o lote territorial, quadra e endereço cartorial no DF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.reverseLatInput,
                            onValueChange = onLatChanged,
                            label = { Text("Latitude") },
                            placeholder = { Text("-15.77165") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lat_input_field"),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = uiState.reverseLonInput,
                            onValueChange = onLonChanged,
                            label = { Text("Longitude") },
                            placeholder = { Text("-47.87985") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("lon_input_field"),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                onReverseGeocode(uiState.reverseLatInput, uiState.reverseLonInput)
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("submit_reverse_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Identificar Lote")
                        }

                        FilledTonalButton(
                            onClick = {
                                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                if (hasFine || hasCoarse) {
                                    fetchDeviceLocation(context) { lat, lon ->
                                        onLatChanged(String.format(Locale.US, "%.6f", lat))
                                        onLonChanged(String.format(Locale.US, "%.6f", lon))
                                        onReverseGeocode(lat.toString(), lon.toString())
                                    }
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gps_location_button")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Meu GPS")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Pontos Notáveis no Distrito Federal:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { (name, coords) ->
                            SuggestionChip(
                                onClick = {
                                    val latStr = coords.first.toString()
                                    val lonStr = coords.second.toString()
                                    onLatChanged(latStr)
                                    onLonChanged(lonStr)
                                    onReverseGeocode(latStr, lonStr)
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }
        }

        if (uiState.isReverseGeocoding) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Identificando parcelas cadastrais no Geoportal IDE DF...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        if (uiState.reverseGeocodeError != null && !uiState.isReverseGeocoding) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = uiState.reverseGeocodeError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        if (uiState.reverseGeocodeResults.isNotEmpty() && !uiState.isReverseGeocoding) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lotes encontrados nestas coordenadas:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${uiState.reverseGeocodeResults.size} lotes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            items(
                items = uiState.reverseGeocodeResults,
                key = { it.objectId }
            ) { lot ->
                val isFav = uiState.favorites.any { it.objectId == lot.objectId }
                LotCard(
                    lot = lot,
                    isFavorite = isFav,
                    onSelect = { onSelectLot(lot) },
                    onViewOnMap = { onViewOnMap(lot) },
                    onToggleFavorite = { onToggleFavorite(lot) }
                )
            }
        }
    }
}

private fun fetchDeviceLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val best = lastGps ?: lastNetwork
        if (best != null) {
            onLocation(best.latitude, best.longitude)
        } else {
            // If emulator / no last location, provide default DF coordinate
            Toast.makeText(context, "Sinal GPS indisponível no momento. Usando coordenadas centrais do DF.", Toast.LENGTH_SHORT).show()
            onLocation(-15.7942, -47.8822)
        }
    } catch (e: SecurityException) {
        Toast.makeText(context, "Permissão negada", Toast.LENGTH_SHORT).show()
    }
}
