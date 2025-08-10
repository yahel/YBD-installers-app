package com.celerate.installer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.celerate.installer.model.ConnectionStatus

@Composable
fun StatusPill(label: String, status: ConnectionStatus, modifier: Modifier = Modifier) {
    val (color, text) = when (status) {
        is ConnectionStatus.Connected -> Pair(MaterialTheme.colorScheme.tertiary, status.latencyMs?.let { "${'$'}it ms" } ?: "UP")
        is ConnectionStatus.Connecting -> Pair(MaterialTheme.colorScheme.secondary, "…")
        is ConnectionStatus.Disconnected -> Pair(MaterialTheme.colorScheme.error, "DOWN")
        else -> Pair(MaterialTheme.colorScheme.outline, "-")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
