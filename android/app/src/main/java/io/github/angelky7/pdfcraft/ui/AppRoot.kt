package io.github.angelky7.pdfcraft.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.angelky7.pdfcraft.OpenedDocument
import io.github.angelky7.pdfcraft.PdfFiles
import io.github.angelky7.pdfcraft.R
import io.github.angelky7.pdfcraft.data.Prefs
import io.github.angelky7.pdfcraft.data.RecentDoc
import io.github.angelky7.pdfcraft.data.Recents
import io.github.angelky7.pdfcraft.data.Thumbs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app.
 *
 * Four places to be and one document to read. The document covers everything else while it is
 * open, because a reader that keeps a tab bar under a page is a website pretending to be one.
 *
 * Opening is the slow part — copying the file in, then parsing its page tree — so it happens off
 * the main thread with the screen saying so, and a failure comes back as a dialog rather than as
 * a screen that silently did nothing.
 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recents = remember { Recents(context) }
    val prefs = remember { Prefs(context) }

    var theme by remember { mutableStateOf(prefs.theme) }
    var fingerWrites by remember { mutableStateOf(prefs.fingerWrites) }
    var singlePage by remember { mutableStateOf(prefs.singlePage) }

    val dark = when (theme) {
        Prefs.ThemeDark -> true
        Prefs.ThemeLight -> false
        else -> isSystemInDarkTheme()
    }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    PdfCraftTheme(dark = dark) {
        var tab by remember { mutableStateOf(HomeTab.Home) }
        var documents by remember { mutableStateOf(recents.list()) }
        var sizes by remember {
            mutableStateOf(recents.list().associate { it.id to recents.sizeOf(it.id) })
        }
        var starred by remember { mutableStateOf(prefs.starred()) }

        var opened by remember { mutableStateOf<OpenedDocument?>(null) }
        var openIntent by remember { mutableStateOf(OpenIntent()) }
        var pending by remember { mutableStateOf(OpenIntent()) }
        var opening by remember { mutableStateOf(false) }
        var failure by remember { mutableStateOf<String?>(null) }

        fun reload() {
            val listed = recents.list()
            documents = listed
            sizes = listed.associate { it.id to recents.sizeOf(it.id) }
            starred = prefs.starred()
        }

        // Some file managers hand PDFs over as octet-stream, so ask for both.
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            opening = true
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        val record = recents.importFrom(uri)
                        val document = PdfFiles.open(
                            id = record.id,
                            file = recents.fileOf(record.id),
                            displayName = record.name,
                            startPage = 0,
                        )
                        // A picture of page one, so the list can be read at a glance next time.
                        Thumbs.save(context, document.document, record.id)
                        document
                    }
                }
                opening = false
                outcome
                    .onSuccess { document ->
                        openIntent = pending
                        opened = document
                        reload()
                    }
                    .onFailure {
                        failure = it.message ?: it::class.java.simpleName
                        reload()
                    }
            }
        }

        fun pickFile(intent: OpenIntent) {
            if (opening) return
            pending = intent
            pick.launch(arrayOf("application/pdf", "application/octet-stream"))
        }

        fun open(record: RecentDoc, intent: OpenIntent) {
            if (opening) return
            opening = true
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        // The copy can be gone: the app was reinstalled, or the system cleaned up.
                        if (!recents.exists(record.id)) error(context.getString(R.string.error_file_gone))
                        val document = PdfFiles.open(record.id, recents.fileOf(record.id), record.name, record.page)
                        // A copy imported before thumbnails existed gets one now, while the page is at hand.
                        if (Thumbs.load(context, record.id) == null) {
                            Thumbs.save(context, document.document, record.id)
                        }
                        document
                    }
                }
                opening = false
                outcome
                    .onSuccess { document ->
                        openIntent = intent
                        opened = document
                    }
                    .onFailure {
                        failure = it.message ?: it::class.java.simpleName
                        reload()
                    }
            }
        }

        /** Run a tool: on the document on the shelf, or on one the reader picks next. */
        fun runTool(intent: OpenIntent) {
            val latest = documents.firstOrNull()
            if (latest != null) open(latest, intent) else pickFile(intent)
        }

        val document = opened
        if (document != null) {
            ReaderScreen(
                opened = document,
                intent = openIntent,
                fingerWrites = fingerWrites,
                singlePage = singlePage,
                onProgress = { page, total ->
                    // Written from the reader's own scope; the list picks it up when the reader closes.
                    scope.launch(Dispatchers.IO) { recents.touch(document.id, page, total) }
                },
                onClose = {
                    reload()
                    opened = null
                },
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                val expanded = maxWidth >= 840.dp

                val content: @Composable () -> Unit = {
                    when (tab) {
                        HomeTab.Home -> HomeScreen(
                            recents = documents,
                            starred = starred,
                            sizes = sizes,
                            opening = opening,
                            onOpen = { pickFile(OpenIntent()) },
                            onOpenRecent = { record -> open(record, OpenIntent()) },
                            onTool = { intent -> runTool(intent) },
                            onToggleStar = { id ->
                                prefs.toggleStar(id)
                                starred = prefs.starred()
                            },
                            onForget = { id ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { recents.forget(id) }
                                    prefs.forgetStar(id)
                                    reload()
                                }
                            },
                            onFiles = { tab = HomeTab.Files },
                            onTools = { tab = HomeTab.Tools },
                            onSettings = { tab = HomeTab.Settings },
                        )

                        HomeTab.Files -> FilesScreen(
                            recents = documents,
                            sizes = sizes,
                            starred = starred,
                            totalBytes = sizes.values.sum(),
                            onOpenRecent = { record -> open(record, OpenIntent()) },
                            onToggleStar = { id ->
                                prefs.toggleStar(id)
                                starred = prefs.starred()
                            },
                            onForget = { id ->
                                scope.launch {
                                    withContext(Dispatchers.IO) { recents.forget(id) }
                                    prefs.forgetStar(id)
                                    reload()
                                }
                            },
                            onImport = { pickFile(OpenIntent()) },
                            onClearAll = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { recents.forgetAll() }
                                    documents.forEach { prefs.forgetStar(it.id) }
                                    reload()
                                }
                            },
                        )

                        HomeTab.Tools -> ToolsScreen(
                            expanded = expanded,
                            onTool = { intent -> runTool(intent) },
                            onImport = { pickFile(OpenIntent()) },
                        )

                        HomeTab.Settings -> SettingsScreen(
                            theme = theme,
                            fingerWrites = fingerWrites,
                            singlePage = singlePage,
                            version = version,
                            docCount = documents.size,
                            totalBytes = sizes.values.sum(),
                            onTheme = {
                                theme = it
                                prefs.theme = it
                            },
                            onFingerWrites = {
                                fingerWrites = it
                                prefs.fingerWrites = it
                            },
                            onSinglePage = {
                                singlePage = it
                                prefs.singlePage = it
                            },
                            onClearAll = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { recents.forgetAll() }
                                    documents.forEach { prefs.forgetStar(it.id) }
                                    reload()
                                }
                            },
                        )
                    }
                }

                if (expanded) {
                    Row(Modifier.fillMaxSize()) {
                        SideRail(tab = tab, onTab = { tab = it })
                        Box(Modifier.weight(1f).fillMaxHeight().statusBarsPadding()) { content() }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { content() }
                        BottomBar(tab = tab, onTab = { tab = it })
                    }
                }
            }
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
}
