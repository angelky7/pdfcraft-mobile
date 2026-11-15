package io.github.angelky7.pdfcraft.data

import android.content.Context

/**
 * The handful of choices the app remembers, and the star on a document.
 *
 * All of it lives in one private `SharedPreferences` file: there is no account to sync it to, so
 * a few booleans and a set of ids is the whole of the app's state that is not a file on disk.
 */
class Prefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pdfcraft.settings", Context.MODE_PRIVATE)

    /** Whether a finger draws when no stylus is in use. Off by default: a page meant for reading. */
    var fingerWrites: Boolean
        get() = prefs.getBoolean(KeyFinger, false)
        set(value) = prefs.edit().putBoolean(KeyFinger, value).apply()

    /** Single-page swipe instead of one continuous column. */
    var singlePage: Boolean
        get() = prefs.getBoolean(KeySingle, false)
        set(value) = prefs.edit().putBoolean(KeySingle, value).apply()

    /** "system", "light" or "dark". */
    var theme: String
        get() = prefs.getString(KeyTheme, ThemeSystem) ?: ThemeSystem
        set(value) = prefs.edit().putString(KeyTheme, value).apply()

    /** The ids of the documents the reader starred. */
    fun starred(): Set<String> = prefs.getStringSet(KeyStarred, null)?.toSet().orEmpty()

    fun isStarred(id: String): Boolean = id in starred()

    /** Flip the star on [id] and report where it ended up. */
    fun toggleStar(id: String): Boolean {
        val next = starred().toMutableSet()
        val on = if (next.contains(id)) {
            next.remove(id)
            false
        } else {
            next.add(id)
            true
        }
        prefs.edit().putStringSet(KeyStarred, next).apply()
        return on
    }

    fun forgetStar(id: String) {
        val next = starred().toMutableSet()
        if (next.remove(id)) prefs.edit().putStringSet(KeyStarred, next).apply()
    }

    companion object {
        const val ThemeSystem = "system"
        const val ThemeLight = "light"
        const val ThemeDark = "dark"

        private const val KeyFinger = "finger"
        private const val KeySingle = "single"
        private const val KeyTheme = "theme"
        private const val KeyStarred = "starred"
    }
}
