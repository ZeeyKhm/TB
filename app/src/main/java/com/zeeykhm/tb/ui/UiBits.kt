package com.zeeykhm.tb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeeykhm.tb.R

@Composable
fun TbLogo(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(54.dp)
                .background(TbAccent, RoundedCornerShape(16.dp)),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = "TB",
            style = MaterialTheme.typography.headlineLarge,
            color = TbText,
        )
    }
}

@Composable
fun PauseGlyph(
    modifier: Modifier = Modifier,
    color: Color = TbText,
    contentDescription: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .size(18.dp)
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                }
            },
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
fun ChannelBadge(
    name: String,
    group: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 300.dp)
            .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(TbAccent, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = TbText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            group?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TbMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun OfflineBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(Color(0xFF3A2A12).copy(alpha = 0.92f), RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = Color(0xFFFFC46B),
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.offline_copy),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFFFC46B),
        )
    }
}

@Composable
fun PlaybackErrorCard(
    message: String,
    onRetry: () -> Unit,
    onNext: () -> Unit,
    onOpenChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = TbSurface.copy(alpha = 0.97f),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 14.dp,
        modifier = modifier.widthIn(max = 420.dp),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .background(TbAccent.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = TbAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.cant_play),
                    style = MaterialTheme.typography.titleLarge,
                    color = TbText,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TbMuted,
            )
            Spacer(Modifier.height(18.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TbAccent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(R.string.retry))
                }
                TextButton(onClick = onNext) {
                    Text(
                        text = stringResource(R.string.next_channel),
                        color = TbMuted,
                    )
                }
            }

            TextButton(
                onClick = onOpenChannels,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(R.string.channels),
                    color = TbAccent,
                )
            }
        }
    }
}
