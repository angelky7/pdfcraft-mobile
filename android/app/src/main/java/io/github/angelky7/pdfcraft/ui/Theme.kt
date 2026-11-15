package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The widgets are Material 3 — a phone's own switch, tab, navigation bar and floating action
// button, so nothing here has to be learned — while the arrangement of them follows a document
// reader: a greeting, a shelf of shortcuts, a list with thumbnails, and four places to be.
//
// The palette is Material's tonal one, with the app's green as the source colour: surfaces are
// tinted versions of it rather than grey, and a single accent does all the pointing.

private val NightScheme = darkColorScheme(
    primary = Color(0xFF8FD3B8),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF1D4C3D),
    onPrimaryContainer = Color(0xFFCFE9DE),
    secondary = Color(0xFFB8C9BF),
    onSecondary = Color(0xFF1B211D),
    secondaryContainer = Color(0xFF2C3630),
    onSecondaryContainer = Color(0xFFDCE7E0),
    tertiary = Color(0xFFA8C8DE),
    onTertiary = Color(0xFF0A2130),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE2E6E2),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE2E6E2),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFBFC7C0),
    outline = Color(0xFF7E8880),
    outlineVariant = Color(0xFF3A423D),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

private val DayScheme = lightColorScheme(
    primary = Color(0xFF2C6A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCFE9DE),
    onPrimaryContainer = Color(0xFF07271C),
    secondary = Color(0xFF4A544D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE5DD),
    onSecondaryContainer = Color(0xFF161D18),
    tertiary = Color(0xFF3A6076),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFBF8),
    onBackground = Color(0xFF161A17),
    surface = Color(0xFFFBFBF8),
    onSurface = Color(0xFF161A17),
    surfaceVariant = Color(0xFFE6EAE5),
    onSurfaceVariant = Color(0xFF444A46),
    outline = Color(0xFF757D77),
    outlineVariant = Color(0xFFC6CDC7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Whether the app is drawing its dark palette. Read by the tint helpers below. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The app's own green: the launcher icon's colour. */
val BrandGreen = Color(0xFF2E7D5B)

/** The page behind an open document: a shade off the canvas, so the sheet reads as paper. */
val PageBackground: Color
    @Composable get() = if (LocalDarkTheme.current) Color(0xFF1A1E1C) else Color(0xFFE7EAE6)

/**
 * A shortcut's colour pair: the block behind an icon, and the icon on it.
 *
 * Six shortcuts that all looked the same would be six grey squares with six words under them;
 * a tint each is what lets the shelf be read at a glance. The light and dark pairs are chosen
 * separately, because a green that reads on a dark block does not read on a pale one.
 */
enum class Accent(
    private val lightBlock: Long,
    private val lightInk: Long,
    private val darkBlock: Long,
    private val darkInk: Long,
) {
    Green(0xFFCFE9DE, 0xFF17553D, 0xFF1B3B2E, 0xFF8FD3B8),
    Blue(0xFFD6E4FA, 0xFF1B4A8C, 0xFF1B2C42, 0xFF9CC7FA),
    Pink(0xFFFADCE6, 0xFF8C2C54, 0xFF3A1E2A, 0xFFF3A7C4),
    Amber(0xFFF7E4C2, 0xFF7A5210, 0xFF3A2E14, 0xFFE9C27A),
    Violet(0xFFE4DEF7, 0xFF4A3A8C, 0xFF2A2440, 0xFFBFAFEF),
    Cyan(0xFFCDE8EE, 0xFF10556B, 0xFF14303A, 0xFF8FD3E4),
    Red(0xFFF8DAD7, 0xFF9B2C20, 0xFF3A1F1C, 0xFFEFA79E),
}

/** The colour behind a [Accent] tile. */
@Composable
fun Accent.block(): Color = Color(if (LocalDarkTheme.current) darkBlock else lightBlock)

/** The colour of a glyph drawn on [block]. */
@Composable
fun Accent.ink(): Color = Color(if (LocalDarkTheme.current) darkInk else lightInk)

/**
 * The theme. [dark] is a parameter rather than a call to `isSystemInDarkTheme` so the choice on
 * the preferences screen can override the phone's own.
 */
@Composable
fun PdfCraftTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) NightScheme else DayScheme,
            content = content,
        )
    }
}
