package com.tensei.ytxtube

import android.util.JsonReader
import android.util.JsonToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/** Fully loaded مرناة content (Blue-Plus style: one bulk pass, everything in memory). */
data class MirnahContentData(
    val movies: List<XtMovie>,
    val series: List<XtSeries>,
    val live: List<XtLive>,
    val vodCats: List<XtCategory>,
    val serCats: List<XtCategory>,
    val liveCats: List<XtCategory>,
    /** True when the panel was unreachable and last-good disk files were used. */
    val stale: Boolean = false,
)

/** Download progress for the bulk pass (bytes done / total when known). */
data class MirnahBulkProgress(
    val phase: String,
    val bytes: Long,
    val total: Long,
)

/**
 * Blue-Plus style bulk loader for مرناة content:
 *
 *  - sequential requests (live → vod → series) so the panel never sees parallel
 *    bursts that some hosts answer with bans;
 *  - every response body is copied raw to a disk file (no whole-JSON string in
 *    RAM, gzip handled transparently by OkHttp), atomic tmp+rename per file;
 *  - parsed back with a streaming [JsonReader] straight into the lists;
 *  - real byte progress is published while downloading;
 *  - if the panel is unreachable, last-good files on disk are parsed instead.
 *
 * The result is kept for the whole session so re-entering the tab is instant.
 */
object MirnahBulk {

    private val _progress = MutableStateFlow<MirnahBulkProgress?>(null)
    val progress: StateFlow<MirnahBulkProgress?> = _progress

    private data class SessionEntry(val key: String, val data: MirnahContentData)

    @Volatile private var entry: SessionEntry? = null

    fun invalidate() {
        entry = null
        _progress.value = null
    }

    /** All three wrapped disk files live in [dir]. */
    suspend fun load(xt: XtreamClient, dir: File, forceFresh: Boolean): MirnahContentData =
        withContext(Dispatchers.IO) {
            val key = xt.prewarmUrl()
            val cached = entry
            if (!forceFresh && cached?.key == key) return@withContext cached.data

            val fileLive = File(dir, "xtream_live.json")
            val fileVod = File(dir, "xtream_vod.json")
            val fileSeries = File(dir, "xtream_series.json")

            val data = runCatching {
                downloadAll(xt, fileLive, fileVod, fileSeries)
                parseAll(fileLive, fileVod, fileSeries, stale = false)
            }.getOrElse { downloadError ->
                // Panel unreachable: keep browsing on the last-good copy if we have one.
                if (!fileLive.exists() && !fileVod.exists() && !fileSeries.exists()) throw downloadError
                runCatching { parseAll(fileLive, fileVod, fileSeries, stale = true) }
                    .getOrElse { throw downloadError }
            }

            entry = SessionEntry(key, data)
            _progress.value = null
            data
        }

    // ---- download ----------------------------------------------------------

    private fun downloadAll(xt: XtreamClient, fileLive: File, fileVod: File, fileSeries: File) {
        val bytes = AtomicLong()
        val total = AtomicLong()

        fun setPhase(phase: String) {
            _progress.value = MirnahBulkProgress(phase, bytes.get(), total.get())
        }

        fun download(url: String, key: String, out: BufferedOutputStream) {
            val request = Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build()
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP${response.code}")
                val len = response.body?.contentLength() ?: -1L
                if (len > 0) total.addAndGet(len)
                out.write(("\"" + key + "\":").toByteArray())
                val input = response.body?.byteStream() ?: throw IOException("BADRESPONSE")
                BufferedInputStream(input, 64 * 1024).use { buffered ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = buffered.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        val done = bytes.addAndGet(n.toLong())
                        _progress.value = _progress.value?.copy(bytes = done, total = total.get())
                    }
                }
                out.flush()
            }
        }

        fun downloadWrapped(url1: String, key1: String, url2: String, key2: String, out: File) {
            val tmp = File(out.parentFile, out.name + ".tmp")
            FileOutputStream(tmp).use { fos ->
                BufferedOutputStream(fos, 128 * 1024).use { bos ->
                    bos.write("{\"data\":{".toByteArray())
                    download(url1, key1, bos)
                    bos.write(",".toByteArray())
                    download(url2, key2, bos)
                    bos.write("}}".toByteArray())
                }
            }
            if (!tmp.renameTo(out)) {
                out.delete()
                if (!tmp.renameTo(out)) tmp.delete()
            }
        }

        setPhase("القنوات المباشرة…")
        downloadWrapped(
            xt.apiUrl("get_live_categories"), "get_live_categories",
            xt.apiUrl("get_live_streams"), "get_live_streams",
            fileLive,
        )
        setPhase("الأفلام…")
        downloadWrapped(
            xt.apiUrl("get_vod_categories"), "get_vod_categories",
            xt.apiUrl("get_vod_streams"), "get_vod_streams",
            fileVod,
        )
        setPhase("المسلسلات…")
        downloadWrapped(
            xt.apiUrl("get_series_categories"), "get_series_categories",
            xt.apiUrl("get_series"), "get_series",
            fileSeries,
        )
        setPhase("جاري التحليل…")
    }

    // ---- parse -------------------------------------------------------------

    private fun parseAll(fileLive: File, fileVod: File, fileSeries: File, stale: Boolean): MirnahContentData {
        val b = Builder()
        parseWrapped(fileLive, b)
        parseWrapped(fileVod, b)
        parseWrapped(fileSeries, b)
        if (b.live.isEmpty() && b.movies.isEmpty() && b.series.isEmpty()) throw IOException("BADRESPONSE")
        return MirnahContentData(b.movies, b.series, b.live, b.vodCats, b.serCats, b.liveCats, stale)
    }

    private fun parseWrapped(file: File, b: Builder) {
        if (!file.exists() || file.length() == 0L) return
        file.inputStream().use { fis ->
            JsonReader(java.io.InputStreamReader(fis, Charsets.UTF_8)).use { r ->
                r.isLenient = true
                r.beginObject()
                while (r.hasNext()) {
                    val name = r.nextName()
                    if (name == "data") {
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "get_live_categories" -> r.arrayInto(b.liveCats) { readCategory() }
                                "get_vod_categories" -> r.arrayInto(b.vodCats) { readCategory() }
                                "get_series_categories" -> r.arrayInto(b.serCats) { readCategory() }
                                "get_live_streams" -> r.arrayInto(b.live) { readLive() }
                                "get_vod_streams" -> r.arrayInto(b.movies) { readMovie() }
                                "get_series" -> r.arrayInto(b.series) { readSeries() }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    } else {
                        r.skipValue()
                    }
                }
                r.endObject()
            }
        }
    }

    private class Builder {
        val liveCats = ArrayList<XtCategory>()
        val vodCats = ArrayList<XtCategory>()
        val serCats = ArrayList<XtCategory>()
        val live = ArrayList<XtLive>()
        val movies = ArrayList<XtMovie>()
        val series = ArrayList<XtSeries>()
    }

    private inline fun <T> JsonReader.arrayInto(list: MutableList<T>, read: JsonReader.() -> T?) {
        beginArray()
        while (hasNext()) read()?.let { list.add(it) }
        endArray()
    }

    // ---- streaming field readers (same mappings as XtreamClient) -----------

    /** Reads any scalar (string/number/bool/null) as a string; null when absent. */
    private fun JsonReader.optValue(): String? = when (peek()) {
        JsonToken.NULL -> { nextNull(); null }
        JsonToken.STRING -> nextString()
        JsonToken.NUMBER -> nextString()
        JsonToken.BOOLEAN -> nextBoolean().toString()
        else -> { skipValue(); null }
    }

    private fun JsonReader.readCategory(): XtCategory? {
        var id = ""
        var name = ""
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "category_id" -> id = optValue() ?: ""
                "category_name" -> name = optValue() ?: ""
                else -> skipValue()
            }
        }
        endObject()
        return if (id.isNotBlank()) XtCategory(id, clean(name)) else null
    }

    private fun JsonReader.readLive(): XtLive? {
        var id = ""
        var name = ""
        var icon: String? = null
        var catId = ""
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "stream_id" -> id = optValue() ?: ""
                "name" -> name = optValue() ?: ""
                "stream_icon" -> icon = optValue() ?: icon
                "icon" -> icon = optValue() ?: icon
                "category_id" -> catId = optValue() ?: ""
                else -> skipValue()
            }
        }
        endObject()
        if (id.isBlank()) return null
        return XtLive(id, clean(name), icon?.takeIf { it.isNotBlank() }, catId)
    }

    private fun JsonReader.readMovie(): XtMovie? {
        var id = ""
        var name = ""
        var icon: String? = null
        var cover: String? = null
        var catId = ""
        var ext = "mp4"
        var rating: String? = null
        var added: Long? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "stream_id" -> id = optValue() ?: ""
                "name" -> name = optValue() ?: ""
                "stream_icon" -> icon = optValue() ?: icon
                "cover_big" -> cover = optValue() ?: cover
                "movie_image" -> cover = optValue() ?: cover
                "category_id" -> catId = optValue() ?: ""
                "container_extension" -> ext = optValue()?.ifBlank { null } ?: "mp4"
                "rating" -> rating = optValue()
                "added" -> added = optValue()?.toLongOrNull()
                else -> skipValue()
            }
        }
        endObject()
        if (id.isBlank()) return null
        return XtMovie(
            id = id,
            name = clean(name),
            icon = icon?.takeIf { it.isNotBlank() } ?: cover?.takeIf { it.isNotBlank() },
            categoryId = catId,
            extension = ext.ifBlank { "mp4" },
            rating = rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" },
            added = added,
        )
    }

    private fun JsonReader.readSeries(): XtSeries? {
        var id = ""
        var name = ""
        var cover: String? = null
        var icon: String? = null
        var catId = ""
        var plot: String? = null
        var rating: String? = null
        var genre: String? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "series_id" -> id = optValue() ?: ""
                "name" -> name = optValue() ?: ""
                "cover" -> cover = optValue() ?: cover
                "stream_icon" -> icon = optValue() ?: icon
                "category_id" -> catId = optValue() ?: ""
                "plot" -> plot = optValue()?.takeIf { it.isNotBlank() }
                "rating" -> rating = optValue()
                "genre" -> genre = optValue()?.takeIf { it.isNotBlank() }
                else -> skipValue()
            }
        }
        endObject()
        if (id.isBlank()) return null
        return XtSeries(
            id = id,
            name = clean(name),
            cover = cover?.takeIf { it.isNotBlank() } ?: icon?.takeIf { it.isNotBlank() },
            categoryId = catId,
            plot = plot,
            rating = rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" },
            genre = genre,
        )
    }

    /** Tidy IPTV names: collapse blank runs and trim pipe/space noise. */
    private fun clean(raw: String): String = raw
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim(' ', '|')
        .ifBlank { raw.trim() }
}
