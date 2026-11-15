package io.github.angelky7.pdfcraft.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.angelky7.pdfcraft.OpenedDocument
import io.github.angelky7.pdfcraft.PdfFiles
import io.github.angelky7.pdfcraft.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app: a way in when nothing is open, the reader once something is.
 *
 * Opening is the slow part — copying the picked file and parsing its page tree — so it happens
 * off the main thread with the home screen showing that it is working, and a failure comes back
 * as a dialog rather than as a screen that silently did nothing.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var opened by remember { mutableStateOf<OpenedDocument?>(null) }
    var opening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        opening = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) { runCatching { PdfFiles.load(context, uri) } }
            opening = false
            outcome
                .onSuccess { opened = it }
                .onFailure { failure = it.message ?: it::class.java.simpleName }
        }
    }

    val document = opened
    if (document != null) {
        ReaderScreen(opened = document, onClose = { opened = null })
    } else {
        HomeScreen(
            opening = opening,
            onOpen = {
                // Some file managers hand PDFs over as octet-stream, so ask for both.
                pick.launch(arrayOf("application/pdf", "application/octet-stream"))
            },
        )
    }

    failure?.let { message ->
        AlertDialog(
            onDismissRequest = { failure = null },
            title = { Text(stringResource(R.string.error_open_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { failure = null }) {
                    Text(stringResource(R.string.action_dismiss))
                }
            },
        )
    }
}
