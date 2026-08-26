package com.lyricsstatus.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricsstatus.app.data.model.LyricsLine
import com.lyricsstatus.app.data.model.SongLyrics
import com.lyricsstatus.app.ui.theme.LyricsInactive
import com.lyricsstatus.app.ui.theme.LyricsTranslatedGlow

@Composable
fun LyricsDisplay(
    lyrics: SongLyrics?,
    activeLineIndex: Int,
    isTranslating: Boolean,
    enableTranslation: Boolean,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyrics == null || lyrics.isEmpty) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isTranslating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Translating lyrics with AI…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No synchronized lyrics available",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Play a song or add custom lyrics in the Custom menu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LyricsInactive
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()

    // Auto-scroll list when active line changes
    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex in lyrics.lines.indices) {
            val targetScroll = (activeLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 100.dp, bottom = 140.dp, start = 20.dp, end = 20.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeLineIndex
            LyricsLineItem(
                line = line,
                isActive = isActive,
                enableTranslation = enableTranslation,
                onClick = { onLineClick(line.time) }
            )
        }
    }
}

@Composable
fun LyricsLineItem(
    line: LyricsLine,
    isActive: Boolean,
    enableTranslation: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.04f else 0.98f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "lineScale"
    )

    val textColor by animateColorAsState(
        targetValue = if (isActive) Color.White else LyricsInactive,
        label = "lineColor"
    )

    val translatedColor by animateColorAsState(
        targetValue = if (isActive) LyricsTranslatedGlow else LyricsInactive.copy(alpha = 0.7f),
        label = "transColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .scale(scale)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            } else {
                Color.Transparent
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Main lyric line
            Text(
                text = line.text,
                style = if (isActive) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        lineHeight = 27.sp
                    )
                } else {
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 17.sp,
                        lineHeight = 23.sp
                    )
                },
                color = textColor
            )

            // AI Translated line underneath (if enabled and available)
            if (enableTranslation && !line.textTranslated.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line.textTranslated ?: "",
                    style = if (isActive) {
                        MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                    },
                    color = translatedColor
                )
            }
        }
    }
}
