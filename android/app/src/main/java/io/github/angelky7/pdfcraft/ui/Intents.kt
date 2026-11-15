package io.github.angelky7.pdfcraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.ink.Tool

/** The four places the app can be in. The reader is not one of them: it covers all four. */
enum class HomeTab(val label: Int, val icon: ImageVector) {
    Home(R.string.tab_home, Icons.Filled.Home),
    Files(R.string.tab_files, Icons.Filled.Folder),
    Tools(R.string.tab_tools, Icons.Filled.GridView),
    Settings(R.string.tab_settings, Icons.Filled.Settings),
}

/**
 * What to do once a document is open.
 *
 * A shortcut on the home screen or a card on the tools screen is not a place — it is an intention
 * about a document the reader has not chosen yet. Carrying the intention through the file picker
 * and into the reader is what makes "highlighter" a button that works rather than a button that
 * describes itself.
 */
data class OpenIntent(
    /** Start with this tool in hand, drawing turned on. */
    val tool: Tool? = null,
    /** Open the navigation panel on this tab. */
    val panel: NavTab? = null,
    /** Open the save sheet as soon as the page is up. */
    val save: Boolean = false,
    /** Show the document's facts: pages, size, encryption. */
    val info: Boolean = false,
)
