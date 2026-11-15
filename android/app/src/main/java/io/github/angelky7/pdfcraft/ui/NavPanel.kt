package io.github.angelky7.pdfcraft.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.PageCache
import io.github.angelky7.pdfcraft.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.pdfcraft_mobile.AnnotationRow
import uniffi.pdfcraft_mobile.Document
import uniffi.pdfcraft_mobile.OutlineRow
import uniffi.pdfcraft_mobile.PageSize
import uniffi.pdfcraft_mobile.SearchHit

/** How tall the panel takes of the window: enough to browse, not enough to hide the page. */
private const val PanelHeightFraction = 0.62f

/** The tabs of the navigation panel, in the order they are shown. */
enum class NavTab(val label: Int) {
    Thumbnails(R.string.tab_thumbnails),
    Outline(R.string.tab_outline),
    Comments(R.string.tab_comments),
    Search(R.string.tab_search),
}

/**
 * The document's own table of contents: page thumbnails, the outline, the comments already in the
 * file, and a search over its text.
 *
 * All four answer the same question — "where do I want to be" — and all four answer it by moving
 * the reader to a page, which is why they share one panel and one callback.
 */
@Composable
fun NavPanel(
    document: Document,
    pages: List<PageSize>,
    cache: PageCache,
    currentPage: Int,
    initialTab: NavTab,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Box(Modifier.fillMaxSize()) {
        // Tapping outside the panel closes it, as a sheet does.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(PanelHeightFraction),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    NavTab.entries.forEach { entry ->
                        Tab(
                            selected = entry == tab,
                            onClick = { tab = entry },
                            text = { Text(stringResource(entry.label), fontSize = 13.sp, maxLines = 1) },
                        )
                    }
                }

                when (tab) {
                    NavTab.Thumbnails -> ThumbnailsTab(pages, cache, currentPage, onJump)
                    NavTab.Outline -> OutlineTab(document, onJump)
                    NavTab.Comments -> CommentsTab(document, onJump)
                    NavTab.Search -> SearchTab(document, onJump)
                }
            }
        }
    }
}

/** Pages as a grid of small rasters, the current one marked. */
@Composable
private fun ThumbnailsTab(pages: List<PageSize>, cache: PageCache, current: Int, onJump: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = pages.size) { index ->
            Thumbnail(cache, index, pages[index], index == current) { onJump(index) }
        }
    }
}

/** One thumbnail: drawn small, and only when it is scrolled into view. */
@Composable
private fun Thumbnail(cache: PageCache, index: Int, page: PageSize, current: Boolean, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    // A thumbnail is a hundred-odd dp wide, so it is rendered at that size rather than shrunk from
    // a page-sized raster: a grid of 900 full pages would be every page of the document in memory.
    LaunchedEffect(index) {
        bitmap = withContext(Dispatchers.IO) { runCatching { cache.bitmap(index, 110f * density / page.width) }.getOrNull() }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height((104.dp) * (page.height / page.width))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            color = Color.White,
            border = if (current) {
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            },
        ) {
            val drawn = bitmap
            if (drawn != null) {
                Image(
                    bitmap = drawn.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        Text(
            text = page.label,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The outline, indented by depth; an entry that goes nowhere in this file is not clickable. */
@Composable
private fun OutlineTab(document: Document, onJump: (Int) -> Unit) {
    val rows = remember(document) { document.outline() }

    if (rows.isEmpty()) {
        Empty(stringResource(R.string.outline_empty))
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(rows.size) { index ->
            val row: OutlineRow = rows[index]
            val reachable = row.page >= 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = reachable) { if (reachable) onJump(row.page) }
                    .padding(start = 16.dp + (row.depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.title.ifBlank { stringResource(R.string.untitled_document) },
                    fontSize = if (row.depth == 0) 15.sp else 14.sp,
                    fontWeight = if (row.depth == 0) FontWeight.Medium else FontWeight.Normal,
                    color = if (reachable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (reachable) {
                    Text(
                        text = "${row.page + 1}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
    }
}

/** The comments already in the file, including the ink this app wrote. */
@Composable
private fun CommentsTab(document: Document, onJump: (Int) -> Unit) {
    val rows = remember(document) { document.annotations() }

    if (rows.isEmpty()) {
        Empty(stringResource(R.string.comments_empty))
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        items(rows.size) { index ->
            val row: AnnotationRow = rows[index]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onJump(row.page) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.subtype,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.page_number, row.page + 1),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (row.contents.isNotBlank()) {
                    Text(
                        text = row.contents,
                        fontSize = 14.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Search over the text layer: one row per page, with the context around the first match. */
@Composable
private fun SearchTab(document: Document, onJump: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var ran by remember { mutableStateOf(false) }

    suspend fun runSearch() {
        if (query.isBlank()) return
        searching = true
        hits = withContext(Dispatchers.IO) { runCatching { document.search(query, 200) }.getOrDefault(emptyList()) }
        searching = false
        ran = true
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint), fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        // Every keystroke would re-interpret the document; a short pause is what makes this
        // usable on a long manual.
        LaunchedEffect(query) {
            hits = emptyList()
            ran = false
            if (query.isBlank()) return@LaunchedEffect
            kotlinx.coroutines.delay(350)
            runSearch()
        }

        when {
            searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            ran && hits.isEmpty() -> Empty(stringResource(R.string.search_no_results))

            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(hits.size) { index ->
                    val hit = hits[index]
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onJump(hit.page) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.page_number, hit.page + 1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (hit.count > 1) {
                                Text(
                                    text = stringResource(R.string.search_count, hit.count),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        Text(
                            text = hit.snippet,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
