package dev.orgdroid.recents

import android.content.Context

data class RecentFile(
    val uri: String,
    val displayName: String,
    val lastOpenedAt: Long,
)

/**
 * Pure encode/decode. One entry per line, tab-delimited:
 *   <timestamp>\t<escaped-displayName>\t<uri>
 *
 * URIs are RFC-encoded and contain no tabs or newlines; stored raw.
 * Only displayName needs escaping: `\` → `\\`, newline → `\n`, tab → `\t`.
 * Malformed and blank lines are skipped silently for forward-compat.
 */
object RecentFilesCodec {
    fun encode(items: List<RecentFile>): String {
        if (items.isEmpty()) return ""
        val sb = StringBuilder()
        for (item in items) {
            sb.append(item.lastOpenedAt)
            sb.append('\t')
            sb.append(escape(item.displayName))
            sb.append('\t')
            sb.append(item.uri)
            sb.append('\n')
        }
        return sb.toString()
    }

    fun decode(text: String): List<RecentFile> {
        if (text.isEmpty()) return emptyList()
        val out = mutableListOf<RecentFile>()
        for (line in text.split('\n')) {
            if (line.isEmpty()) continue
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) continue
            val ts = parts[0].toLongOrNull() ?: continue
            out.add(
                RecentFile(
                    uri = parts[2],
                    displayName = unescape(parts[1]),
                    lastOpenedAt = ts,
                )
            )
        }
        return out
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    else -> {
                        sb.append(c); sb.append(s[i + 1])
                    }
                }
                i += 2
            } else {
                sb.append(c)
                i += 1
            }
        }
        return sb.toString()
    }
}

class RecentFilesStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<RecentFile> {
        val text = prefs.getString(KEY_LIST, "") ?: ""
        return RecentFilesCodec.decode(text)
    }

    fun add(item: RecentFile): List<RecentFile> {
        val current = load().filter { it.uri != item.uri }
        val updated = (listOf(item) + current).take(CAPACITY)
        save(updated)
        return updated
    }

    fun remove(uri: String): List<RecentFile> {
        val updated = load().filter { it.uri != uri }
        save(updated)
        return updated
    }

    private fun save(items: List<RecentFile>) {
        prefs.edit().putString(KEY_LIST, RecentFilesCodec.encode(items)).apply()
    }

    companion object {
        private const val PREFS_NAME = "orgdroid_recents"
        private const val KEY_LIST = "list"
        const val CAPACITY = 10
    }
}
