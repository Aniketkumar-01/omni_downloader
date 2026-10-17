package com.omni.downloader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.R
import com.omni.downloader.ui.theme.*

/**
 * Metadata for a verified supported video/audio platform.
 */
data class PlatformItem(
    val id: String,
    val name: String,
    val iconResId: Int?,
    val domains: List<String>,
    val brandColor: Color
)

val SupportedPlatformsList = listOf(
    PlatformItem("youtube", "YouTube", R.drawable.ic_platform_youtube, listOf("youtube.com", "youtu.be"), Color(0xFFFF0000)),
    PlatformItem("instagram", "Instagram", R.drawable.ic_platform_instagram, listOf("instagram.com"), Color(0xFFE1306C)),
    PlatformItem("tiktok", "TikTok", R.drawable.ic_platform_tiktok, listOf("tiktok.com"), Color(0xFF00F2FE)),
    PlatformItem("x", "X / Twitter", R.drawable.ic_platform_x, listOf("twitter.com", "x.com"), Color(0xFFFFFFFF)),
    PlatformItem("facebook", "Facebook", R.drawable.ic_platform_facebook, listOf("facebook.com", "fb.watch"), Color(0xFF1877F2)),
    PlatformItem("reddit", "Reddit", R.drawable.ic_platform_reddit, listOf("reddit.com"), Color(0xFFFF4500)),
    PlatformItem("bilibili", "Bilibili", R.drawable.ic_platform_bilibili, listOf("bilibili.com", "b23.tv"), Color(0xFF00A1D6)),
    PlatformItem("more", "+1,000 More", null, emptyList(), AccentCobalt)
)

/**
 * Reusable initial empty-state card showcasing verified supported platforms
 * and engine capabilities with live domain matching highlights.
 */
@Composable
fun SupportedPlatformsCard(
    currentUrl: String,
    modifier: Modifier = Modifier,
    onPlatformClick: (PlatformItem) -> Unit = {}
) {
    // Detect active platform from the typed or pasted URL
    val lowerUrl = currentUrl.trim().lowercase()
    val detectedPlatform = SupportedPlatformsList.firstOrNull { item ->
        item.domains.isNotEmpty() && item.domains.any { lowerUrl.contains(it) }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentCobaltContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentCobaltLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "SUPPORTED SOURCES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                            color = AccentCobaltLight
                        )
                        Text(
                            text = "Verified Platforms",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }
                }

                // Live Detection Status Badge
                AnimatedVisibility(
                    visible = detectedPlatform != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        color = StatusSuccessContainer,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, StatusSuccess.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(StatusSuccess)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${detectedPlatform?.name} detected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = StatusSuccess
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Platform Grid (2 rows of 4 items)
            val rows = SupportedPlatformsList.chunked(4)
            rows.forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { platform ->
                        val isDetected = detectedPlatform?.id == platform.id
                        PlatformChip(
                            platform = platform,
                            isDetected = isDetected,
                            modifier = Modifier.weight(1f),
                            onClick = { onPlatformClick(platform) }
                        )
                    }
                }
                if (rowIndex < rows.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderSubtle)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Capabilities Showcase
            Text(
                text = "ENGINE CAPABILITIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CapabilityTile(
                        icon = Icons.Default.Bolt,
                        title = "Embedded yt-dlp",
                        subtitle = "Native NDK C++ engine",
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityTile(
                        icon = Icons.Default.Layers,
                        title = "FIFO Queue",
                        subtitle = "Sequential background downloads",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CapabilityTile(
                        icon = Icons.Default.HighQuality,
                        title = "4K & MP3 Audio",
                        subtitle = "Lossless streams & extraction",
                        modifier = Modifier.weight(1f)
                    )
                    CapabilityTile(
                        icon = Icons.Default.Shield,
                        title = "100% On-Device",
                        subtitle = "Private with zero tracking",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Compact interactive platform badge.
 */
@Composable
private fun PlatformChip(
    platform: PlatformItem,
    isDetected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isDetected) AccentCobaltContainer else SurfaceCard,
        animationSpec = tween(durationMillis = 200),
        label = "chipBg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isDetected) AccentCobalt else BorderSubtle,
        animationSpec = tween(durationMillis = 200),
        label = "chipBorder"
    )

    Surface(
        modifier = modifier
            .height(68.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = BorderStroke(if (isDetected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterVertically,
            verticalArrangement = Arrangement.Center
        ) {
            if (platform.iconResId != null) {
                Icon(
                    painter = painterResource(id = platform.iconResId),
                    contentDescription = platform.name,
                    tint = Color.Unspecified, // Preserves SVG brand colors
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(AccentCobalt.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = platform.name,
                        tint = AccentCobaltLight,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = platform.name,
                fontSize = 11.sp,
                fontWeight = if (isDetected) FontWeight.Bold else FontWeight.Medium,
                color = if (isDetected) Color.White else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Reusable capability showcase mini-tile.
 */
@Composable
private fun CapabilityTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AccentCobaltLight,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
