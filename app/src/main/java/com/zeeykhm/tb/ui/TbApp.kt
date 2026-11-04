package com.zeeykhm.tb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zeeykhm.tb.R

@Composable
fun TbApp(viewModel: TbViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TbBlack),
    ) {
        when {
            state.loading -> LoadingScreen(sourceUrl = state.sourceUrl)

            state.channels.isNotEmpty() -> PlayerScreen(
                state = state,
                player = viewModel.player,
                onSelectChannel = viewModel::play,
                onRefresh = viewModel::refresh,
                onTogglePlayPause = viewModel::togglePlayPause,
                onEditSource = { showSourceDialog = true },
                onRetryPlayback = viewModel::retryPlayback,
            )

            else -> ErrorScreen(
                message = state.error ?: "Couldn’t download the playlist.",
                sourceUrl = state.sourceUrl,
                onRetry = viewModel::refresh,
                onEditSource = { showSourceDialog = true },
            )
        }
    }

    if (showSourceDialog) {
        SourceDialog(
            currentUrl = state.sourceUrl,
            onSave = { url ->
                viewModel.setSource(url)
                showSourceDialog = false
            },
            onReset = {
                viewModel.resetSource()
                showSourceDialog = false
            },
            onDismiss = { showSourceDialog = false },
        )
    }
}

@Composable
private fun LoadingScreen(sourceUrl: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TbLogo()
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = TbAccent,
                strokeWidth = 2.5.dp,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.loading_playlist),
                style = MaterialTheme.typography.bodyMedium,
                color = TbMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = TbMuted.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
    }
}

@Composable
private fun ErrorScreen(
    message: String,
    sourceUrl: String,
    onRetry: () -> Unit,
    onEditSource: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            TbLogo()
            Spacer(Modifier.height(34.dp))
            Text(
                text = stringResource(R.string.couldnt_load),
                style = MaterialTheme.typography.titleLarge,
                color = TbText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TbMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = sourceUrl,
                style = MaterialTheme.typography.bodySmall,
                color = TbMuted.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TbAccent),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.try_again))
            }
            TextButton(onClick = onEditSource) {
                Text(
                    text = stringResource(R.string.change_url),
                    color = TbMuted,
                )
            }
        }
    }
}
