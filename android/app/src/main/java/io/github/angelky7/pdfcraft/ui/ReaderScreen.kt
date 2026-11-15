package io.github.angelky7.pdfcraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.OpenedDocument
import io.github.angelky7.pdfcraft.PageCache
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.ink.InkOverlay
import io.github.angelky7.pdfcraft.ink.InkStore
import io.github.angelky7.pdfcraft.ink.PenHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.pdfcraft_mobile.Document
import uniffi.pdfcraft_mobile.InkStroke
import uniffi.pdfcraft_mobile.PageInk
import uniffi.pdfcraft_mobile.PageSize
import java.io.File

/** The width at which a window gets the tablet treatment: Android's "expanded" size class. */
private val ExpandedWidth = 840.dp

/** What the toolbar offers: four inks and three widths, each with its spoken name. */
private val PenColors = listOf(
    Color(0xFF14181A) to R.string.pen_black,
    Color(0xFFB3261E) to R.string.pen_red,
    Color(0xFF1A4FB4) to R.string.pen_blue,
    Color(0xFFB8860B) to R.string.pen_amber,
)
private val PenWidths = listOf(
    1.0f to R.string.pen_thin,
    2.5f to R.string.pen_medium,
    5.5f to R.string.pen_thick,
)

/**
 * Reading a document: pages down a scrolling column, pinch to zoom, and on a tablet a rail of
 * pages beside them. A stylus writes on the pages while a finger keeps scrolling them.
 *
 * Pages are rendered only as they are scrolled to, and each keeps the raster it was drawn at, so
 * opening a 900-page manual is immediate and memory follows the reader rather than the document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(opened: OpenedDocument, onClose: () -> Unit) {
    val document = opened.document
    val context = LocalContext.current
    val pages = remember(document) { document.pages() }
    val cache = remember(document) { PageCache(document) }
    val ink = remember(document) { InkStore() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var toolbarOpen by remember { mutableStateOf(false) }
    var panelTab by remember { mutableStateOf<NavTab?>(null) }
    var penColor by remember { mutableStateOf(PenColors.first().first) }
    var penWidth by remember { mutableFloatStateOf(PenWidths[1].first) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    DisposableEffect(document) {
        onDispose { cache.clear() }
    }

    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        val next = (zoom * zoomChange).coerceIn(1f, 4f)
        pan = if (next > 1f) pan + panChange else Offset.Zero
        zoom = next
    }

    // Saving goes through the system picker so the original file is never touched: the reader
    // writes a new PDF wherever the user chooses, as Acrobat's "Save a copy" does.
    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target: Uri? ->
        if (target == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { writeInkedCopy(document, ink, context, target) }
            }
            saving = false
            outcome
                .onSuccess { notice = context.getString(R.string.saved_notice) }
                .onFailure { notice = it.message ?: it::class.java.simpleName }
        }
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
                    IconButton(onClick = { panelTab = NavTab.Thumbnails }) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = stringResource(R.string.panel_toggle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { toolbarOpen = !toolbarOpen }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.pen_toggle),
                            tint = if (toolbarOpen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
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
        Box(Modifier.fillMaxSize()) {
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
                                    ink = ink,
                                    penColor = penColor,
                                    penWidth = penWidth,
                                    // The zoom is a draw-time scale, so while it is on the pen's
                                    // coordinates no longer line up with what is under its tip.
                                    // Writing is therefore a 1:1 activity, as on paper.
                                    writable = zoom == 1f,
                                )
                            }
                        }
                    }

                    InkToolbar(
                        open = toolbarOpen,
                        color = penColor,
                        onColor = { penColor = it },
                        width = penWidth,
                        onWidth = { penWidth = it },
                        canUndo = !ink.isEmpty(),
                        saving = saving,
                        onUndo = { ink.undo() },
                        onSave = { savePicker.launch(opened.displayName) },
                        writable = zoom == 1f,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 16.dp),
                    )
                }
            }
            }

            panelTab?.let { tab ->
                NavPanel(
                    document = document,
                    pages = pages,
                    cache = cache,
                    currentPage = currentPage,
                    initialTab = tab,
                    onJump = { page ->
                        panelTab = null
                        scope.launch { listState.scrollToItem(page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))) }
                    },
                    onDismiss = { panelTab = null },
                )
            }
        }
    }

    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            title = { Text(stringResource(R.string.save_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}

/**
 * Write the document plus its strokes to the picked location.
 *
 * The engine writes a real file first, and that file is then copied into the picked URI: a content
 * URI is a stream, not something the engine can open, so going through a file is what lets the
 * engine own the PDF writing — annotations, appearance streams and all.
 */
private fun writeInkedCopy(document: Document, ink: InkStore, context: Context, target: Uri) {
    val exported = File(context.cacheDir, "export.pdf")
    val pageInks = ink.snapshot().map { (page, strokes) ->
        PageInk(
            page = page,
            strokes = strokes.map { stroke ->
                InkStroke(
                    points = stroke.points.flatMap { listOf(it.x, it.y) },
                    color = stroke.color.toArgb(),
                    width = stroke.widthPt,
                )
            },
        )
    }
    document.saveWithInk(exported.absolutePath, pageInks)

    context.contentResolver.openOutputStream(target)?.use { out ->
        exported.inputStream().use { input -> input.copyTo(out, bufferSize = 1 shl 16) }
    } ?: error(context.getString(R.string.error_cannot_write))
}

/** One page: its raster once rendered, its ink on top, and a spinner until the raster arrives. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageImage(
    document: Document,
    cache: PageCache,
    index: Int,
    page: PageSize,
    width: Dp,
    ink: InkStore,
    penColor: Color,
    penWidth: Float,
    writable: Boolean,
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

    val strokes = ink.strokes(index)
    val handler = remember(index, penColor, penWidth) {
        PenHandler(
            color = penColor,
            widthPt = penWidth,
            onStroke = { stroke -> ink.add(index, stroke) },
            onErase = { x, y -> ink.eraseNear(index, x, y, radius = 0.015f) },
        )
    }

    val widthPx = with(LocalDensity.current) { width.toPx() }.toInt()
    val heightPx = with(LocalDensity.current) { height.toPx() }.toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(2.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    // While zoomed, the pen belongs to the reader rather than the writer.
                    if (!writable) false else handler.onEvent(event, widthPx, heightPx)
                },
            contentAlignment = Alignment.Center,
        ) {
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

            InkOverlay(strokes = strokes, pageWidthPt = page.width, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The page rail on a tablet: every page's label, the current one marked. */
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

/**
 * The pen's controls: four colours, three widths, undo, and save.
 *
 * It floats over the bottom of the page rather than taking a strip of its own, so turning writing
 * on does not shrink the page the reader is reading.
 */
@Composable
private fun InkToolbar(
    open: Boolean,
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    canUndo: Boolean,
    saving: Boolean,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    writable: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!open) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (!writable) {
                Text(
                    text = stringResource(R.string.pen_zoom_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PenColors.forEach { (swatch, name) ->
                    val chosen = swatch == color
                    val label = stringResource(name)
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(swatch, CircleShape)
                            .border(
                                width = if (chosen) 2.5.dp else 0.dp,
                                color = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape,
                            )
                            .clickable { onColor(swatch) }
                            .semantics { contentDescription = label },
                    )
                }

                Spacer(Modifier.width(4.dp))
                VerticalDivider(Modifier.height(22.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.width(4.dp))

                PenWidths.forEach { (size, name) ->
                    val chosen = size == width
                    val label = stringResource(name)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                CircleShape,
                            )
                            .clickable { onWidth(size) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((size * 2.8f).dp.coerceAtMost(15.dp))
                                .background(color, CircleShape),
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))
                VerticalDivider(Modifier.height(22.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(
                        Icons.Filled.Undo,
                        contentDescription = stringResource(R.string.action_undo),
                        tint = if (canUndo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        },
                    )
                }

                IconButton(onClick = onSave, enabled = !saving) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = stringResource(R.string.action_save),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
