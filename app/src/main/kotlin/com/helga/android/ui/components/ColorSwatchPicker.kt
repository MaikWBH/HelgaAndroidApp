package com.helga.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.helga.android.ui.theme.accentPrimaryColors

data class ColorSwatch(
    val key: String,
    val label: String,
    val color: Color?,
    val icon: ImageVector? = null,
)

@Composable
fun ColorSwatchPicker(
    swatches: List<ColorSwatch>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(swatches, key = { it.key }) { swatch ->
            val selected = swatch.key == selectedKey
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(swatch.key) },
                    )
                    .semantics { contentDescription = swatch.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatch.color ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (swatch.icon != null || swatch.color == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                Color.White
                            },
                        )
                    } else if (swatch.icon != null) {
                        Icon(
                            imageVector = swatch.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val previewSwatches = listOf(
    ColorSwatch("-1", "System (Wallpaper)", null, Icons.Filled.Wallpaper),
    ColorSwatch("0", "Grün", accentPrimaryColors[0]),
    ColorSwatch("1", "Blau", accentPrimaryColors[1]),
    ColorSwatch("3", "Orange", accentPrimaryColors[3]),
)

@ThemePreviews
@Composable
private fun ColorSwatchPickerSystemSelectedPreview() {
    PreviewSurface { ColorSwatchPicker(previewSwatches, selectedKey = "-1", onSelect = {}) }
}

@ThemePreviews
@Composable
private fun ColorSwatchPickerColorSelectedPreview() {
    PreviewSurface { ColorSwatchPicker(previewSwatches, selectedKey = "1", onSelect = {}) }
}
