package com.omni.downloader.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.omni.downloader.data.models.MediaMetadata
import com.omni.downloader.ui.theme.*

/**
 * Reusable Card component for media preview, quality chip selection, size indicators, and download triggers.
 */
@Composable
fun MediaPreviewCard(
    meta: MediaMetadata,
    selectedQuality: String,
    selectedPlaylistCount: Int,
    onQualitySelect: (String) -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasActiveDownload: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Thumbnail & Duration Overlay
            if (!meta.thumbnail.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AsyncImage(
                        model = meta.thumbnail,
                        contentDescription = meta.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    meta.durationFormatted?.let { dur ->
                        Surface(
                            color = BgDark.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                dur,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Title & Channel
            Text(
                meta.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${meta.channel ?: "Unknown"} • ${meta.platform}",
                fontSize = 13.sp,
                color = AccentCobaltLight
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Quality Chips
            Text(
                "Select Download Quality:",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(meta.availableQualities) { quality ->
                    val isSelected = quality.id == selectedQuality
                    FilterChip(
                        selected = isSelected,
                        onClick = { onQualitySelect(quality.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    quality.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                val sizeBadge = quality.estimatedSizeFormatted ?: "~45 MB"
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isSelected) Color.White.copy(alpha = 0.2f) else AccentCobaltContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = sizeBadge,
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color.White else AccentCobaltLight,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCobalt,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = BorderSubtle,
                            selectedBorderColor = AccentCobalt
                        )
                    )
                }
            }

            // Prominent Estimated Download Size Badge
            val currentQualityOption = meta.availableQualities.firstOrNull { it.id == selectedQuality }
            val displaySize = currentQualityOption?.estimatedSizeFormatted ?: "~45 MB"

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = SurfaceCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentCobalt,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Estimated Download Size: ",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (meta.isPlaylist && meta.playlistItems.isNotEmpty()) {
                            "$displaySize (${meta.playlistItems.size} videos)"
                        } else {
                            displaySize
                        },
                        fontSize = 12.sp,
                        color = AccentCobaltLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Playlist detected helper card
            if (meta.isPlaylist) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = AccentCobaltContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, AccentCobalt.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = AccentCobaltLight,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val totalCount = meta.playlistItems.size
                        val playlistText = if (totalCount > 0) {
                            "Playlist detected • $selectedPlaylistCount of $totalCount items selected"
                        } else {
                            "Playlist detected • Full collection ready"
                        }
                        Text(
                            playlistText,
                            color = AccentCobaltLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download Trigger Button
            val isPlaylist = meta.isPlaylist
            val hasItems = meta.playlistItems.isNotEmpty()
            val downloadBtnText = when {
                hasActiveDownload && isPlaylist && hasItems && selectedPlaylistCount == meta.playlistItems.size -> "Queue All (${selectedPlaylistCount} Videos)"
                hasActiveDownload && isPlaylist && hasItems && selectedPlaylistCount > 0 -> "Queue Selected (${selectedPlaylistCount} Videos)"
                hasActiveDownload && isPlaylist && hasItems && selectedPlaylistCount == 0 -> "Select at least 1 video"
                hasActiveDownload && isPlaylist -> "Queue Entire Playlist"
                hasActiveDownload -> "Add to Download Queue"
                isPlaylist && hasItems && selectedPlaylistCount == meta.playlistItems.size -> "Download All (${selectedPlaylistCount} Videos)"
                isPlaylist && hasItems && selectedPlaylistCount > 0 -> "Download Selected (${selectedPlaylistCount} Videos)"
                isPlaylist && hasItems && selectedPlaylistCount == 0 -> "Select at least 1 video"
                isPlaylist -> "Download Entire Playlist"
                else -> "Download to Phone"
            }
            val isDownloadEnabled = !isPlaylist || !hasItems || selectedPlaylistCount > 0

            Button(
                onClick = onDownloadClick,
                enabled = isDownloadEnabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCobalt,
                    disabledContainerColor = SurfaceCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    if (meta.isPlaylist) Icons.Default.PlaylistPlay else Icons.Default.Download,
                    contentDescription = null,
                    tint = if (isDownloadEnabled) Color.White else TextTertiary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    downloadBtnText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDownloadEnabled) Color.White else TextTertiary
                )
            }
        }
    }
}
