package com.helga.android.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Scaffold
import androidx.wear.compose.material3.TimeTextMode
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.google.android.horologist.compose.layout.PositionIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.helga.android.data.local.entity.ShoppingItemEntity

@Composable
fun ShoppingListWearScreen(
    viewModel: ShoppingListViewModel = hiltViewModel()
) {
    val itemsByAisle by viewModel.itemsByAisle.collectAsState()
    val activeListId by viewModel.activeListId.collectAsState()

    val scalingState = rememberScalingLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            timeTextMode = TimeTextMode.Inside,
            positionIndicator = {
                PositionIndicator(scalingLazyListState = scalingState)
            },
            modifier = Modifier.background(MaterialTheme.colorScheme.background)
        ) {
            ScalingLazyColumn(
                state = scalingState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.Start
            ) {
                item {
                    Text(
                        text = if (activeListId != null) "Einkaufsliste" else "Keine Liste",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                        fontSize = 18.sp
                    )
                }

                if (itemsByAisle.isEmpty()) {
                    item {
                        Text(
                            text = "Keine Items",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(vertical = 16.dp),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    itemsByAisle.forEach { (aisle, items) ->
                        item {
                            Text(
                                text = aisle.ifBlank { "Sonstiges" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 10.sp
                            )
                        }

                        items(items.size) { index ->
                            val item = items[index]
                            WearShoppingItemRow(
                                item = item,
                                onToggle = { viewModel.toggleChecked(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WearShoppingItemRow(
    item: ShoppingItemEntity,
    onToggle: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked == 1,
            onCheckedChange = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = item.name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            fontSize = 12.sp
        )

        if (item.quantity > 0) {
            val qDisplay = if (item.quantity % 1.0 == 0.0)
                item.quantity.toInt().toString()
            else
                item.quantity.toString()
            Text(
                text = "$qDisplay${item.unit}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@WearPreviewDevices
@Composable
fun ShoppingListWearScreenPreview() {
    ShoppingListWearScreen()
}
