package io.github.angelky7.pdfcraft.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.ink.PenTool

/** The inks on offer, each with the name spoken by a screen reader. */
val PenColors = listOf(
    Color(0xFF14181A) to R.string.pen_black,
    Color(0xFFB3261E) to R.string.pen_red,
    Color(0xFF1A4FB4) to R.string.pen_blue,
    Color(0xFF2C6A55) to R.string.pen_green,
)

/** Nominal widths in points: a fine pen, a marker, and a highlighter-ish broad nib. */
val PenWidths = listOf(
    1.2f to R.string.pen_thin,
    2.6f to R.string.pen_medium,
    5.2f to R.string.pen_thick,
)

/**
 * The writing controls.
 *
 * Two rows: what marks the page, then what happens to the marks. It floats over the bottom of the
 * page rather than taking a strip of its own, so turning writing on does not shrink the document
 * being read — and it scrolls sideways rather than wrapping, because a narrow phone should show
 * every choice at its natural size instead of shrinking them below a fingertip.
 */
@Composable
fun InkToolbar(
    color: Color,
    onColor: (Color) -> Unit,
    width: Float,
    onWidth: (Float) -> Unit,
    tool: PenTool,
    onTool: (PenTool) -> Unit,
    allowFinger: Boolean,
    onAllowFinger: (Boolean) -> Unit,
    canUndo: Boolean,
    saving: Boolean,
    onUndo: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 10.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ToolButton(
                    selected = tool == PenTool.Ink,
                    label = stringResource(R.string.pen_tool_ink),
                    icon = { tint -> Icon(Icons.Filled.Edit, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
                    onClick = { onTool(PenTool.Ink) },
                )
                ToolButton(
                    selected = tool == PenTool.Eraser,
                    label = stringResource(R.string.pen_tool_eraser),
                    icon = null,
                    onClick = { onTool(PenTool.Eraser) },
                )

                Separator()

                PenColors.forEach { (swatch, name) ->
                    val chosen = swatch == color && tool == PenTool.Ink
                    val label = stringResource(name)
                    val ring by animateColorAsState(
                        if (chosen) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        label = "swatch",
                    )
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                onColor(swatch)
                                onTool(PenTool.Ink)
                            }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(swatch, CircleShape)
                                .border(2.dp, ring, CircleShape),
                        )
                    }
                }

                Separator()

                PenWidths.forEach { (size, name) ->
                    val chosen = size == width
                    val label = stringResource(name)
                    val background by animateColorAsState(
                        if (chosen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "width",
                    )
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(background, CircleShape)
                            .clickable {
                                onWidth(size)
                                onTool(PenTool.Ink)
                            }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size((size * 2.6f).dp.coerceIn(3.dp, 14.dp))
                                .background(if (tool == PenTool.Eraser) MaterialTheme.colorScheme.outline else color, CircleShape),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.TouchApp,
                        contentDescription = null,
                        tint = if (allowFinger) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = fingerLabel,
                        fontSize = 12.sp,
                        fontWeight = if (allowFinger) FontWeight.Medium else FontWeight.Normal,
                        color = if (allowFinger) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Spacer(Modifier.weight(1f))

                ActionButton(
                    enabled = canUndo,
                    label = stringResource(R.string.action_undo),
                    onClick = onUndo,
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(19.dp))
                }

                Spacer(Modifier.width(4.dp))

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
    icon: (@Composable (Color) -> Unit)?,
    onClick: () -> Unit,
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

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            icon(tint)
        } else {
            EraserGlyph(tint)
        }
        Text(text = label, fontSize = 12.sp, color = tint, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
    }
}

/** An eraser: a tilted block with a lighter tip. Drawn rather than shipped as an asset. */
@Composable
private fun EraserGlyph(tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.30f, h * 0.86f)
            lineTo(w * 0.08f, h * 0.62f)
            lineTo(w * 0.60f, h * 0.14f)
            lineTo(w * 0.90f, h * 0.44f)
            close()
        }
        drawPath(body, tint, style = Stroke(width = w * 0.09f, cap = StrokeCap.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.22f, h * 0.72f),
            end = Offset(w * 0.50f, h * 0.42f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round,
        )
    }
}

/** A short vertical rule between groups of controls. */
@Composable
private fun Separator() {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Undo and save: the two things that happen to the ink rather than to the pen. */
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
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint,
        ) { content() }
    }
}
