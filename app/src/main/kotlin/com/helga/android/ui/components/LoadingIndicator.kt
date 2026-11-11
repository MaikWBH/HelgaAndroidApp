package com.helga.android.ui.components

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.helga.android.R

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    color: Color = ProgressIndicatorDefaults.circularColor,
    label: String = stringResource(R.string.common_loading),
) {
    CircularProgressIndicator(
        modifier = modifier.semantics { contentDescription = label },
        color = color,
        strokeWidth = strokeWidth,
    )
}

@ThemePreviews
@Composable
private fun LoadingIndicatorPreview() {
    PreviewSurface { LoadingIndicator() }
}
