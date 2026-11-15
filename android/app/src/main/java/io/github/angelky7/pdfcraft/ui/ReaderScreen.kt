package io.github.angelky7.pdfcraft.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.angelky7.pdfcraft.ink.AnnotationOverlay
import io.github.angelky7.pdfcraft.ink.MarkHandler
import io.github.angelky7.pdfcraft.ink.MarkOverlay
import io.github.angelky7.pdfcraft.ink.MarkStore
import io.github.angelky7.pdfcraft.ink.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.pdfcraft_mobile.AnnotationRow
import uniffi.pdfcraft_mobile.Document
import uniffi.pdfcraft_mobile.Mark as PdfMark
import uniffi.pdfcraft_mobile.MarkKind
import uniffi.pdfcraft_mobile.PageMark
import uniffi.pdfcraft_mobile.PageSize
import java.io.File

/** How pages are laid out. */
enum class ReadMode {
    /** Pages down one column: how a phone reads a document. */
    Vertical,

    /** One page at a time, swiped sideways: how a tablet reads one. */
    Single,
}

/** The width at which a window gets the tablet layout. */
private val ExpandedWidth = 840.dp

/** How far a page may be enlarged from fit-width. */
private const val MaxZoom = 3.0f

/**
 * Reading a document.
 *
 * Pages are laid out from their own geometry and each is rendered only as it is reached, so
 * opening a 900-page manual is immediate and memory follows the reader rather than the document.
 * Everything that maps a touch to the page — writing, erasing — works in page-relative
 * coordinates, which is why a zoom is applied to the page's *width* rather than to the finished
 * picture: the page keeps being laid out, so its scroll range stays right, its text stays sharp,
 * and a stroke lands where the tip was even at 3×.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    opened: OpenedDocument,
    onProgress: (page: Int, total: Int) -> Unit,
    onClose: () -> Unit,
) {
    val document = opened.document
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pages = remember(document) { document.pages() }
    val cache = remember(document) { PageCache(document) }
    val store = remember(document) { MarkStore() }

    // The annotations the file arrived with, per page: without drawing them a document
    // highlighted elsewhere opens looking untouched.
    val existing = remember(document) {
        runCatching { document.annotations() }.getOrDefault(emptyList()).groupBy { it.page }
    }

    val start = opened.startPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = start)
    val pagerState = rememberPagerState(initialPage = start) { pages.size }

    var mode by remember { mutableStateOf(ReadMode.Vertical) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var writing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.Pen) }
    var penColor by remember { mutableStateOf(PenColors.first().first) }
    var penWidth by remember { mutableFloatStateOf(PenWidths[1].first) }
    var allowFinger by remember { mutableStateOf(false) }
    var panelTab by remember { mutableStateOf<NavTab?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var goToOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val currentPage by remember(mode) {
        derivedStateOf {
            if (mode == ReadMode.Vertical) {
                listState.firstVisibleItemIndex
            } else {
                pagerState.currentPage
            }
        }
    }

    DisposableEffect(document) {
        onDispose { cache.clear() }
    }

    // Remember where the reader is, after they have stopped moving: a flick through sixty pages
    // should leave one write behind, not sixty.
    LaunchedEffect(currentPage, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        delay(700)
        onProgress(currentPage, pages.size)
    }

    fun goTo(page: Int) {
        val target = page.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        if (mode == ReadMode.Vertical) {
            scope.launch { listState.scrollToItem(target) }
        } else {
            scope.launch { pagerState.scrollToPage(target) }
        }
    }

    // Saving goes through the system picker so the original file is never touched.
    val savePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { target: Uri? ->
        if (target == null) return@rememberLauncherForActivityResult
        saving = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { writeMarksCopy(document, store, context, target) }
            }
            saving = false
            outcome
                .onSuccess { notice = context.getString(R.string.saved_notice) }
                .onFailure { notice = it.message ?: it::class.java.simpleName }
        }
    }

    Column(Modifier.fillMaxSize().background(PageBackground)) {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_close),
                    )
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
                            fontSize = 11.sp,
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
                IconButton(
                    onClick = {
                        writing = !writing
                        if (writing) zoom = 1f
                    },
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.pen_toggle),
                        tint = if (writing) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mode_vertical)) },
                            trailingIcon = { if (mode == ReadMode.Vertical) Check() },
                            onClick = {
                                mode = ReadMode.Vertical
                                menuOpen = false
                                scope.launch { listState.scrollToItem(pagerState.currentPage) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.mode_single)) },
                            trailingIcon = { if (mode == ReadMode.Single) Check() },
                            onClick = {
                                mode = ReadMode.Single
                                menuOpen = false
                                scope.launch { pagerState.scrollToPage(listState.firstVisibleItemIndex) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_go_to_page)) },
                            onClick = {
                                menuOpen = false
                                goToOpen = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_save)) },
                            onClick = {
                                menuOpen = false
                                savePicker.launch(opened.displayName)
                            },
                        )
                    }
                }
            },
        )

        Box(Modifier.weight(1f)) {
            when (mode) {
                ReadMode.Vertical -> VerticalReader(
                    document = document,
                    cache = cache,
                    pages = pages,
                    listState = listState,
                    zoom = zoom,
                    store = store,
                    existing = existing,
                    writing = writing,
                    tool = tool,
                    penColor = penColor,
                    penWidth = penWidth,
                    allowFinger = allowFinger,
                    onZoomBy = { factor -> zoom = (zoom * factor).coerceIn(1f, MaxZoom) },
                    onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f },
                )

                ReadMode.Single -> SingleReader(
                    document = document,
                    cache = cache,
                    pages = pages,
                    pagerState = pagerState,
                    zoom = zoom,
                    store = store,
                    existing = existing,
                    writing = writing,
                    tool = tool,
                    penColor = penColor,
                    penWidth = penWidth,
                    allowFinger = allowFinger,
                    onZoomBy = { factor -> zoom = (zoom * factor).coerceIn(1f, MaxZoom) },
                )
            }

            // The controls float over the page rather than taking a strip of their own: turning
            // writing on should not shrink the document being written on.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (writing) {
                    InkToolbar(
                        tool = tool,
                        onTool = { tool = it },
                        color = penColor,
                        onColor = { penColor = it },
                        width = penWidth,
                        onWidth = { penWidth = it },
                        allowFinger = allowFinger,
                        onAllowFinger = { allowFinger = it },
                        canUndo = !store.isEmpty(),
                        saving = saving,
                        onUndo = { store.undo() },
                        onSave = { savePicker.launch(opened.displayName) },
                    )
                } else {
                    PageBar(
                        page = currentPage + 1,
                        total = pages.size,
                        zoom = zoom,
                        onZoom = { zoom = it },
                        onSeek = { goTo(it) },
                    )
                }
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
                goTo(page)
            },
            onDismiss = { panelTab = null },
        )
    }

    if (goToOpen) {
        GoToPageDialog(
            total = pages.size,
            onGo = {
                goToOpen = false
                goTo(it)
            },
            onDismiss = { goToOpen = false },
        )
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

/** A tick in a menu: the mark of the option already in force. */
@Composable
private fun Check() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(17.dp),
    )
}

/**
 * Pages down one column.
 *
 * The zoom widens the column instead of magnifying the finished picture, so the list scrolls the
 * document as it now is: at 2× the reader scrolls through twice the page, sees twice the detail,
 * and the page is rendered at the pixels it occupies rather than blown up from a smaller raster.
 */
@Composable
private fun VerticalReader(
    document: Document,
    cache: PageCache,
    pages: List<PageSize>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    zoom: Float,
    store: MarkStore,
    existing: Map<Int, List<AnnotationRow>>,
    writing: Boolean,
    tool: Tool,
    penColor: Color,
    penWidth: Float,
    allowFinger: Boolean,
    onZoomBy: (Float) -> Unit,
    onDoubleTap: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= ExpandedWidth
        val margin = if (expanded) 28.dp else 10.dp
        val fitWidth = (maxWidth - margin * 2).coerceAtLeast(80.dp)
        val pageWidth = fitWidth * zoom
        val columnWidth = if (pageWidth + margin * 2 > maxWidth) pageWidth + margin * 2 else maxWidth

        val horizontal = rememberScrollState()

        Box(
            Modifier
                .fillMaxSize()
                .horizontalScroll(horizontal)
                .pointerInputDoubleTap(!writing, onDoubleTap)
                .pinchZoom(!writing, onZoomBy),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .width(columnWidth)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = margin, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(count = pages.size, key = { it }) { index ->
                    PageView(
                        document = document,
                        cache = cache,
                        index = index,
                        page = pages[index],
                        width = pageWidth,
                        store = store,
                        existing = existing[index].orEmpty(),
                        writing = writing,
                        tool = tool,
                        color = penColor,
                        widthPt = penWidth,
                        allowFinger = allowFinger,
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

/** One page at a time, swiped sideways; a page taller than the window still scrolls down. */
@Composable
private fun SingleReader(
    document: Document,
    cache: PageCache,
    pages: List<PageSize>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    zoom: Float,
    store: MarkStore,
    existing: Map<Int, List<AnnotationRow>>,
    writing: Boolean,
    tool: Tool,
    penColor: Color,
    penWidth: Float,
    allowFinger: Boolean,
    onZoomBy: (Float) -> Unit,
) {
    if (pages.isEmpty()) return

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= ExpandedWidth
        val margin = if (expanded) 28.dp else 12.dp
        val fitWidth = (maxWidth - margin * 2).coerceAtLeast(80.dp)
        val pageWidth = fitWidth * zoom
        val horizontal = rememberScrollState()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = margin),
            pageSpacing = 12.dp,
        ) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontal)
                    .pinchZoom(!writing, onZoomBy),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier
                        .width(pageWidth)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PageView(
                        document = document,
                        cache = cache,
                        index = index,
                        page = pages[index],
                        width = pageWidth,
                        store = store,
                        existing = existing[index].orEmpty(),
                        writing = writing,
                        tool = tool,
                        color = penColor,
                        widthPt = penWidth,
                        allowFinger = allowFinger,
                    )
                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}

/** Double-tap toggles the zoom, except while writing: there a tap is a dot on the page. */
private fun Modifier.pointerInputDoubleTap(enabled: Boolean, onDoubleTap: () -> Unit): Modifier =
    if (!enabled) {
        this
    } else {
        this.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleTap() }) }
    }

/**
 * Two fingers zoom the page.
 *
 * Watched on the initial pass so the scrolling list underneath does not see the gesture too:
 * otherwise a pinch both zooms and scrolls, and the page slides out from under the fingers doing
 * the pinching. A single finger is never consumed here, which is what keeps dragging on a page
 * scrolling the document.
 *
 * The factor is relative rather than absolute so a frame that arrives late cannot make the zoom
 * jump: each event multiplies what is on screen by how much the fingers moved since the last one.
 */
@Composable
private fun Modifier.pinchZoom(enabled: Boolean, onZoomBy: (Float) -> Unit): Modifier {
    val callback by rememberUpdatedState(onZoomBy)
    return if (!enabled) {
        this
    } else {
        this.pointerInput(Unit) {
            awaitEachGesture {
                var previous = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                        if (previous > 0f && distance > 0f) callback(distance / previous)
                        previous = distance
                        pressed.forEach { it.consume() }
                    } else {
                        previous = 0f
                        if (pressed.isEmpty()) break
                    }
                }
            }
        }
    }
}

/**
 * One page: its raster once rendered, the file's own annotations over it, then the marks being
 * drawn, and a spinner until the raster arrives.
 *
 * [width] is the page's width on screen, and it is the only thing a zoom changes — which is what
 * keeps the pen honest. The touch handler divides by exactly this width, so a mark is stored in
 * page coordinates whether the page is at fit-width or at three times that.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageView(
    document: Document,
    cache: PageCache,
    index: Int,
    page: PageSize,
    width: Dp,
    store: MarkStore,
    existing: List<AnnotationRow>,
    writing: Boolean,
    tool: Tool,
    color: Color,
    widthPt: Float,
    allowFinger: Boolean,
) {
    val density = LocalDensity.current.density
    val height = width * (page.height / page.width)

    // A page is drawn at the pixels the slot actually occupies, so it is sharp at 1× without
    // being decoded at print resolution. The cache caps the scale; see PageCache.
    val scale = (width.value * density) / page.width

    var bitmap by remember(index, scale) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, scale) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { cache.bitmap(index, scale) }.getOrNull()
        }
    }

    val marks = store.marks(index)
    val preview = store.preview(index)
    val handler = remember(index, tool, color, widthPt, allowFinger) {
        MarkHandler(
            tool = tool,
            color = color,
            widthPt = widthPt,
            allowFinger = allowFinger,
            onCommit = { mark -> store.add(index, mark) },
            onPreview = { mark -> store.setPreview(index, mark) },
            onErase = { x, y -> store.eraseNear(index, x, y, radius = 0.012f) },
        )
    }

    val widthPx = with(LocalDensity.current) { width.toPx() }.toInt()
    val heightPx = with(LocalDensity.current) { height.toPx() }.toInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        shape = RoundedCornerShape(3.dp),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!writing) {
                        Modifier
                    } else {
                        Modifier.pointerInteropFilter { event -> handler.onEvent(event, widthPx, heightPx) }
                    },
                ),
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

            // Under the marks, over the page: a highlight somebody else made is part of the
            // document, and what this reader draws on top of it is not.
            AnnotationOverlay(
                rows = existing,
                pageWidthPt = page.width,
                modifier = Modifier.fillMaxSize(),
            )

            MarkOverlay(
                marks = if (preview != null) marks + preview else marks,
                pageWidthPt = page.width,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The bar at the bottom of the pages: where the reader is, a way to slide there, and the zoom.
 *
 * It is one line and it floats, so the page it describes is not shortened by it. The slider is
 * the fastest way through a long document on a phone, where the thumbnail panel is a detour.
 */
@Composable
private fun PageBar(
    page: Int,
    total: Int,
    zoom: Float,
    onZoom: (Float) -> Unit,
    onSeek: (Int) -> Unit,
) {
    var dragged by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf((page - 1).toFloat()) }

    LaunchedEffect(page, total) {
        if (!dragged) position = (page - 1).toFloat()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.widthIn(max = 520.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$page / $total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = 52.dp),
            )

            Slider(
                value = position.coerceIn(0f, (total - 1).coerceAtLeast(0).toFloat()),
                onValueChange = {
                    dragged = true
                    position = it
                },
                onValueChangeFinished = {
                    dragged = false
                    onSeek(position.toInt())
                },
                valueRange = 0f..(total - 1).coerceAtLeast(0).toFloat(),
                enabled = total > 1,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )

            ZoomButton(
                enabled = zoom > 1f,
                label = stringResource(R.string.action_zoom_out),
                onClick = { onZoom((zoom / 1.5f).coerceAtLeast(1f)) },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(17.dp))
            }
            Text(
                text = "${(zoom * 100).toInt()}%",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .widthIn(min = 38.dp)
                    .clickable { onZoom(1f) },
            )
            ZoomButton(
                enabled = zoom < MaxZoom,
                label = stringResource(R.string.action_zoom_in),
                onClick = { onZoom((zoom * 1.5f).coerceAtMost(MaxZoom)) },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ZoomButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint,
        ) { content() }
    }
}

/** Ask for a page number, for when the reader knows where they want to be. */
@Composable
private fun GoToPageDialog(total: Int, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_go_to_page)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(6) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.page_number_hint, total)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { text.toIntOrNull()?.let { onGo(it - 1) } ?: onDismiss() },
                enabled = text.toIntOrNull()?.let { it in 1..total } == true,
            ) {
                Text(stringResource(R.string.action_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Write the document plus its marks to the picked location.
 *
 * The engine writes a real file first, and that file is then copied into the picked URI: a content
 * URI is a stream, not something the engine can open, so going through a file is what lets the
 * engine own the PDF writing — annotations, appearance streams and all.
 */
private fun writeMarksCopy(document: Document, store: MarkStore, context: Context, target: Uri) {
    val exported = File(context.cacheDir, "export.pdf")
    val pageMarks = store.snapshot().map { (page, marks) ->
        PageMark(
            page = page,
            marks = marks.map { mark ->
                PdfMark(
                    kind = when (mark.kind) {
                        Tool.Pen -> MarkKind.INK
                        Tool.Highlighter -> MarkKind.HIGHLIGHTER
                        Tool.Rectangle -> MarkKind.RECTANGLE
                        Tool.Arrow -> MarkKind.ARROW
                        // The eraser removes; it never becomes a mark of its own.
                        Tool.Eraser -> MarkKind.INK
                    },
                    points = mark.points.flatMap { listOf(it.x, it.y) },
                    color = mark.color.toArgb(),
                    width = mark.widthPt,
                )
            },
        )
    }
    document.saveWithMarks(exported.absolutePath, pageMarks)

    context.contentResolver.openOutputStream(target)?.use { out ->
        exported.inputStream().use { input -> input.copyTo(out, bufferSize = 1 shl 16) }
    } ?: error(context.getString(R.string.error_cannot_write))
}
