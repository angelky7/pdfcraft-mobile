package io.github.angelky7.pdfcraft.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.ink.Tool

/** The inks on offer, each with the name spoken by a screen reader. */
val PenColors = listOf(
    Color(0xFF14181A) to R.string.pen_black,
    Color(0xFFB3261E) to R.string.pen_red,
    Color(0xFF1A4FB4) to R.string.pen_blue,
    Color(0xFF2C6A55) to R.string.pen_green,
)

/** Nominal widths in points: a fine pen, a marker, and a broad nib. */
val PenWidths = listOf(
    1.2f to R.string.pen_thin,
    2.6f to R.string.pen_medium,
    5.2f to R.string.pen_thick,
)

/** The tools, in the order they appear: what draws, then what removes. */
private val Tools = listOf(
    Tool.Pen to R.string.pen_tool_ink,
    Tool.Highlighter to R.string.pen_tool_highlighter,
    Tool.Rectangle to R.string.pen_tool_rectangle,
    Tool.Arrow to R.string.pen_tool_arrow,
    Tool.Eraser to R.string.pen_tool_eraser,
)

/**
 * The drawing controls.
 *
 * Two rows: what draws, then how it draws. It floats over the bottom of the page rather than
 * taking a strip of its own, so turning drawing on does not shrink the document — and it scrolls
 * sideways rather than shrinking, because a control smaller than a fingertip is not a control.
 */
@Composable
fun InkToolbar(
    tool: Tool,
    onTool: (Tool) -> Unit,
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    allowFinger: Boolean,
    onAllowFinger: (Boolean) -> Unit,
    canUndo: Boolean,
    saving: Boolean,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 560.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Tools.forEach { (candidate, name) ->
                    val label = stringResource(name)
                    ToolButton(
                        selected = tool == candidate,
                        label = label,
                        onClick = { onTool(candidate) },
                    ) {
                        ToolGlyph(candidate, it)
                    }
                }

                Separator()

                PenColors.forEach { (swatch, name) ->
                    val chosen = swatch == color
                    val label = stringResource(name)
                    val ring by animateColorAsState(
                        if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        label = "swatch",
                    )
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onColor(swatch) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(21.dp)
                                .background(swatch, CircleShape)
                                .border(2.dp, ring, CircleShape),
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                PenWidths.forEach { (size, name) ->
                    val chosen = kotlin.math.abs(size - width) < 0.01f
                    val label = stringResource(name)
                    val background by animateColorAsState(
                        if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "width",
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(background, CircleShape)
                            .clickable { onWidth(size) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((size * 2.4f).dp.coerceIn(3.dp, 13.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                        )
                    }
                }

                Separator()

                val fingerLabel = stringResource(R.string.pen_finger_write)
                val fingerBackground by animateColorAsState(
                    if (allowFinger) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    label = "finger",
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(fingerBackground)
                        .clickable { onAllowFinger(!allowFinger) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .semantics { contentDescription = fingerLabel },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val tint = if (allowFinger) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Icon(
                        Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(text = fingerLabel, fontSize = 11.sp, color = tint)
                }

                Spacer(Modifier.weight(1f))

                ActionButton(
                    enabled = canUndo,
                    label = stringResource(R.string.action_undo),
                    onClick = onUndo,
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(19.dp))
                }

                Spacer(Modifier.width(2.dp))

                ActionButton(
                    enabled = !saving,
                    label = stringResource(R.string.action_save),
                    highlighted = true,
                    onClick = onSave,
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

/** A tool in the first row: lit when it is the one in use. */
@Composable
private fun ToolButton(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    glyph: @Composable (Color) -> Unit,
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "tool",
    )
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        glyph(tint)
    }
}

/**
 * The tool marks, drawn rather than shipped as assets: five small shapes, so a new tool costs a
 * dozen lines instead of a new set of PNGs at five densities.
 */
@Composable
fun ToolGlyph(tool: Tool, tint: Color, glyphSize: androidx.compose.ui.unit.Dp = 18.dp) {
    Canvas(Modifier.size(glyphSize)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.13f

        when (tool) {
            Tool.Pen -> {
                // A nib: the shaft, then the point.
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.74f, h * 0.26f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.30f, h * 0.86f), stroke * 0.8f, StrokeCap.Round)
            }

            Tool.Highlighter -> {
                // A broad band, with the tip under it.
                drawLine(tint, Offset(w * 0.16f, h * 0.42f), Offset(w * 0.84f, h * 0.42f), h * 0.30f, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.30f, h * 0.78f), Offset(w * 0.70f, h * 0.78f), stroke * 0.8f, StrokeCap.Round)
            }

            Tool.Rectangle -> drawRect(
                color = tint,
                topLeft = Offset(w * 0.16f, h * 0.22f),
                size = Size(w * 0.68f, h * 0.56f),
                style = Stroke(width = stroke),
            )

            Tool.Arrow -> {
                drawLine(tint, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.78f, h * 0.24f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.24f), Offset(w * 0.50f, h * 0.28f), stroke, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.24f), Offset(w * 0.74f, h * 0.52f), stroke, StrokeCap.Round)
            }

            Tool.Eraser -> {
                val body = Path().apply {
                    moveTo(w * 0.30f, h * 0.84f)
                    lineTo(w * 0.10f, h * 0.60f)
                    lineTo(w * 0.60f, h * 0.14f)
                    lineTo(w * 0.88f, h * 0.42f)
                    close()
                }
                drawPath(body, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawLine(tint, Offset(w * 0.22f, h * 0.70f), Offset(w * 0.50f, h * 0.40f), stroke, StrokeCap.Round)
            }
        }
    }
}

/** A short vertical rule between groups of controls. */
@Composable
private fun Separator() {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Undo and save: the two things that happen to the marks rather than to the pen. */
@Composable
private fun ActionButton(
    enabled: Boolean,
    label: String,
    highlighted: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        highlighted -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}
