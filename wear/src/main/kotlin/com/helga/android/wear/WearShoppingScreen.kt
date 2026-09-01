package com.helga.android.wear

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

@Composable
fun WearShoppingScreen(
    state: WearShoppingListState,
    onToggle: (itemId: String) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val itemsByAisle = state.items.groupBy { it.aisle }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Text(
                    text = state.listName.ifBlank { stringResource(R.string.wear_no_list) },
                    style = MaterialTheme.typography.title3,
                )
            }

            if (!state.connected) {
                item {
                    Text(
                        text = stringResource(R.string.wear_no_phone),
                        style = MaterialTheme.typography.caption1,
                    )
                }
            } else if (state.items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.wear_no_items),
                        style = MaterialTheme.typography.caption1,
                    )
                }
            } else {
                itemsByAisle.forEach { (aisle, items) ->
                    item {
                        Text(
                            text = aisle.ifBlank { "Sonstiges" },
                            style = MaterialTheme.typography.caption2,
                            color = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        WearShoppingItemRow(
                            item = item,
                            onToggle = { onToggle(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WearShoppingItemRow(
    item: WearShoppingItem,
    onToggle: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (item.quantity > 0) {
            val qDisplay = if (item.quantity % 1.0 == 0.0)
                item.quantity.toInt().toString()
            else
                item.quantity.toString()
            Text(
                text = "$qDisplay${item.unit}",
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant,
            )
        }
    }
}
