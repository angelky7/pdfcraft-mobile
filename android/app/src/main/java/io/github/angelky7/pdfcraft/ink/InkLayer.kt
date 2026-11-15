package io.github.angelky7.pdfcraft.ink

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.abs

/** One sample from the pen: where it was, and how hard it pressed. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float)

/**
 * A finished stroke, in coordinates relative to the page (`0..1` across and down).
 *
 * Relative rather than pixels because the same stroke has to survive a rotation, a split-screen
 * resize and a zoom, each of which changes what a pixel means. The width stays in points for the
 * same reason: it is a property of the ink, not of the screen showing it.
 */
data class Stroke(
    val points: List<InkPoint>,
    val color: Color,
    val widthPt: Float,
)

/**
 * Ink drawn on the open document, per page.
 *
 * The engine holds the same strokes as real PDF annotations — that is what a save writes — but the
 * page on screen is drawn from the file as it was opened, so this is what the reader actually
 * sees. Keeping the model here, rather than inside each page's composable, is what lets a stroke
 * survive its page being scrolled out of the lazy list and back.
 */
class InkStore {
    private val pages = mutableStateMapOf<Int, SnapshotStateList<Stroke>>()

    /** Undo across pages: each entry is the page a stroke went onto. */
    private val history = ArrayDeque<Pair<Int, Stroke>>()

    fun strokes(page: Int): List<Stroke> = pages[page] ?: emptyList()

    fun add(page: Int, stroke: Stroke) {
        pages.getOrPut(page) { mutableStateListOf() }.add(stroke)
        history.addLast(page to stroke)
    }

    /**
     * Rub out the most recent stroke within [radius] (in page-relative units) of the point.
     * Returns true when something was erased.
     */
    fun eraseNear(page: Int, x: Float, y: Float, radius: Float): Boolean {
        val list = pages[page] ?: return false
        val hit = list.lastOrNull { stroke ->
            stroke.points.any { abs(it.x - x) <= radius && abs(it.y - y) <= radius }
        } ?: return false

        list.remove(hit)
        history.remove(page to hit)
        return true
    }

    /** Take back the last stroke wherever it was. Null when there is nothing left. */
    fun undo(): Pair<Int, Stroke>? {
        val last = history.removeLastOrNull() ?: return null
        pages[last.first]?.remove(last.second)
        return last
    }

    fun isEmpty(): Boolean = history.isEmpty()

    /**
     * Every page that has ink, for the engine to write.
     *
     * Pages with nothing on them are left out rather than sent empty: a save that mentions a page
     * is a save that opens it, and there is no reason to open a thousand of them.
     */
    fun snapshot(): Map<Int, List<Stroke>> =
        pages.mapValues { (_, strokes) -> strokes.toList() }.filterValues { it.isNotEmpty() }

    fun clear() {
        pages.clear()
        history.clear()
    }
}

/**
 * Turns touch events on one page into strokes.
 *
 * The rule the whole feature rests on: a **stylus writes, a finger does not touch anything here**.
 * Returning `false` for a finger lets the event carry on to the scrolling list underneath, so
 * the same surface is a page to write on and a list to swipe. While a stylus is down, finger
 * events are swallowed instead — that is palm rejection, and without it the hand resting on the
 * glass scrolls the page out from under the pen.
 *
 * Everything here reads the raw `MotionEvent` rather than Compose's pointer abstraction because
 * only the raw event carries pressure, tilt and the stylus's side button.
 */
class PenHandler(
    private val color: Color,
    private val widthPt: Float,
    private val onStroke: (Stroke) -> Unit,
    private val onErase: (x: Float, y: Float) -> Unit,
) {
    private var active: MutableList<InkPoint>? = null
    private var eraseMode = false

    /** True while a stroke is being drawn, which is when fingers must be ignored. */
    val drawing: Boolean get() = active != null

    fun onEvent(event: MotionEvent, widthPx: Int, heightPx: Int): Boolean {
        if (widthPx <= 0 || heightPx <= 0) return false

        val tool = event.getToolType(0)
        val stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER

        // Input from a finger: swallowed while the pen is down, passed on otherwise.
        if (!stylus) return drawing

        val x = (event.x / widthPx).coerceIn(0f, 1f)
        val y = (event.y / heightPx).coerceIn(0f, 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The stylus's side button is the eraser, as it is in every note-taking app.
                eraseMode = tool == MotionEvent.TOOL_TYPE_ERASER ||
                    event.isButtonPressed(MotionEvent.BUTTON_STYLUS_PRIMARY)
                if (eraseMode) {
                    onErase(x, y)
                } else {
                    active = mutableListOf(sample(event, x, y))
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (eraseMode) {
                    onErase(x, y)
                    return true
                }
                val points = active ?: return true
                // A second finger means the reader wants to pinch: drop the stroke rather than
                // leave a stray line under the zoom.
                if (event.pointerCount > 1) {
                    active = null
                    return true
                }
                points.add(sample(event, x, y))
                return true
            }

            MotionEvent.ACTION_UP -> {
                val points = active
                active = null
                eraseMode = false
                // One point is a tap, which ink cannot express; two are a stroke.
                if (points != null && points.size >= 2) {
                    onStroke(Stroke(points = points.toList(), color = color, widthPt = widthPt))
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                active = null
                eraseMode = false
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Another finger landed while writing: treat it as a pinch and drop the stroke.
                active = null
                return true
            }
        }
        return true
    }

    private fun sample(event: MotionEvent, x: Float, y: Float): InkPoint =
        // Pressure is device-dependent: some report 0..1, some past 1. Clamping keeps a device
        // that reports 2.5 from drawing a slab.
        InkPoint(x = x, y = y, pressure = event.getPressure(0).coerceIn(0.05f, 1.5f))
}

/**
 * Draws the strokes over the page raster.
 *
 * Each segment is a round-capped line whose width comes from the pressure at its two ends, which
 * is what makes the line thicken and thin as the pen is pressed. Drawing it per segment rather
 * than as one filled outline keeps this simple and fast enough for a 120 Hz pen: a few hundred
 * lines per frame is nothing for the GPU.
 */
@Composable
fun InkOverlay(strokes: List<Stroke>, pageWidthPt: Float, modifier: Modifier = Modifier) {
    if (strokes.isEmpty() || pageWidthPt <= 0f) return

    Canvas(modifier) {
        // Points are in PDF points; the canvas is in pixels.
        val scale = size.width / pageWidthPt
        for (stroke in strokes) {
            val points = stroke.points
            if (points.size < 2) continue
            for (i in 1 until points.size) {
                val from = points[i - 1]
                val to = points[i]
                val fromWidth = stroke.widthPt * widthFactor(from.pressure) * scale
                val toWidth = stroke.widthPt * widthFactor(to.pressure) * scale
                drawLine(
                    color = stroke.color,
                    start = Offset(from.x * size.width, from.y * size.height),
                    end = Offset(to.x * size.width, to.y * size.height),
                    strokeWidth = ((fromWidth + toWidth) / 2f).coerceAtLeast(0.7f),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * How much of the nominal width a sample at this pressure gets: a light touch is 45% of it, a
 * firm one is the full width. The floor keeps the faintest line visible rather than a shimmer.
 */
private fun widthFactor(pressure: Float): Float = 0.45f + 0.55f * pressure.coerceIn(0f, 1f)
