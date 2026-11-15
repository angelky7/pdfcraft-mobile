package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.ink.Tool

/** The four shelves of the tools screen: everything, then what each one is for. */
private val GroupLabels = listOf(
    R.string.tools_group_all,
    R.string.tools_group_read,
    R.string.tools_group_edit,
    R.string.tools_group_convert,
)

/** One tool on the shelf. [group] is which shelf it belongs to: 1 read, 2 edit, 3 convert. */
private class Entry(
    val group: Int,
    val title: Int,
    val body: Int,
    val accent: Accent,
    val icon: ImageVector? = null,
    val tool: Tool? = null,
    val intent: OpenIntent? = null,
    val imports: Boolean = false,
)

/**
 * Everything the app can do, in one place.
 *
 * Each card is a verb, not a feature list: it names what will happen, says in one line what that
 * buys, and — because the reader needs a document first — either opens the one already on the
 * shelf or asks for one, then arrives with the tool in hand.
 */
private val Entries = listOf(
    Entry(1, R.string.tool_read_title, R.string.tool_read_body, Accent.Blue, icon = Icons.Filled.Description, intent = OpenIntent()),
    Entry(1, R.string.tool_search_title, R.string.tool_search_body, Accent.Cyan, icon = Icons.Filled.Search, intent = OpenIntent(panel = NavTab.Search)),
    Entry(1, R.string.tool_outline_title, R.string.tool_outline_body, Accent.Amber, icon = Icons.Filled.FormatListBulleted, intent = OpenIntent(panel = NavTab.Outline)),
    Entry(1, R.string.tool_comments_title, R.string.tool_comments_body, Accent.Violet, icon = Icons.Outlined.Comment, intent = OpenIntent(panel = NavTab.Comments)),
    Entry(1, R.string.tool_info_title, R.string.tool_info_body, Accent.Green, icon = Icons.Filled.Info, intent = OpenIntent(info = true)),
    Entry(2, R.string.tool_pen_title, R.string.tool_pen_body, Accent.Pink, tool = Tool.Pen, intent = OpenIntent(tool = Tool.Pen)),
    Entry(2, R.string.tool_highlighter_title, R.string.tool_highlighter_body, Accent.Amber, tool = Tool.Highlighter, intent = OpenIntent(tool = Tool.Highlighter)),
    Entry(2, R.string.tool_rect_title, R.string.tool_rect_body, Accent.Blue, tool = Tool.Rectangle, intent = OpenIntent(tool = Tool.Rectangle)),
    Entry(2, R.string.tool_arrow_title, R.string.tool_arrow_body, Accent.Violet, tool = Tool.Arrow, intent = OpenIntent(tool = Tool.Arrow)),
    Entry(2, R.string.tool_eraser_title, R.string.tool_eraser_body, Accent.Red, tool = Tool.Eraser, intent = OpenIntent(tool = Tool.Eraser)),
    Entry(3, R.string.tool_save_title, R.string.tool_save_body, Accent.Green, icon = Icons.Filled.Save, intent = OpenIntent(save = true)),
    Entry(3, R.string.tool_open_title, R.string.tool_open_body, Accent.Cyan, icon = Icons.Filled.Add, imports = true),
)

@Composable
fun ToolsScreen(
    expanded: Boolean,
    onTool: (OpenIntent) -> Unit,
    onImport: () -> Unit,
) {
    var group by remember { mutableIntStateOf(0) }
    val shown = if (group == 0) Entries else Entries.filter { it.group == group }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columnWidth = if (maxWidth > 900.dp) 900.dp else maxWidth

        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (expanded) 3 else 2),
                modifier = Modifier
                    .fillMaxHeight()
                    .width(columnWidth)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        ScreenTitle(stringResource(R.string.tools_title))
                        Spacer(Modifier.height(10.dp))
                        TabRow(
                            selectedTabIndex = group,
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary,
                            divider = { Hairline() },
                        ) {
                            GroupLabels.forEachIndexed { index, label ->
                                Tab(
                                    selected = group == index,
                                    onClick = { group = index },
                                    text = {
                                        Text(
                                            text = stringResource(label),
                                            fontSize = 13.sp,
                                            fontWeight = if (group == index) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    },
                                    selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                items(shown, key = { it.title }) { entry ->
                    ToolCard(
                        accent = entry.accent,
                        title = stringResource(entry.title),
                        body = stringResource(entry.body),
                        onClick = {
                            if (entry.imports) {
                                onImport()
                            } else {
                                onTool(entry.intent ?: OpenIntent())
                            }
                        },
                        icon = entry.icon,
                        tool = entry.tool,
                    )
                }
            }
        }
    }
}
