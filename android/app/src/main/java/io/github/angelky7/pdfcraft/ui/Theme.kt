package io.github.angelky7.pdfcraft.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// A reader is a frame around someone else's document, so the chrome stays quiet: a deep green
// accent on paper colours, no tinted surfaces competing with the page. The page itself is always
// drawn on the document's own white.

private val Ink = Color(0xFF161A17)
private val Paper = Color(0xFFFBFBF9)
private val Moss = Color(0xFF2C6A55)
private val MossLight = Color(0xFF8FD3B8)
private val Slate = Color(0xFF444A46)

private val LightScheme = lightColorScheme(
    primary = Moss,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9DE),
    onPrimaryContainer = Color(0xFF07271C),
    secondary = Slate,
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8EBE7),
    onSurfaceVariant = Slate,
    outline = Color(0xFF9AA29C),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = MossLight,
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF1D4C3D),
    onPrimaryContainer = Color(0xFFCFE9DE),
    secondary = Color(0xFFB8C2BB),
    onSecondary = Color(0xFF1B211D),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE4E7E3),
    surface = Color(0xFF111413),
    onSurface = Color(0xFFE4E7E3),
    surfaceVariant = Color(0xFF2A302C),
    onSurfaceVariant = Color(0xFFBFC7C0),
    outline = Color(0xFF6E7871),
    error = Color(0xFFF2B8B5),
)

/** The page behind a document: white in light mode, and deliberately not pure black in dark. */
val PageBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1C201E) else Color(0xFFE7EAE6)

@Composable
fun PdfCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
