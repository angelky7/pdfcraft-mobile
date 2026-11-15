package io.github.angelky7.pdfcraft.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import uniffi.pdfcraft_mobile.Document
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The first page of a document, small, kept as a PNG next to its copy.
 *
 * A list of identical PDF glyphs answers "which of these is the manual I was reading" badly; a
 * picture of the page answers it at a glance. The thumbnail is rendered once, when the document
 * is imported or first opened, and kept on disk because rendering it every time the list is
 * rebuilt would cost a page raster per row.
 *
 * Failures are deliberately silent: a missing thumbnail falls back to a glyph, which is not worth
 * a dialog in the middle of opening a document.
 */
object Thumbs {
    private const val Tag = "Thumbs"

    /** How wide a stored thumbnail is. Big enough for a retina list row, small enough to be a PNG. */
    private const val Width = 168f

    private fun dir(context: Context): File = File(context.filesDir, "thumbs").apply { mkdirs() }

    fun fileOf(context: Context, id: String): File = File(dir(context), "$id.png")

    /** Render page one of [document] and store it as the thumbnail for [id]. Blocking: call off the UI thread. */
    fun save(context: Context, document: Document, id: String) {
        runCatching {
            val page = document.page(0) ?: return
            if (page.width <= 0f || page.height <= 0f) return
            val image = document.renderPage(0, (Width / page.width).coerceAtLeast(0.02f))
            if (image.width <= 0 || image.height <= 0) return

            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(image.pixels).order(ByteOrder.LITTLE_ENDIAN))
            fileOf(context, id).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            bitmap.recycle()
        }.onFailure { Log.w(Tag, "no thumbnail for $id: ${it.message}") }
    }

    fun load(context: Context, id: String): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(fileOf(context, id).absolutePath)?.asImageBitmap()
    }.getOrNull()

    fun forget(context: Context, id: String) {
        fileOf(context, id).delete()
    }
}
