package io.github.angelky7.pdfcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.angelky7.pdfcraft.ui.AppRoot
import io.github.angelky7.pdfcraft.ui.PdfCraftTheme

/**
 * The single activity: everything else is Compose.
 *
 * Draw edge to edge and let the screens handle their own insets, so the reader's page can extend
 * under the system bars while the bar above it does not.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PdfCraftTheme {
                AppRoot()
            }
        }
    }
}
