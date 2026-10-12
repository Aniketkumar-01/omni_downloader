package com.omni.downloader.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omni.downloader.data.models.PlaylistItem
import com.omni.downloader.ui.theme.*

/**
 * Reusable Card component for playlist checklist selection with select/deselect all controls.
 * Virtualized via LazyColumn for high-performance rendering of large playlists.
 */
@Composable
fun PlaylistVideosCard(
    items: List<PlaylistItem>,
    selectedIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Playlist Videos",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        "${selectedIds.size} of ${items.size} selected",
                        fontSize = 11.sp,
                        color = AccentCyan
                    )
                }

                TextButton(
                    onClick = {
                        if (selectedIds.size == items.size) {
                            onDeselectAll()
                        } else {
                            onSelectAll()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (selectedIds.size == items.size) "Deselect All" else "Select All",
                        color = AccentTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { idx, item ->
                    val isSelected = selectedIds.contains(item.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleItem(item.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleItem(item.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = AccentTeal,
                                uncheckedColor = TextSecondary,
                                checkmarkColor = BgDark
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${idx + 1}.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            item.title,
                            color = if (isSelected) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (idx < items.size - 1) {
                        HorizontalDivider(color = BorderSubtle.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
