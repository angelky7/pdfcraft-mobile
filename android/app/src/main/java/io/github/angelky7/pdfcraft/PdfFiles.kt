package io.github.angelky7.pdfcraft

import android.util.Log
import uniffi.pdfcraft_mobile.Document
import java.io.File

/**
 * A document that is open, with everything the reader needs about where it came from.
 *
 * [id] is the key it is filed under in [io.github.angelky7.pdfcraft.data.Recents], [startPage] is
 * where the reader left off last time, and [displayName] is what the title bar shows.
 */
class OpenedDocument(
    val id: String,
    val document: Document,
    val displayName: String,
    val startPage: Int,
) {
    val pageCount: Int get() = runCatching { document.pageCount().toInt() }.getOrDefault(0)
}

/**
 * Turning a file on disk into an open document.
 *
 * The engine opens a real path, not a content URI: a URI is a stream, and every page the reader
 * renders needs to seek in the file. That is why opening goes through a copy the app owns
 * (see `Recents`), and why this stays as small as it is.
 */
object PdfFiles {
    private const val Tag = "PdfFiles"

    /** Open [file]. Call from a background dispatcher: parsing the page tree is the slow part. */
    fun open(id: String, file: File, displayName: String, startPage: Int): OpenedDocument {
        require(file.isFile) { "the file is gone" }
        val document = Document.open(file.absolutePath)
        Log.i(Tag, "opened $displayName (${file.length()} bytes)")
        return OpenedDocument(id = id, document = document, displayName = displayName, startPage = startPage)
    }
}
