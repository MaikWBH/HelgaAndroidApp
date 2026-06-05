package com.helga.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.helga.android.data.local.entity.OffProductEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailSheet(
    product: OffProductEntity,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (product.brand.isNotBlank()) {
                            Text(
                                text = product.brand,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                }
                HorizontalDivider()
            }

            // Nutri-Score
            item {
                QualityScoreItem(
                    icon = "⭐",
                    label = "Nutri-Score",
                    value = product.nutriScore.uppercase().ifBlank { "Keine Daten" },
                    color = when (product.nutriScore.lowercase()) {
                        "a" -> Color(0xFF22863A)
                        "b" -> Color(0xFF28A745)
                        "c" -> Color(0xFFFFC107)
                        "d" -> Color(0xFFFF9800)
                        else -> Color(0xFFD32F2F)
                    },
                )
            }

            // Eco-Score
            if (product.ecoScore.isNotBlank()) {
                item {
                    QualityScoreItem(
                        icon = "🌍",
                        label = "Eco-Score",
                        value = product.ecoScore.uppercase(),
                        color = when (product.ecoScore.lowercase()) {
                            "a" -> Color(0xFF22863A)
                            "b" -> Color(0xFF28A745)
                            "c" -> Color(0xFFFFC107)
                            "d" -> Color(0xFFFF9800)
                            else -> Color(0xFFD32F2F)
                        },
                    )
                }
            }

            // NOVA
            if (product.nova > 0) {
                item {
                    QualityScoreItem(
                        icon = "📦",
                        label = "NOVA",
                        value = "${product.nova}/4",
                        subtitle = when (product.nova) {
                            1 -> "Unverarbeitet"
                            2 -> "Verarbeitete Zutaten"
                            3 -> "Verarbeitete Lebensmittel"
                            4 -> "Ultra-verarbeitet"
                            else -> ""
                        },
                        color = when (product.nova) {
                            1 -> Color(0xFF22863A)
                            2 -> Color(0xFF28A745)
                            3 -> Color(0xFFFF9800)
                            else -> Color(0xFFD32F2F)
                        },
                    )
                }
            }

            // Attribute
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Eigenschaften",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (product.isOrganic == 1) {
                            AttributeBadge("🌿 Bio")
                        }
                        if (product.vegan == 1) {
                            AttributeBadge("🌱 Vegan")
                        }
                        if (product.vegetarian == 1) {
                            AttributeBadge("🥬 Vegetarisch")
                        }
                    }
                }
            }

            // Allergenes
            if (product.allergenes.isNotBlank() && product.allergenes != "[]") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(12.dp),
                    ) {
                        Text(
                            text = "⚠️ Allergene",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = product.allergenes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            item {
                // Spacer for scroll
                Column(modifier = Modifier.padding(bottom = 32.dp)) {}
            }
        }
    }
}

@Composable
private fun QualityScoreItem(
    icon: String,
    label: String,
    value: String,
    subtitle: String = "",
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun AttributeBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
