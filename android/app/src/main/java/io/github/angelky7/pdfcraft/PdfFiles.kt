package io.github.angelky7.pdfcraft

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import uniffi.pdfcraft_mobile.Document
import java.io.File

/** A document that has been opened, with the name to show for it. */
class OpenedDocument(val document: Document, val displayName: String) {
    val pageCount: Int get() = document.pageCount().toInt()
}

/**
 * Getting a document from the system picker into the engine.
 *
 * The picker hands back a `content://` URI, which is not a path the engine can open, and whose
 * read grant can be withdrawn once the activity that picked it is finished. So the bytes are
 * copied into the app's own cache and the engine opens that copy: the document then survives a
 * rotation, a return from the background, and the granting app being killed.
 */
object PdfFiles {
    private const val Tag = "PdfFiles"

    fun load(context: Context, uri: Uri): OpenedDocument {
        val displayName = displayName(context, uri)
        val target = File(context.cacheDir, "opened.pdf")

        // One copy slots, so re-opening does not accumulate a cache of every document ever viewed.
        runCatching { target.delete() }

        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, bufferSize = 1 shl 16) }
        } ?: throw IllegalStateException(context.getString(R.string.error_cannot_read))

        Log.i(Tag, "opened $displayName (${target.length()} bytes)")
        val document = Document.open(target.absolutePath)
        return OpenedDocument(document, displayName)
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()

        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.untitled_document)
    }
}
