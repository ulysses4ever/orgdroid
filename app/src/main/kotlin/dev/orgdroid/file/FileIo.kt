package dev.orgdroid.file

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object FileIo {
    private const val PERSIST_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun persistPermission(resolver: ContentResolver, uri: Uri) {
        resolver.takePersistableUriPermission(uri, PERSIST_FLAGS)
    }

    suspend fun read(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        } ?: error("Could not open input stream for $uri")
    }

    suspend fun write(resolver: ContentResolver, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates before writing; plain "w" leaves stale tail bytes if new content is shorter.
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Could not open output stream for $uri")
    }
}
