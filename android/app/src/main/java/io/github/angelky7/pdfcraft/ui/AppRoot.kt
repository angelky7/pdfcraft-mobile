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
import io.github.angelky7.pdfcraft.data.RecentDoc
import io.github.angelky7.pdfcraft.data.Recents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app: the list of documents until one is open, the reader once it is.
 *
 * Opening is the slow part — copying the picked file, then parsing its page tree — so it happens
 * off the main thread with the screen saying that it is working, and a failure comes back as a
 * dialog rather than as a screen that silently did nothing.
 *
 * The reader reports where it got to, and this remembers it. That is the whole reason a document
 * is copied in rather than streamed: the app can come back to page 214 of a manual tomorrow.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents = remember { Recents(context) }

    var documents by remember { mutableStateOf(recents.list()) }
    var opened by remember { mutableStateOf<OpenedDocument?>(null) }
    var opening by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        opening = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val record = recents.importFrom(uri)
                    PdfFiles.open(
                        id = record.id,
                        file = recents.fileOf(record.id),
                        displayName = record.name,
                        startPage = 0,
                    )
                }
            }
            opening = false
            outcome
                .onSuccess { opened = it }
                .onFailure {
                    failure = it.message ?: it::class.java.simpleName
                    documents = recents.list()
                }
        }
    }

    fun openRecent(record: RecentDoc) {
        if (opening) return
        opening = true
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    // The copy can be gone: the app was reinstalled, or the system cleaned up.
                    if (!recents.exists(record.id)) error(context.getString(R.string.error_file_gone))
                    PdfFiles.open(record.id, recents.fileOf(record.id), record.name, record.page)
                }
            }
            opening = false
            outcome
                .onSuccess { opened = it }
                .onFailure {
                    failure = it.message ?: it::class.java.simpleName
                    documents = recents.list()
                }
        }
    }

    val document = opened
    if (document != null) {
        ReaderScreen(
            opened = document,
            onProgress = { page, total ->
                // Written from the reader's own scope; the list picks it up when the reader closes.
                scope.launch(Dispatchers.IO) { recents.touch(document.id, page, total) }
            },
            onClose = {
                // The page was already reported by onProgress; this only refreshes the list.
                documents = recents.list()
                opened = null
            },
        )
    } else {
        HomeScreen(
            recents = documents,
            opening = opening,
            onOpen = {
                // Some file managers hand PDFs over as octet-stream, so ask for both.
                pick.launch(arrayOf("application/pdf", "application/octet-stream"))
            },
            onOpenRecent = { record -> openRecent(record) },
            onForget = { id ->
                scope.launch {
                    withContext(Dispatchers.IO) { recents.forget(id) }
                    documents = recents.list()
                }
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
