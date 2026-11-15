package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.RecentDoc
import io.github.angelky7.pdfcraft.ink.Tool

/** How wide the content column is allowed to get on a tablet before it just centres. */
private val MaxContentWidth = 900.dp

/**
 * Where the app starts.
 *
 * The shape of the screen is the answer to "what now?": a line saying who is back, a shelf of the
 * six things this app does, and the documents it already knows about with their first page
 * showing. Nothing on it is a menu — each tile and each row is one tap from a document.
 */
@Composable
fun HomeScreen(
    recents: List<RecentDoc>,
    starred: Set<String>,
    sizes: Map<String, Long>,
    opening: Boolean,
    onOpen: () -> Unit,
    onOpenRecent: (RecentDoc) -> Unit,
    onTool: (OpenIntent) -> Unit,
    onToggleStar: (String) -> Unit,
    onForget: (String) -> Unit,
    onFiles: () -> Unit,
    onTools: () -> Unit,
    onSettings: () -> Unit,
) {
    var shelf by remember { mutableIntStateOf(0) }
    val shown = if (shelf == 0) recents else recents.filter { it.id in starred }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columnWidth = if (maxWidth > MaxContentWidth) MaxContentWidth else maxWidth

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(columnWidth)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item { TopRow(onSearch = { onTool(OpenIntent(panel = NavTab.Search)) }, onSettings = onSettings) }

                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        ScreenTitle(stringResource(R.string.home_greeting))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.home_greeting_body),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item { Banners(onOpen = onOpen, onInk = { onTool(OpenIntent(tool = Tool.Pen)) }) }

                item {
                    SectionHeader(
                        text = stringResource(R.string.home_what),
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }

                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        item {
                            ActionTile(
                                icon = Icons.Outlined.PictureAsPdf,
                                label = stringResource(R.string.tile_open),
                                accent = Accent.Green,
                                onClick = onOpen,
                            )
                        }
                        item {
                            ActionTile(
                                icon = Icons.Filled.Edit,
                                label = stringResource(R.string.tile_ink),
                                accent = Accent.Pink,
                                onClick = { onTool(OpenIntent(tool = Tool.Pen)) },
                            )
                        }
                        item {
                            ActionTile(
                                icon = Icons.Filled.Search,
                                label = stringResource(R.string.tile_search),
                                accent = Accent.Blue,
                                onClick = { onTool(OpenIntent(panel = NavTab.Search)) },
                            )
                        }
                        item {
                            ActionTile(
                                icon = Icons.Filled.FormatListBulleted,
                                label = stringResource(R.string.tile_outline),
                                accent = Accent.Amber,
                                onClick = { onTool(OpenIntent(panel = NavTab.Outline)) },
                            )
                        }
                        item {
                            ActionTile(
                                icon = Icons.Outlined.Comment,
                                label = stringResource(R.string.tile_comments),
                                accent = Accent.Violet,
                                onClick = { onTool(OpenIntent(panel = NavTab.Comments)) },
                            )
                        }
                        item {
                            ActionTile(
                                icon = Icons.Filled.GridView,
                                label = stringResource(R.string.tile_tools),
                                accent = Accent.Cyan,
                                onClick = onTools,
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(14.dp))
                    TabRow(
                        selectedTabIndex = shelf,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { Hairline() },
                    ) {
                        listOf(R.string.home_tab_recent, R.string.home_tab_starred).forEachIndexed { index, label ->
                            Tab(
                                selected = shelf == index,
                                onClick = { shelf = index },
                                text = {
                                    Text(
                                        text = stringResource(label),
                                        fontSize = 14.sp,
                                        fontWeight = if (shelf == index) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (shown.isEmpty()) {
                    item {
                        EmptyNotice(
                            icon = Icons.Outlined.PictureAsPdf,
                            title = stringResource(if (shelf == 0) R.string.home_empty_title else R.string.home_empty_starred),
                            body = stringResource(if (shelf == 0) R.string.home_empty_body else R.string.home_empty_starred_body),
                            action = {
                                TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open)) }
                            },
                        )
                    }
                } else {
                    items(shown, key = { it.id }) { doc ->
                        DocRow(
                            doc = doc,
                            bytes = sizes[doc.id] ?: 0L,
                            starred = doc.id in starred,
                            onOpen = { onOpenRecent(doc) },
                            onToggleStar = { onToggleStar(doc.id) },
                            onForget = { onForget(doc.id) },
                        )
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = onFiles, contentPadding = PaddingValues(0.dp)) {
                                Text(stringResource(R.string.home_all_files), fontSize = 12.sp)
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }

                item {
                    Footnote(
                        text = stringResource(R.string.home_privacy),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            FloatingActionButton(
                onClick = { if (!opening) onOpen() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            ) {
                if (opening) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_open), modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** The logo, a search, and the way into the settings — the whole of the top bar. */
@Composable
private fun TopRow(onSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(30.dp)
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 10.dp),
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(
                Icons.Filled.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.tab_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Two cards that say what this app is, edge-peeking so it is obvious there is a second one.
 *
 * The reader app this replaces opened onto a paragraph about privacy; nobody read it. A card the
 * size of a thumb, with one sentence and one button, says the same thing and is readable.
 */
@Composable
private fun Banners(onOpen: () -> Unit, onInk: () -> Unit) {
    val pager = rememberPagerState { 2 }

    HorizontalPager(
        state = pager,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp),
        pageSpacing = 12.dp,
    ) { page ->
        if (page == 0) {
            BannerCard(
                accent = Accent.Green,
                icon = Icons.Outlined.PictureAsPdf,
                title = stringResource(R.string.banner_local_title),
                body = stringResource(R.string.banner_local_body),
                action = stringResource(R.string.banner_local_action),
                onClick = onOpen,
            )
        } else {
            BannerCard(
                accent = Accent.Pink,
                icon = Icons.Filled.Edit,
                title = stringResource(R.string.banner_ink_title),
                body = stringResource(R.string.banner_ink_body),
                action = stringResource(R.string.banner_ink_action),
                onClick = onInk,
            )
        }
    }
}

@Composable
private fun BannerCard(
    accent: Accent,
    icon: ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(132.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(84.dp)
                    .fillMaxHeight()
                    .background(accent.block()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent.ink(), modifier = Modifier.size(34.dp))
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                    Text(action, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
