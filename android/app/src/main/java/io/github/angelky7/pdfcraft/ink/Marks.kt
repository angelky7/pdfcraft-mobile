package io.github.angelky7.pdfcraft.ink

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** What the stylus draws with. */
enum class Tool {
    Pen,
    Highlighter,
    Rectangle,
    Arrow,
    Eraser,
}

/** One sample from the pen: where it was, and how hard it pressed. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float)

/**
 * One thing drawn on a page, in coordinates relative to the page (`0..1` across and down).
 *
 * Relative rather than pixels because the same mark has to survive a rotation, a window resize
 * and a change of zoom, each of which changes what a pixel means. The width stays in points for
 * the same reason: it is a property of the ink, not of the screen showing it.
 *
 * A [Tool.Rectangle] and [Tool.Arrow] carry exactly two points — the corners that were dragged
 * between — because that is what they are. Strokes carry every sample.
 */
data class Mark(
    val kind: Tool,
    val points: List<InkPoint>,
    val color: Color,
    val widthPt: Float,
)

/**
 * Marks drawn on the open document, per page.
 *
 * The engine holds the same marks as real PDF annotations — that is what a save writes — but the
 * page on screen is drawn from the file as it was opened, so this is what the reader actually
 * sees. Keeping the model here, rather than inside each page's composable, is what lets a mark
 * survive its page being scrolled out of the lazy list and back.
 *
 * [preview] is the mark under the pen right now: without it a stroke only appeared when the pen
 * lifted, which reads as a lag even when the rendering is instant.
 */
class MarkStore {
    private val pages = mutableStateMapOf<Int, SnapshotStateList<Mark>>()
    private val previews = mutableStateMapOf<Int, Mark>()

    /** Undo across pages: each entry is the page a mark went onto. */
    private val history = ArrayDeque<Pair<Int, Mark>>()

    fun marks(page: Int): List<Mark> = pages[page] ?: emptyList()

    fun preview(page: Int): Mark? = previews[page]

    fun setPreview(page: Int, mark: Mark?) {
        if (mark == null) {
            previews.remove(page)
        } else {
            previews[page] = mark
        }
    }

    fun add(page: Int, mark: Mark) {
        pages.getOrPut(page) { mutableStateListOf() }.add(mark)
        history.addLast(page to mark)
    }

    /**
     * Rub out the most recent mark within [radius] (in page-relative units) of the point.
     * Returns true when something was erased.
     */
    fun eraseNear(page: Int, x: Float, y: Float, radius: Float): Boolean {
        val list = pages[page] ?: return false
        val hit = list.lastOrNull { mark ->
            mark.points.any { hypot(it.x - x, it.y - y) <= radius } ||
                // A rectangle or an arrow is grabbed anywhere along its outline, not only at the
                // two corners the drag started and ended at.
                (mark.points.size == 2 && nearSegment(mark.points[0], mark.points[1], x, y, radius))
        } ?: return false

        list.remove(hit)
        history.remove(page to hit)
        return true
    }

    /** Take back the last mark wherever it was. Null when there is nothing left. */
    fun undo(): Pair<Int, Mark>? {
        val last = history.removeLastOrNull() ?: return null
        pages[last.first]?.remove(last.second)
        return last
    }

    fun isEmpty(): Boolean = history.isEmpty()

    fun count(): Int = history.size

    /**
     * Every page that has a mark, for the engine to write.
     *
     * Pages with nothing on them are left out rather than sent empty: a save that mentions a page
     * is a save that opens it, and there is no reason to open a thousand of them.
     */
    fun snapshot(): Map<Int, List<Mark>> =
        pages.mapValues { (_, marks) -> marks.toList() }.filterValues { it.isNotEmpty() }

    fun clear() {
        pages.clear()
        previews.clear()
        history.clear()
    }
}

/** Whether a point is within [radius] of the segment between two page-relative points. */
private fun nearSegment(a: InkPoint, b: InkPoint, x: Float, y: Float, radius: Float): Boolean {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared < 1e-9f) return hypot(x - a.x, y - a.y) <= radius
    val t = (((x - a.x) * dx + (y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
    return hypot(x - (a.x + t * dx), y - (a.y + t * dy)) <= radius
}

/**
 * Turns touch events on one page into marks.
 *
 * The rule this rests on is that the app decides, not the system: Android reports which tool a
 * touch came from, and [allowFinger] says what each tool is allowed to do here. With a stylus in
 * hand that is "stylus writes, finger scrolls" — returning `false` for a finger lets the event
 * carry on to the scrolling list underneath. Without one it is "anything writes", because a
 * finger is all some tablets have and refusing to write with it would make the feature useless.
 *
 * While a mark is being drawn, finger events are swallowed instead — that is palm rejection, and
 * without it the hand resting on the glass scrolls the page out from under the pen.
 *
 * Everything here reads the raw `MotionEvent` rather than Compose's pointer abstraction because
 * only the raw event carries pressure, tilt and the stylus's side button.
 */
class MarkHandler(
    private val tool: Tool,
    private val color: Color,
    private val widthPt: Float,
    private val allowFinger: Boolean,
    private val onCommit: (Mark) -> Unit,
    private val onPreview: (Mark?) -> Unit,
    private val onErase: (x: Float, y: Float) -> Unit,
) {
    private var points: MutableList<InkPoint>? = null
    private var start: InkPoint? = null
    private var erasing = false

    /** True while a mark is being drawn, which is when fingers must be ignored. */
    val drawing: Boolean get() = points != null || start != null

    fun onEvent(event: MotionEvent, widthPx: Int, heightPx: Int): Boolean {
        if (widthPx <= 0 || heightPx <= 0) return false

        val kind = event.getToolType(0)
        val stylus = kind == MotionEvent.TOOL_TYPE_STYLUS || kind == MotionEvent.TOOL_TYPE_ERASER

        // A finger, when the pen is the only thing allowed to draw, belongs to the scrolling list.
        // Consuming it while drawing is what keeps a resting palm from scrolling the page.
        if (!stylus && !allowFinger) return drawing

        // Two fingers mean the reader wants to zoom, not draw. The mark is dropped and the event
        // released, so the pinch handler outside this page can take over.
        if (!stylus && event.pointerCount > 1) {
            reset()
            return false
        }

        val x = (event.x / widthPx).coerceIn(0f, 1f)
        val y = (event.y / heightPx).coerceIn(0f, 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The eraser is whichever of: the chosen tool, the stylus's tail, or its side
                // button held down — every note-taking app's convention.
                erasing = tool == Tool.Eraser ||
                    kind == MotionEvent.TOOL_TYPE_ERASER ||
                    event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)

                if (erasing) {
                    onErase(x, y)
                } else {
                    val sample = sample(event, x, y, stylus)
                    start = sample
                    if (isFreehand()) {
                        points = mutableListOf(sample)
                        onPreview(Mark(tool, listOf(sample, sample), color, widthPt))
                    } else {
                        onPreview(Mark(tool, listOf(sample, sample), color, widthPt))
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (erasing) {
                    onErase(x, y)
                    return true
                }
                val origin = start ?: return true
                if (event.pointerCount > 1) {
                    reset()
                    return false
                }
                val sample = sample(event, x, y, stylus)

                if (isFreehand()) {
                    val drawn = points ?: return true
                    // A sample closer than a fifth of a point adds nothing but work: the pen
                    // reports at 240 Hz, which is four samples inside the width of the line.
                    if (hypot(sample.x - drawn.last().x, sample.y - drawn.last().y) > 0.0008f) {
                        drawn.add(sample)
                    }
                    onPreview(Mark(tool, drawn.toList(), color, widthPt))
                } else {
                    onPreview(Mark(tool, listOf(origin, sample), color, widthPt))
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                commit(x, y, stylus)
                reset()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                reset()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Another finger landed while drawing: that is a pinch, so the mark goes.
                reset()
                return false
            }
        }
        return true
    }

    private fun commit(x: Float, y: Float, stylus: Boolean) {
        val end = sample(null, x, y, stylus)

        if (isFreehand()) {
            val drawn = points ?: return
            // A tap is a dot, which ink cannot express as one point: it becomes a mark of zero
            // length, which every reader draws as a dot.
            val sized = if (drawn.size == 1) listOf(drawn[0], drawn[0]) else drawn
            onCommit(Mark(tool, sized.toList(), color, widthPt))
        } else {
            val origin = start ?: return
            // Under a few points it is a stray tap, not a shape somebody meant to draw.
            if (hypot(end.x - origin.x, end.y - origin.y) * 1000f < 3f) return
            onCommit(Mark(tool, listOf(origin, end), color, widthPt))
        }
    }

    private fun reset() {
        points = null
        start = null
        erasing = false
        onPreview(null)
    }

    private fun isFreehand(): Boolean = tool == Tool.Pen || tool == Tool.Highlighter

    /**
     * Pressure is device-dependent: some report 0..1, some past 1, and a finger usually reports a
     * constant. Clamping keeps a device that reports 2.5 from drawing a slab, and a finger is
     * treated as a full-width press rather than as the lightest possible touch.
     */
    private fun sample(event: MotionEvent?, x: Float, y: Float, stylus: Boolean): InkPoint {
        val pressure = if (event != null && stylus) {
            event.getPressure(0).coerceIn(0.05f, 1.5f)
        } else {
            1f
        }
        return InkPoint(x = x, y = y, pressure = pressure)
    }
}

/**
 * Draws the marks over the page raster.
 *
 * Each stroke segment is a round-capped line whose width comes from the pressure at its two ends,
 * which is what makes the line thicken and thin as the pen is pressed. Drawing it per segment
 * rather than as one filled outline keeps this simple and fast enough for a 120 Hz pen: a few
 * hundred lines per frame is nothing for the GPU.
 */
@Composable
fun MarkOverlay(marks: List<Mark>, pageWidthPt: Float, modifier: Modifier = Modifier) {
    if (marks.isEmpty() || pageWidthPt <= 0f) return

    Canvas(modifier) {
        // Points are PDF points; the canvas is in pixels. The conversion is the page's on-screen
        // width over its width in points — not the display density, which knows nothing about
        // the zoom the reader has set.
        val scale = size.width / pageWidthPt
        for (mark in marks) {
            when (mark.kind) {
                Tool.Pen -> drawFreehand(mark, unit = scale, highlighter = false)
                Tool.Highlighter -> drawFreehand(mark, unit = scale, highlighter = true)
                Tool.Rectangle -> drawBox(mark, unit = scale)
                Tool.Arrow -> drawArrow(mark, unit = scale)
                Tool.Eraser -> Unit
            }
        }
    }
}

private fun DrawScope.drawFreehand(mark: Mark, unit: Float, highlighter: Boolean) {
    val points = mark.points
    if (points.size < 2) return

    // One highlighter stroke is one path: drawing it as overlapping round segments would show a
    // darker band wherever two of them met, which is the one thing a marker must not do.
    if (highlighter) {
        val path = Path()
        path.moveTo(points[0].x * size.width, points[0].y * size.height)
        for (i in 1 until points.size) {
            path.lineTo(points[i].x * size.width, points[i].y * size.height)
        }
        drawPath(
            path = path,
            color = mark.color.copy(alpha = mark.color.alpha * 0.38f),
            style = Stroke(width = mark.widthPt * 3f * unit, cap = StrokeCap.Round),
        )
        return
    }

    for (i in 1 until points.size) {
        val from = points[i - 1]
        val to = points[i]
        val fromWidth = mark.widthPt * widthFactor(from.pressure) * unit
        val toWidth = mark.widthPt * widthFactor(to.pressure) * unit
        drawLine(
            color = mark.color,
            start = Offset(from.x * size.width, from.y * size.height),
            end = Offset(to.x * size.width, to.y * size.height),
            strokeWidth = ((fromWidth + toWidth) / 2f).coerceAtLeast(0.7f),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBox(mark: Mark, unit: Float) {
    if (mark.points.size < 2) return
    val a = mark.points[0]
    val b = mark.points[1]
    val left = minOf(a.x, b.x) * size.width
    val top = minOf(a.y, b.y) * size.height
    val width = kotlin.math.abs(b.x - a.x) * size.width
    val height = kotlin.math.abs(b.y - a.y) * size.height
    if (width < 1f || height < 1f) return

    drawRect(
        color = mark.color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = mark.widthPt * unit),
    )
}

private fun DrawScope.drawArrow(mark: Mark, unit: Float) {
    if (mark.points.size < 2) return
    val from = Offset(mark.points[0].x * size.width, mark.points[0].y * size.height)
    val to = Offset(mark.points[1].x * size.width, mark.points[1].y * size.height)
    val stroke = mark.widthPt * unit

    drawLine(color = mark.color, start = from, end = to, strokeWidth = stroke, cap = StrokeCap.Round)

    // The head: two short strokes back from the tip, at a fixed angle, sized by the line's width
    // so a thick arrow does not get a hairline head.
    val angle = atan2(to.y - from.y, to.x - from.x)
    val length = (stroke * 4.5f).coerceIn(8f, 40f)
    val spread = 0.42f
    listOf(angle + spread, angle - spread).forEach { arm ->
        drawLine(
            color = mark.color,
            start = to,
            end = Offset(to.x - cos(arm) * length, to.y - sin(arm) * length),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * How much of the nominal width a sample at this pressure gets: a light touch is 55% of it, a
 * firm one is the full width. The floor keeps the faintest line visible rather than a shimmer —
 * a line that thins to a hair reads as a rendering fault rather than as handwriting.
 */
private fun widthFactor(pressure: Float): Float = 0.55f + 0.45f * pressure.coerceIn(0f, 1f)
