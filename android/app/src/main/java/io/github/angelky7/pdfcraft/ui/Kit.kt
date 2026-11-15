package io.github.angelky7.pdfcraft.ui

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.RecentDoc
import io.github.angelky7.pdfcraft.data.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * The pieces every screen is built from.
 *
 * They are Material widgets with the reader's proportions: a hairline instead of a shadow, a
 * 16 dp card, a 44 dp row. Keeping them here rather than inside each screen is what makes the
 * home screen, the file list and the tools grid agree about how big a row is.
 */

/** 571.5 KB, 10.0 MB: a size a person can hold in their head. */
fun humanSize(bytes: Long): String = when {
    bytes <= 0L -> "—"
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024f * 1024f * 1024f))
}

/** A hairline. The app separates blocks with one of these rather than with a shadow. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** A small heading over a block: what the next few rows are. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** The screen's own title: the largest thing on a page that has no document on it. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

/** A footnote: the one line of small print a screen needs. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = MaterialTheme.colorScheme.outline,
        modifier = modifier,
    )
}

/**
 * The app's mark, drawn rather than shipped as a bitmap: a sheet with a folded corner on the
 * launcher icon's green, so the home screen and the app drawer agree.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    val accent = BrandGreen
    val paper = Color.White

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

/**
 * A document's first page, small.
 *
 * The thumbnail is written to disk when the document is imported; a row shows it if it is there
 * and a sheet glyph if it is not, which is why a document imported before thumbnails existed
 * still lists.
 */
@Composable
fun DocThumbnail(id: String, width: Dp = 36.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = id) {
        value = withContext(Dispatchers.IO) { Thumbs.load(context, id) }
    }

    Box(
        modifier
            .width(width)
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Outlined.PictureAsPdf,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(width * 0.5f),
            )
        }
    }
}

/**
 * One shortcut on the home shelf: a tinted square with a glyph, and its name under it.
 *
 * The shelf is where a reader app earns its first screen: six verbs, each visible without a menu,
 * each one a single tap away from the document it needs.
 */
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    accent: Accent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(accent.block()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.ink(), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One tool, as a card: a tinted glyph, a name, and the sentence that says what it does.
 *
 * A tool that has to be explained in a menu is a tool nobody finds; the description is the point
 * of the card, and the card is the size of a thumb.
 */
@Composable
fun ToolCard(
    accent: Accent,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tool: io.github.angelky7.pdfcraft.ink.Tool? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accent.block()),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    tool != null -> ToolGlyph(tool, accent.ink(), 24.dp)
                    icon != null -> Icon(icon, contentDescription = null, tint = accent.ink(), modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = body,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One remembered document: its first page, its name, and what the file is.
 *
 * The second line is the file's own facts — size, length, where the reader got to — because the
 * question a list of documents has to answer is "which of these do I want", and the name alone
 * often does not.
 */
@Composable
fun DocRow(
    doc: RecentDoc,
    bytes: Long,
    starred: Boolean,
    onOpen: () -> Unit,
    onToggleStar: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocThumbnail(id = doc.id, width = 38.dp)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = doc.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = metaOf(doc, bytes),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (starred) {
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.action_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (starred) R.string.action_unstar else R.string.action_star,
                            ),
                        )
                    },
                    onClick = {
                        menu = false
                        onToggleStar()
                    },
                )
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
}

/** "PDF · 10.0 MB · 12/128 页 · 3 天前", with whatever of it is known. */
@Composable
private fun metaOf(doc: RecentDoc, bytes: Long): String {
    val parts = mutableListOf("PDF")
    if (bytes > 0) parts += humanSize(bytes)
    if (doc.total > 0) parts += stringResource(R.string.unit_pages_read, doc.page + 1, doc.total)
    parts += DateUtils.getRelativeTimeSpanString(
        doc.openedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    return parts.joinToString(" · ")
}

/** An empty list, said plainly: what is missing, and the one way to fix it. */
@Composable
fun EmptyNotice(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

/** The four places, on a phone: the bar across the bottom. */
@Composable
fun BottomBar(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        HomeTab.values().forEach { item ->
            val active = item == tab
            NavigationBarItem(
                selected = active,
                onClick = { onTab(item) },
                icon = {
                    Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                },
                label = { Text(stringResource(item.label), fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

/** The same four places on a tablet: a rail down the side, so the page keeps its width. */
@Composable
fun SideRail(tab: HomeTab, onTab: (HomeTab) -> Unit) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background,
        header = {
            Box(Modifier.padding(top = 8.dp, bottom = 16.dp)) { BrandMark(36.dp) }
        },
    ) {
        Spacer(Modifier.height(8.dp))
        HomeTab.values().forEach { item ->
            NavigationRailItem(
                selected = item == tab,
                onClick = { onTab(item) },
                icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(stringResource(item.label), fontSize = 10.sp) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

/** A row of the file list's worth of information, in the settings screen's rhythm. */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    value: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tap = onClick
    Row(
        modifier
            .fillMaxWidth()
            .let { if (tap != null) it.clickable { tap() } else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (body != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            it()
        }
    }
}

/** The padding every list screen uses, so a row and a card line up. */
val ScreenPadding = PaddingValues(horizontal = 20.dp)
