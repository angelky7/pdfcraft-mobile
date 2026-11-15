package io.github.angelky7.pdfcraft.ink

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import uniffi.pdfcraft_mobile.AnnotationRow

/**
 * Draws the annotations the file already carries.
 *
 * Without this the app showed only what it had written itself: a document highlighted in Acrobat
 * opened looking untouched, which reads as the highlights having been lost. The engine does not
 * bake annotation appearances into the page raster, so they are drawn here from the geometry the
 * reader was told about — normalised to the page, so they stay on the words at any zoom.
 *
 * Only what can be drawn faithfully is drawn. A stamp or a signature is an image the engine has
 * already composed into the page, and re-drawing it here would double it; a pop-up note's icon is
 * drawn because the raster has no icon to show.
 */
@Composable
fun AnnotationOverlay(
    rows: List<AnnotationRow>,
    pageWidthPt: Float,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty() || pageWidthPt <= 0f) return

    Canvas(modifier) {
        val unit = size.width / pageWidthPt
        for (row in rows) {
            val color = Color(row.color)
            when (row.subtype) {
                "Highlight" -> {
                    // A highlight is a wash over the words: opaque enough to find, light enough to
                    // read through. Drawn as one polygon per quad, which is what a quad is.
                    quads(row).forEach { corners ->
                        drawPath(fill(corners), color.copy(alpha = 0.32f))
                    }
                }

                "Underline", "Squiggly" ->
                    quads(row).forEach { corners -> underline(corners, color, unit, offset = 0f) }

                "StrikeOut" ->
                    quads(row).forEach { corners -> underline(corners, color, unit, offset = 0.5f) }

                "Square", "Rectangle" -> {
                    val rect = row.rect
                    if (rect.size == 4) {
                        val left = minOf(rect[0], rect[2]) * size.width
                        val top = minOf(rect[1], rect[3]) * size.height
                        val width = kotlin.math.abs(rect[2] - rect[0]) * size.width
                        val height = kotlin.math.abs(rect[3] - rect[1]) * size.height
                        if (width > 1f && height > 1f) {
                            drawRect(
                                color = color,
                                topLeft = Offset(left, top),
                                size = Size(width, height),
                                style = Stroke(width = 1.5f * unit),
                            )
                        }
                    }
                }

                "Circle" -> {
                    val rect = row.rect
                    if (rect.size == 4) {
                        val width = kotlin.math.abs(rect[2] - rect[0]) * size.width
                        val height = kotlin.math.abs(rect[3] - rect[1]) * size.height
                        if (width > 1f && height > 1f) {
                            drawOval(
                                color = color,
                                topLeft = Offset(minOf(rect[0], rect[2]) * size.width, minOf(rect[1], rect[3]) * size.height),
                                size = Size(width, height),
                                style = Stroke(width = 1.5f * unit),
                            )
                        }
                    }
                }

                "Line" -> {
                    val points = row.points
                    if (points.size >= 4) {
                        drawLine(
                            color = color,
                            start = Offset(points[0] * size.width, points[1] * size.height),
                            end = Offset(points[2] * size.width, points[3] * size.height),
                            strokeWidth = 1.5f * unit,
                        )
                    }
                }

                "Text", "FreeText" -> {
                    // A sticky note's icon, or a text box's frame: at this size there is nothing
                    // in it to read, so it is drawn as a marker that something is there.
                    val rect = row.rect
                    if (rect.size == 4) {
                        val left = minOf(rect[0], rect[2]) * size.width
                        val top = minOf(rect[1], rect[3]) * size.height
                        val width = (kotlin.math.abs(rect[2] - rect[0]) * size.width).coerceAtLeast(14f * unit)
                        val height = (kotlin.math.abs(rect[3] - rect[1]) * size.height).coerceAtLeast(14f * unit)
                        drawRoundRect(
                            color = color.copy(alpha = 0.22f),
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * unit),
                        )
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * unit),
                            style = Stroke(width = 1.2f * unit),
                        )
                    }
                }

                // Stamps, signatures, ink and everything else the engine composes into the page,
                // or that carries no geometry the reader can use.
                else -> Unit
            }
        }
    }
}

/** The quads of one annotation, each as four screen points, skipping anything malformed. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.quads(row: AnnotationRow): List<List<Offset>> =
    row.quads.chunked(8).filter { it.size == 8 }.map { q ->
        listOf(
            Offset(q[0] * size.width, q[1] * size.height),
            Offset(q[2] * size.width, q[3] * size.height),
            Offset(q[6] * size.width, q[7] * size.height),
            Offset(q[4] * size.width, q[5] * size.height),
        )
    }

/** A quad as a closed path: top-left, top-right, bottom-right, bottom-left. */
private fun fill(corners: List<Offset>): Path = Path().apply {
    moveTo(corners[0].x, corners[0].y)
    lineTo(corners[1].x, corners[1].y)
    lineTo(corners[2].x, corners[2].y)
    lineTo(corners[3].x, corners[3].y)
    close()
}

/** A line across a quad, at [offset] of the way down it: the bottom edge for an underline, the
 * middle for a strike-through. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.underline(
    corners: List<Offset>,
    color: Color,
    unit: Float,
    offset: Float,
) {
    val left = corners[0]
    val right = corners[1]
    val bottomLeft = corners[3]
    val y = left.y + (bottomLeft.y - left.y) * (1f - offset)
    drawLine(
        color = color,
        start = Offset(left.x, y),
        end = Offset(right.x, y),
        strokeWidth = 1.5f * unit,
    )
}
