package io.github.angelky7.pdfcraft

import android.graphics.Bitmap
import android.util.LruCache
import uniffi.pdfcraft_mobile.Document
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Rendered pages, kept until memory runs short.
 *
 * A page at tablet width is a few megabytes of pixels, and a document is hundreds of them, so
 * nothing may hold them all: the cache is sized against the heap the process was actually given
 * and evicts the least recently shown page first. Rendering is slow enough (tens of milliseconds
 * to a second on a scanned page) that a page scrolled past and back must not be rendered twice,
 * which is what this buys.
 */
class PageCache(private val document: Document) {

    /** A page is only reusable at the scale it was drawn at: a rotation changes every scale. */
    private data class Key(val page: Int, val scaleCentis: Int)

    private val bitmaps = object : LruCache<Key, Bitmap>(budget()) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount
    }

    /**
     * The page at [scale] device pixels per point, rendered on first use.
     *
     * The scale is capped at [MaxScale] rather than followed all the way up. Zooming a page to
     * four times its fit-width means sixteen times the pixels: a portrait A4 page at that size is
     * over 80 MB of ARGB in one bitmap, which no phone will give this app. Past the cap the same
     * raster is shown larger — slightly soft at the deepest zoom, and the alternative is being
     * killed for memory while zoomed in.
     *
     * Call from a background dispatcher: this blocks for the length of a render.
     */
    fun bitmap(page: Int, scale: Float): Bitmap {
        val capped = scale.coerceAtMost(MaxScale)
        // Hundredths of a scale step: two zooms that differ by less than 1% show the same raster.
        val key = Key(page, (capped * 100).toInt())
        bitmaps.get(key)?.let { return it }

        val image = document.renderPage(page, capped)
        require(image.width > 0 && image.height > 0) { "page $page rendered empty" }

        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        // BGRA from the engine, and a little-endian buffer makes copyPixelsFromBuffer read each
        // pixel as the 0xAARRGGBB word the bitmap wants.
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(image.pixels).order(ByteOrder.LITTLE_ENDIAN))

        bitmaps.put(key, bitmap)
        return bitmap
    }

    fun clear() = bitmaps.evictAll()

    private companion object {
        /**
         * Device pixels per PDF point past which a page is rendered once and scaled on screen.
         * 2.2 is a little over twice the linear resolution of a fit-width page on a phone: sharp
         * enough to read small print when zoomed, small enough to keep a bitmap in the tens of
         * megabytes rather than the hundreds.
         */
        const val MaxScale = 2.2f

        /**
         * A quarter of the heap the process can use, clamped: large enough for the pages around
         * the one on screen (and a tablet's two-page spread), small enough that the parser and
         * the bitmap of the page being drawn still fit beside it.
         */
        fun budget(): Int {
            val quarter = Runtime.getRuntime().maxMemory() / 4
            return quarter.coerceIn(24L shl 20, 192L shl 20).toInt()
        }
    }
}
