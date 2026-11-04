package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CadastreLot
import com.example.ui.theme.CadastreYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotDetailSheet(
    lot: CadastreLot,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onViewOnMap: () -> Unit,
    onToggleFavorite: () -> Unit,
    sheetState: SheetState
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with RA and Close/Fav buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (lot.ra.isNotBlank() && lot.ra != "Null") {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    lot.ra.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }

                    if (lot.situacao.isNotBlank() && lot.situacao != "Null") {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(lot.situacao, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("detail_fav_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (isFavorite) "Remover favorito" else "Favoritar",
                            tint = if (isFavorite) CadastreYellow else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Address Title
            Text(
                text = lot.displayAddress,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (lot.endCartorial.isNotBlank() && lot.endCartorial != "Null") {
                Text(
                    text = "Endereço Cartorial: ${lot.endCartorial}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Actions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        val coords = "${lot.latitude}, ${lot.longitude}"
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Coordenadas", coords))
                        Toast.makeText(context, "Coordenadas copiadas: $coords", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", style = MaterialTheme.typography.labelMedium)
                }

                Button(
                    onClick = onViewOnMap,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("No Mapa", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:${lot.latitude},${lot.longitude}?q=${lot.latitude},${lot.longitude}(${Uri.encode(lot.displayAddress)})")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        try {
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Nenhum app de mapa instalado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rota", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Catalog Card: ct_CIU, Quadra, Casa/Lote, CEP
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Código de Identificação da Unidade:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        FilledTonalButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("ct_CIU", lot.ciu))
                                Toast.makeText(context, "ct_CIU copiado: ${lot.ciu}", Toast.LENGTH_SHORT).show()
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar CIU", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = lot.displayCiu,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Quadra / Setor:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(lot.quadraDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Casa / Lote:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(lot.numeroCasaDisplay, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CEP:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(lot.formattedCep, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Região Administrativa:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(lot.ra, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 1: Coordenadas & Localização
            DossierSection(title = "Localização & Coordenadas Geográficas") {
                DossierItem(label = "Graus Decimais (WGS84)", value = lot.formattedDecimalCoords)
                DossierItem(label = "Formato DMS", value = lot.formattedDms)
                DossierItem(label = "Região Administrativa (RA)", value = lot.ra.ifBlank { "Não informada" })
                DossierItem(label = "CEP", value = lot.formattedCep.ifBlank { "Não informado" })
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2: Cadastro Territorial
            DossierSection(title = "Identificação Cadastral do Lote") {
                DossierItem(label = "Código CIPU", value = lot.cipu.ifBlank { "N/A" })
                DossierItem(label = "Código CIU", value = lot.ciu.ifBlank { "N/A" })
                DossierItem(label = "Setor", value = lot.setor.ifBlank { "N/A" })
                DossierItem(label = "Quadra", value = lot.quadra.ifBlank { "N/A" })
                DossierItem(label = "Conjunto", value = if (lot.conjunto != "Null") lot.conjunto else "N/A")
                DossierItem(label = "Lote", value = lot.lote.ifBlank { "N/A" })
                DossierItem(label = "Área de Projeto", value = lot.formattedArea)
                DossierItem(label = "Projeto Aprovado", value = lot.projeto.ifBlank { "N/A" })
                DossierItem(label = "Planta Georreferenciada", value = lot.planta.ifBlank { "N/A" })
                DossierItem(label = "Data de Registro", value = if (lot.dataRegistro != "Null") lot.dataRegistro else "N/A")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 3: Normas & Parâmetros Urbanísticos
            DossierSection(title = "Legislação & Parâmetros Urbanísticos") {
                DossierItem(label = "Norma Vigente (PPCUB)", value = lot.normaVigente.ifBlank { "N/A" })
                DossierItem(label = "Norma Anterior", value = lot.normaAnterior.ifBlank { "N/A" })
                DossierItem(label = "Código do Parâmetro", value = lot.codigoParametro.ifBlank { "N/A" })
                DossierItem(label = "UOS Parâmetro / Cores", value = "${lot.uosParametro} ${lot.uosCores}".trim().ifBlank { "N/A" })
                DossierItem(label = "ONALT", value = if (lot.onalt != "Null") lot.onalt else "N/A")
                DossierItem(label = "Norma ONALT", value = if (lot.onaltNorma != "Null") lot.onaltNorma else "N/A")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Share button
            OutlinedButton(
                onClick = {
                    val shareText = buildString {
                        appendLine("📍 FICHA CADASTRAL - GEODF")
                        appendLine("Endereço: ${lot.displayAddress}")
                        if (lot.endCartorial.isNotBlank()) appendLine("Cartorial: ${lot.endCartorial}")
                        appendLine("RA: ${lot.ra} | CEP: ${lot.formattedCep}")
                        appendLine("CIPU: ${lot.cipu} | CIU: ${lot.ciu}")
                        appendLine("Área: ${lot.formattedArea}")
                        appendLine("Norma Vigente: ${lot.normaVigente}")
                        appendLine("Coordenadas: ${lot.formattedDecimalCoords} (${lot.formattedDms})")
                        appendLine("Fonte: Geoportal IDE DF (Cadastro Territorial)")
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Compartilhar Ficha Cadastral"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compartilhar Dados Cadastrais")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DossierSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun DossierItem(label: String, value: String) {
    if (value.isBlank() || value == "Null") return

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    }
}
