package io.github.angelky7.pdfcraft.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.OpenedDocument
import io.github.angelky7.pdfcraft.PageCache
import io.github.angelky7.pdfcraft.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.pdfcraft_mobile.Document
import uniffi.pdfcraft_mobile.PageSize

/** The width at which a window gets the tablet treatment: Android's "expanded" size class. */
private val ExpandedWidth = 840.dp

/**
 * Reading a document: pages down a scrolling column, pinch to zoom, and on a tablet a rail of
 * pages beside them.
 *
 * Pages are rendered only as they are scrolled to, and each keeps the raster it was drawn at, so
 * opening a 900-page manual is immediate and memory follows the reader rather than the document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(opened: OpenedDocument, onClose: () -> Unit) {
    val document = opened.document
    val pages = remember(document) { document.pages() }
    val cache = remember(document) { PageCache(document) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // The page number in the bar is whatever page is at the top, which is what a reader calls
    // "where I am".
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    // A raster of every page ever shown would outlive the document; the cache goes with it.
    DisposableEffect(document) {
        onDispose { cache.clear() }
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (zoom * zoomChange).coerceIn(1f, 4f)
        // Panning only makes sense while zoomed in; at 1× the column scrolls instead.
        pan = if (next > 1f) pan + panChange else Offset.Zero
        zoom = next
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                title = {
                    Column {
                        Text(
                            text = document.title()?.takeIf { it.isNotBlank() } ?: opened.displayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.page_of, currentPage + 1, pages.size),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (zoom > 1f) {
                        Text(
                            text = "${(zoom * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
    ) { insets ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            val expanded = maxWidth >= ExpandedWidth

            Row(Modifier.fillMaxSize()) {
                if (expanded && pages.isNotEmpty()) {
                    PageRail(
                        pages = pages,
                        current = currentPage,
                        onSelect = { index -> scope.launch { listState.scrollToItem(index) } },
                    )
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight().width(1.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                Box(Modifier.weight(1f)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val margin = if (expanded) 28.dp else 10.dp
                        val pageWidth = maxWidth - margin * 2

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoom,
                                    scaleY = zoom,
                                    translationX = pan.x,
                                    translationY = pan.y,
                                )
                                .transformable(transform),
                            contentPadding = PaddingValues(horizontal = margin, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(count = pages.size, key = { it }) { index ->
                                PageImage(
                                    document = document,
                                    cache = cache,
                                    index = index,
                                    page = pages[index],
                                    width = pageWidth,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One page: its raster once rendered, a spinner until then, and the right height either way. */
@Composable
private fun PageImage(
    document: Document,
    cache: PageCache,
    index: Int,
    page: PageSize,
    width: Dp,
) {
    val density = LocalDensity.current.density
    val height = width * (page.height / page.width)

    // A page is drawn at the pixels the slot actually occupies, so it is sharp at 1× without
    // being decoded at print resolution.
    val scale = (width.value * density) / page.width

    var bitmap by remember(index, scale) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, scale) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { cache.bitmap(index, scale) }.getOrNull()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val drawn = bitmap
            if (drawn != null) {
                Image(
                    bitmap = drawn.asImageBitmap(),
                    contentDescription = stringResource(R.string.page_of, index + 1, document.pageCount()),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** The tablet rail: every page's label, the current one marked. */
@Composable
private fun PageRail(pages: List<PageSize>, current: Int, onSelect: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .width(168.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(count = pages.size) { index ->
            val selected = index == current
            Surface(
                onClick = { onSelect(index) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = pages[index].label,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
