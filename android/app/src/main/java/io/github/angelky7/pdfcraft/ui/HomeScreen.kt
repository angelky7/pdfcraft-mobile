package io.github.angelky7.pdfcraft.ui

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.RecentDoc

/** The width at which the home screen switches from one column to two. */
private val ExpandedWidth = 840.dp

/**
 * Where the app starts: the documents it already knows about, and one way in.
 *
 * A reader that begins every session with a file picker makes a 300-page manual impossible to
 * continue; the list is the point of this screen, and the picker is the fallback. On a tablet the
 * list moves beside the introduction instead of below it — a wide window has room for both, and
 * scrolling a nearly empty page to find a button is not a good use of it.
 */
@Composable
fun HomeScreen(
    recents: List<RecentDoc>,
    opening: Boolean,
    onOpen: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onForget: (String) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        val expanded = maxWidth >= ExpandedWidth
        val inset = if (expanded) 40.dp else 20.dp

        if (expanded) {
            Row(Modifier.fillMaxSize().padding(horizontal = inset, vertical = 32.dp)) {
                Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    Introduction(expanded = true)
                    Spacer(Modifier.height(32.dp))
                    OpenButton(opening = opening, onOpen = onOpen, fill = true)
                }
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    RecentHeader(count = recents.size)
                    Spacer(Modifier.height(12.dp))
                    RecentList(recents, onOpenRecent, onForget, Modifier.weight(1f))
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(horizontal = inset, vertical = 24.dp)) {
                        Introduction(expanded = false)
                    }

                    if (recents.isEmpty()) {
                        EmptyState(
                            opening = opening,
                            onOpen = onOpen,
                            modifier = Modifier.fillMaxSize().padding(horizontal = inset),
                        )
                    } else {
                        RecentHeader(count = recents.size, modifier = Modifier.padding(horizontal = inset))
                        Spacer(Modifier.height(8.dp))
                        RecentList(
                            recents,
                            onOpenRecent,
                            onForget,
                            Modifier.weight(1f),
                            horizontalPadding = inset,
                        )
                    }
                }

                if (recents.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { if (!opening) onOpen() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = {
                            if (opening) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        },
                        text = { Text(stringResource(if (opening) R.string.home_opening else R.string.action_open)) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = inset, bottom = 24.dp),
                    )
                }
            }
        }
    }
}

/** Logo, name, and the one sentence that says why this app exists. */
@Composable
private fun Introduction(expanded: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = if (expanded) 56.dp else 44.dp)
        Spacer(Modifier.width(if (expanded) 16.dp else 12.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = if (expanded) 26.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.home_subtitle),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (expanded) {
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.home_lead),
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Recent", with the count so the list has a size before it is read. */
@Composable
private fun RecentHeader(count: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_recent),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        if (count > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentList(
    recents: List<RecentDoc>,
    onOpenRecent: (RecentDoc) -> Unit,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 0.dp,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recents, key = { it.id }) { doc ->
            RecentRow(doc = doc, onOpen = { onOpenRecent(doc) }, onForget = { onForget(doc.id) })
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

/** One remembered document: what it is, how far in the reader got, and a way to drop it. */
@Composable
private fun RecentRow(doc: RecentDoc, onOpen: () -> Unit, onForget: () -> Unit) {
    var menu by remember { mutableStateOf(false) }

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Outlined.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = doc.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = detailOf(doc),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_remove_recent)) },
                            onClick = {
                                menu = false
                                onForget()
                            },
                        )
                    }
                }
            }

            if (doc.total > 1) {
                LinearProgressIndicator(
                    progress = { doc.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}

/** "第 5 页 · 共 12 页 · 3 天前", with whatever of it is known. */
@Composable
private fun detailOf(doc: RecentDoc): String {
    val when_ = DateUtils.getRelativeTimeSpanString(
        doc.openedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()

    return if (doc.total > 0) {
        stringResource(R.string.recent_detail, doc.page + 1, doc.total, when_)
    } else {
        when_
    }
}

/** Nothing opened yet: what the app is, and the way in. */
@Composable
private fun EmptyState(opening: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.PictureAsPdf,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(42.dp),
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.home_empty_title),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.home_empty_body),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
        )

        Spacer(Modifier.height(28.dp))

        OpenButton(opening = opening, onOpen = onOpen, fill = false)

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.home_privacy),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}

@Composable
private fun OpenButton(opening: Boolean, onOpen: () -> Unit, fill: Boolean) {
    Button(
        onClick = onOpen,
        enabled = !opening,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        modifier = if (fill) Modifier.fillMaxWidth() else Modifier,
    ) {
        if (opening) {
            CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.home_opening), fontSize = 14.sp)
        } else {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_open), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * The app's mark, drawn rather than shipped as a bitmap: a sheet with a folded corner, on the
 * same green as the launcher icon, so the home screen and the app drawer agree.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val paper = MaterialTheme.colorScheme.onPrimary

    Canvas(modifier.size(size)) {
        val unit = this.size.width / 96f
        val width = this.size.width

        drawRoundRect(color = accent, cornerRadius = CornerRadius(width * 0.23f), size = Size(width, width))

        // The sheet, inside the icon's 72 dp safe zone.
        drawRoundRect(
            color = paper,
            topLeft = Offset(29f * unit, 22f * unit),
            size = Size(35f * unit, 53f * unit),
            cornerRadius = CornerRadius(5f * unit),
        )

        // The folded corner.
        drawPath(
            path = Path().apply {
                moveTo(52f * unit, 22f * unit)
                lineTo(64f * unit, 34f * unit)
                lineTo(52f * unit, 34f * unit)
                close()
            },
            color = accent.copy(alpha = 0.45f),
        )

        // Three lines of text.
        listOf(42f to 21f, 50f to 21f, 58f to 13f).forEach { (top, length) ->
            drawRoundRect(
                color = accent,
                topLeft = Offset(36f * unit, top * unit),
                size = Size(length * unit, 3.2f * unit),
                cornerRadius = CornerRadius(1.6f * unit),
            )
        }
    }
}
