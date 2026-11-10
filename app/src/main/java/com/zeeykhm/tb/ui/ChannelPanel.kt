package com.zeeykhm.tb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zeeykhm.tb.R
import com.zeeykhm.tb.data.Channel
import kotlinx.coroutines.delay

@Composable
fun ChannelPanel(
    state: TbUiState,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSelectMode: (Boolean) -> Unit,
    landscape: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }

    val filtered = remember(state.channels, state.radioMode, query) {
        val needle = query.trim()
        state.channels.withIndex().filter { (_, channel) ->
            channel.isRadio == state.radioMode &&
                (needle.isEmpty() ||
                    channel.name.contains(needle, ignoreCase = true) ||
                    channel.group?.contains(needle, ignoreCase = true) == true)
        }
    }

    LaunchedEffect(state.currentIndex, state.radioMode, query) {
        if (query.isBlank()) {
            val position = filtered.indexOfFirst { it.index == state.currentIndex }
            if (position >= 0) {
                listState.scrollToItem(position)
                delay(60)
                runCatching { selectedFocus.requestFocus() }
            }
        }
    }

    Surface(
        color = TbSurface,
        shape = if (landscape) {
            RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        shadowElevation = 24.dp,
        modifier = Modifier
            .then(
                if (landscape) {
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.38f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.76f)
                },
            )
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!landscape) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .size(width = 42.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(2.dp)),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = if (landscape) 16.dp else 8.dp, bottom = 2.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(TbAccent, CircleShape),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "TB",
                            style = MaterialTheme.typography.labelSmall,
                            color = TbAccent,
                        )
                        if (state.refreshing) {
                            Spacer(Modifier.width(10.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = TbMuted,
                                strokeWidth = 1.5.dp,
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.channel_count_split,
                            state.tvCount,
                            state.radioCount,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = TbText,
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp),
                ) {
                    if (state.isPlaying) {
                        PauseGlyph(contentDescription = stringResource(R.string.pause))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = TbText,
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !state.refreshing,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = if (state.refreshing) {
                            TbMuted.copy(alpha = 0.4f)
                        } else {
                            TbText
                        },
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = TbText,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                ModeTab(
                    label = stringResource(R.string.tv),
                    count = state.tvCount,
                    selected = !state.radioMode,
                    onClick = { onSelectMode(false) },
                    modifier = Modifier.weight(1f),
                )
                ModeTab(
                    label = stringResource(R.string.radio),
                    count = state.radioCount,
                    selected = state.radioMode,
                    onClick = { onSelectMode(true) },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_channels),
                        color = TbMuted,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = TbMuted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = TbMuted,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = TbSurfaceHigh.copy(alpha = 0.55f),
                    unfocusedContainerColor = TbSurfaceHigh.copy(alpha = 0.55f),
                    focusedBorderColor = TbAccent.copy(alpha = 0.65f),
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = TbAccent,
                    focusedTextColor = TbText,
                    unfocusedTextColor = TbText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(
                    items = filtered,
                    key = { item -> item.value.key },
                ) { item ->
                    ChannelRow(
                        index = item.index,
                        channel = item.value,
                        selected = item.index == state.currentIndex,
                        focusRequester = if (item.index == state.currentIndex) {
                            selectedFocus
                        } else {
                            null
                        },
                        onClick = { onSelect(item.index) },
                    )
                }

                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (query.isBlank()) {
                                    stringResource(R.string.no_channels_in_section)
                                } else {
                                    stringResource(R.string.no_matches, query.trim())
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = TbMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeTab(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TbAccent else TbSurfaceHigh.copy(alpha = 0.55f),
        contentColor = if (selected) Color.White else TbMuted,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) {
                    Color.White.copy(alpha = 0.75f)
                } else {
                    TbMuted.copy(alpha = 0.7f)
                },
            )
        }
    }
}

@Composable
private fun ChannelRow(
    index: Int,
    channel: Channel,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .background(if (selected) TbAccent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(
            text = (index + 1).toString().padStart(2, '0'),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TbAccent else TbMuted.copy(alpha = 0.5f),
            modifier = Modifier.width(34.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) TbText else TbText.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            channel.group?.takeIf { it.isNotBlank() }?.let { group ->
                Spacer(Modifier.height(1.dp))
                Text(
                    text = group,
                    style = MaterialTheme.typography.bodySmall,
                    color = TbMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (selected) {
            Spacer(Modifier.width(12.dp))
            NowPlayingBars()
        }
    }
}

@Composable
private fun NowPlayingBars() {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(8, 14, 10).forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(height.dp)
                    .background(TbAccent, RoundedCornerShape(1.dp)),
            )
        }
    }
}
