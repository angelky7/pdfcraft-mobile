package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.RecentDoc

/**
 * Where documents come from, and what the app is keeping.
 *
 * The list of copies is the same one the home screen shows, with the numbers a file manager would
 * give: how many, how big, and how to get rid of them. The one thing this screen will not pretend
 * to have is a cloud — the app never asks for the network permission, so a row offering one would
 * be a lie with an icon on it.
 */
@Composable
fun FilesScreen(
    recents: List<RecentDoc>,
    sizes: Map<String, Long>,
    starred: Set<String>,
    totalBytes: Long,
    onOpenRecent: (RecentDoc) -> Unit,
    onToggleStar: (String) -> Unit,
    onForget: (String) -> Unit,
    onImport: () -> Unit,
    onClearAll: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columnWidth = if (maxWidth > 900.dp) 900.dp else maxWidth

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(columnWidth)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ScreenTitle(stringResource(R.string.files_title))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.files_summary, recents.size, humanSize(totalBytes)),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.files_on_device),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                if (recents.isEmpty()) {
                    item {
                        EmptyNotice(
                            icon = Icons.Outlined.PictureAsPdf,
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.files_empty_body),
                            action = {
                                TextButton(onClick = onImport) { Text(stringResource(R.string.action_open)) }
                            },
                        )
                    }
                } else {
                    items(recents, key = { it.id }) { doc ->
                        DocRow(
                            doc = doc,
                            bytes = sizes[doc.id] ?: 0L,
                            starred = doc.id in starred,
                            onOpen = { onOpenRecent(doc) },
                            onToggleStar = { onToggleStar(doc.id) },
                            onForget = { onForget(doc.id) },
                        )
                        Hairline(Modifier.padding(start = 72.dp, end = 20.dp))
                    }
                }

                item {
                    SectionHeader(
                        text = stringResource(R.string.files_other),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item {
                    LocationRow(
                        icon = Icons.Filled.FolderOpen,
                        accent = Accent.Green,
                        title = stringResource(R.string.files_browse_title),
                        body = stringResource(R.string.files_browse_body),
                        onClick = onImport,
                    )
                }

                item {
                    LocationRow(
                        icon = Icons.Outlined.Cloud,
                        accent = Accent.Blue,
                        title = stringResource(R.string.files_cloud_title),
                        body = stringResource(R.string.files_cloud_body),
                        onClick = null,
                    )
                }

                if (recents.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Hairline()
                        SettingsRow(
                            title = stringResource(R.string.files_clear),
                            body = stringResource(R.string.files_clear_body),
                            destructive = true,
                            onClick = { confirmClear = true },
                            trailing = {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                        )
                    }
                }

                item {
                    Footnote(
                        text = stringResource(R.string.files_footnote),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.files_clear_confirm_title)) },
            text = { Text(stringResource(R.string.files_clear_confirm_body, recents.size, humanSize(totalBytes))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) {
                    Text(stringResource(R.string.action_remove_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** A place documents can come from: a tinted glyph, a name, and what tapping it will do. */
@Composable
private fun LocationRow(
    icon: ImageVector,
    accent: Accent,
    title: String,
    body: String,
    onClick: (() -> Unit)?,
) {
    val tap = onClick
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (tap != null) it.clickable { tap() } else it }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.block()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.ink(), modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (onClick != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A card that wraps a whole section, for screens that have one thing worth looking at. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.Top) { content() }
    }
}
