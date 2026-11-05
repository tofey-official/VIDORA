package com.tensei.ytxtube

import android.content.Context
import java.io.File

/**
 * Tiny file cache for Xtream API responses.
 *
 * Bodies are stored as plain .txt files (the raw JSON text) with a .meta
 * sidecar holding:
 *   line 1: credential fingerprint (dns/username/password hash)
 *   line 2: saved-at epoch millis
 *
 * Files live under filesDir/mirnah_cache so the settings "clear cache"
 * button (which wipes cacheDir) does not destroy them, and so the app can
 * serve movies / series / live lists instantly on every reopen without
 * re-downloading anything from the panel.
 *
 * Freshness: a cached body younger than [MAX_AGE_MS] is served from disk
 * with zero network calls. Older bodies are re-fetched, but any cached
 * copy is still used as a fallback when the network fails.
 */
object MirnahCache {

    /** How long a cached response is considered fresh (24 hours). */
    const val MAX_AGE_MS: Long = 24 * 60 * 60 * 1000L

    fun dir(context: Context): File = File(context.filesDir, "mirnah_cache").apply { mkdirs() }

    /** Delete every cached file (called on logout). */
    fun clear(context: Context) {
        File(context.filesDir, "mirnah_cache").deleteRecursively()
    }

    data class Entry(val body: String, val savedAt: Long)

    /** Build a filesystem-safe cache key from an API action + params. */
    fun key(action: String, params: Map<String, String> = emptyMap()): String {
        val raw = buildString {
            append(action)
            params.forEach { (k, v) -> append('_').append(k).append('-').append(v) }
        }
        return raw.map { if (it.isLetterOrDigit() || it == '_') it else '_' }.joinToString("").take(120)
    }

    /** Fingerprint of the current credentials; cache is invisible across changes. */
    fun fingerprint(dns: String, username: String, password: String): String =
        "${dns.trim().hashCode()}_${username.trim().hashCode()}_${password.hashCode()}"

    /**
     * Read a cached body. Returns null when missing, blank, foreign
     * (different credentials) or older than [maxAgeMs].
     */
    fun read(dir: File, key: String, fp: String, maxAgeMs: Long = MAX_AGE_MS): Entry? {
        val data = File(dir, "$key.txt")
        val meta = File(dir, "$key.meta")
        if (!data.isFile || !meta.isFile) return null
        val body = runCatching { data.readText() }.getOrNull() ?: return null
        if (body.isBlank()) return null
        val lines = runCatching { meta.readText().trim().lines() }.getOrNull() ?: return null
        if (lines.size < 2) return null
        if (lines[0].trim() != fp) return null
        val savedAt = lines[1].trim().toLongOrNull() ?: return null
        val age = System.currentTimeMillis() - savedAt
        if (age < 0 || age > maxAgeMs) return null
        return Entry(body, savedAt)
    }

    /** Persist a response body (meta written last so partial writes fail safe). */
    fun write(dir: File, key: String, fp: String, body: String) {
        runCatching {
            dir.mkdirs()
            File(dir, "$key.txt").writeText(body)
            File(dir, "$key.meta").writeText("$fp\n${System.currentTimeMillis()}")
        }
    }
}
