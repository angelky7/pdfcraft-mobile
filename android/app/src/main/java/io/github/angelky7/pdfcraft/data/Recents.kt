package io.github.angelky7.pdfcraft.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.angelky7.pdfcraft.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A document this app has opened before.
 *
 * [page] is where the reader last was, 0-based, so reopening a manual continues reading rather
 * than starting at page one. [total] is kept so the list can say how long the document is without
 * opening it again — opening is the expensive part.
 */
data class RecentDoc(
    val id: String,
    val name: String,
    val page: Int,
    val total: Int,
    val openedAt: Long,
) {
    val progress: Float
        get() = if (total <= 0) 0f else (page + 1).toFloat() / total
}

/**
 * The documents the app keeps on hand.
 *
 * The system picker hands over a `content://` URI whose read grant can be withdrawn once the
 * granting activity finishes, so a document is copied into the app's own storage when it is
 * opened. Keeping that copy — rather than one scratch file overwritten by the next open — is what
 * makes a "recent documents" list possible at all: the app can reopen something days later without
 * the user finding it again.
 *
 * Two things bound what that costs. Only [Keep] documents are remembered, oldest dropped first,
 * and the copies live under the same cap, so the app cannot grow into a shadow of the user's
 * Downloads folder.
 */
class Recents(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("pdfcraft.recents", Context.MODE_PRIVATE)

    /** Where imported copies live. Private storage: no permission needed to read them back. */
    private val dir: File
        get() = File(app.filesDir, "documents").apply { mkdirs() }

    fun list(): List<RecentDoc> {
        val raw = prefs.getString(Key, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecentDoc(
                    id = id,
                    name = item.optString("name").ifBlank { id },
                    page = item.optInt("page", 0),
                    total = item.optInt("total", 0),
                    openedAt = item.optLong("openedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Copy [uri] in and remember it. Call from a background dispatcher: this reads the whole file.
     *
     * A document with the same name replaces the earlier one instead of appearing twice — opening
     * yesterday's invoice again after it was re-sent should not leave two rows for one document.
     */
    fun importFrom(uri: Uri): RecentDoc {
        val name = displayName(uri)
        val existing = list().firstOrNull { it.name == name }

        // A fresh id per import, so a partially written copy can never be mistaken for an older
        // complete one of the same name.
        val id = "d${System.currentTimeMillis()}"
        val target = File(dir, "$id.pdf")
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        } ?: run {
            target.delete()
            throw IllegalStateException(app.getString(R.string.error_cannot_read))
        }

        val record = RecentDoc(
            id = id,
            name = name,
            page = 0,
            total = 0,
            openedAt = System.currentTimeMillis(),
        )
        save((list().filterNot { it.name == name } + record).sortedByDescending { it.openedAt })

        existing?.let { forget(it.id) }
        prune()
        return record
    }

    /** Remember where the reader stopped. */
    fun touch(id: String, page: Int, total: Int) {
        val updated = list().map {
            if (it.id == id) it.copy(page = page, total = total, openedAt = System.currentTimeMillis()) else it
        }
        save(updated)
    }

    fun forget(id: String) {
        fileOf(id).delete()
        Thumbs.forget(app, id)
        save(list().filterNot { it.id == id })
    }

    fun fileOf(id: String): File = File(dir, "$id.pdf")

    fun exists(id: String): Boolean = fileOf(id).let { it.isFile && it.length() > 0 }

    /** How much disk one copy takes. Zero when the copy is gone, which the list can show as such. */
    fun sizeOf(id: String): Long = fileOf(id).let { if (it.isFile) it.length() else 0L }

    /** What the whole shelf costs, for the settings and files screens. */
    fun totalBytes(): Long = list().sumOf { sizeOf(it.id) }

    /** Drop every copy, keeping the list's bookkeeping consistent. */
    fun forgetAll() {
        list().forEach { forget(it.id) }
    }

    private fun save(documents: List<RecentDoc>) {
        val array = JSONArray()
        documents.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("name", it.name)
                    .put("page", it.page)
                    .put("total", it.total)
                    .put("openedAt", it.openedAt),
            )
        }
        prefs.edit().putString(Key, array.toString()).apply()
    }

    /** Drop the oldest documents past the cap, files included. */
    private fun prune() {
        val documents = list().sortedByDescending { it.openedAt }
        documents.drop(Keep).forEach { forget(it.id) }
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            app.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()

        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "document.pdf"
    }

    private companion object {
        const val Key = "documents"

        /** How many documents stay on hand. Each costs its own file, so this is a size decision. */
        const val Keep = 8
    }
}
