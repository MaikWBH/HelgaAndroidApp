package com.helga.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.helga.android.R
import com.helga.android.data.sync.SyncStatus

@Composable
fun SyncStatusIcon(status: SyncStatus, modifier: Modifier = Modifier) {
    when (status) {
        SyncStatus.Idle -> Icon(
            imageVector = Icons.Default.Sync,
            contentDescription = stringResource(R.string.sync_idle),
            modifier = modifier.size(24.dp),
        )
        SyncStatus.Syncing -> {
            val transition = rememberInfiniteTransition(label = "sync-rotate")
            val angle by transition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "angle",
            )
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = stringResource(R.string.sync_syncing),
                modifier = modifier.size(24.dp).rotate(angle),
            )
        }
        is SyncStatus.Success -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.sync_ok),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(24.dp),
        )
        SyncStatus.Offline -> Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = stringResource(R.string.sync_offline),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(24.dp),
        )
        is SyncStatus.Error -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = stringResource(R.string.sync_error),
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier.size(24.dp),
        )
    }
}
