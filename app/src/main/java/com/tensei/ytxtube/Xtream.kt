package com.tensei.ytxtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Xtream category (used for live, movies and series). */
data class XtCategory(val id: String, val name: String)

/** Live channel from get_live_streams. */
data class XtLive(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
)

/** VOD movie from get_vod_streams. */
data class XtMovie(
    val id: String,
    val name: String,
    val icon: String?,
    val categoryId: String,
    val extension: String,
    val rating: String?,
    val added: Long?,
)

/** Series entry from get_series. */
data class XtSeries(
    val id: String,
    val name: String,
    val cover: String?,
    val categoryId: String,
    val plot: String?,
    val rating: String?,
    val genre: String?,
)

/** One episode of a series. */
data class XtEpisode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val extension: String,
    val image: String?,
    val durationSecs: Long?,
    val plot: String?,
)

/** Result of get_series_info. */
data class XtSeriesInfo(
    val seasons: List<Int>,
    val episodes: Map<Int, List<XtEpisode>>,
)

/**
 * Minimal Xtream Codes API client for مرناة.
 *
 * All three Xtream branches are supported:
 *  - live:      get_live_categories / get_live_streams  → {dns}/live/u/p/{id}.ts
 *  - movies:    get_vod_categories  / get_vod_streams   → {dns}/movie/u/p/{id}.{ext}
 *  - series:    get_series_categories/get_series/get_series_info → {dns}/series/u/p/{id}.{ext}
 */
class XtreamClient(private val dns: String, private val username: String, private val password: String) {

    private val base = MirnahCodes.normalizeDns(dns)

    /** Verify credentials; throws IOException with an Arabic-friendly cause when rejected. */
    suspend fun checkAuth() {
        val root = jsonGet(null).let { runCatching { JSONObject(it) }.getOrElse { throw IOException("BADRESPONSE") } }
        val userInfo = root.optJSONObject("user_info")
        val auth = userInfo?.optString("auth", "0") == "1"
        if (!auth) throw IOException("AUTH")
    }

    suspend fun liveCategories(): List<XtCategory> = categories("get_live_categories")
    suspend fun vodCategories(): List<XtCategory> = categories("get_vod_categories")
    suspend fun seriesCategories(): List<XtCategory> = categories("get_series_categories")

    suspend fun liveStreams(): List<XtLive> = withContext(Dispatchers.IO) {
        val arr = arrayGet("get_live_streams")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("stream_id")
            if (id.isBlank()) null else XtLive(
                id = id,
                name = clean(o.optString("name")),
                icon = MirnahCodes.opt(o, "stream_icon", "icon"),
                categoryId = o.optString("category_id"),
            )
        }
    }

    suspend fun vodStreams(): List<XtMovie> = withContext(Dispatchers.IO) {
        val arr = arrayGet("get_vod_streams")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("stream_id")
            if (id.isBlank()) null else XtMovie(
                id = id,
                name = clean(o.optString("name")),
                icon = MirnahCodes.opt(o, "stream_icon", "cover_big", "movie_image"),
                categoryId = o.optString("category_id"),
                extension = o.optString("container_extension", "mp4").ifBlank { "mp4" },
                rating = o.optString("rating", "").takeIf { it.isNotBlank() && it != "0" && it != "0.0" },
                added = o.optString("added").toLongOrNull(),
            )
        }
    }

    suspend fun series(): List<XtSeries> = withContext(Dispatchers.IO) {
        val arr = arrayGet("get_series")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("series_id")
            if (id.isBlank()) null else XtSeries(
                id = id,
                name = clean(o.optString("name")),
                cover = MirnahCodes.opt(o, "cover", "stream_icon"),
                categoryId = o.optString("category_id"),
                plot = o.optString("plot", "").takeIf { it.isNotBlank() },
                rating = o.optString("rating", "").takeIf { it.isNotBlank() && it != "0" && it != "0.0" },
                genre = o.optString("genre", "").takeIf { it.isNotBlank() },
            )
        }
    }

    suspend fun seriesInfo(seriesId: String): XtSeriesInfo = withContext(Dispatchers.IO) {
        val text = jsonGet("get_series_info", mapOf("series_id" to seriesId))
        val root = runCatching { JSONObject(text) }.getOrElse { throw IOException("BADRESPONSE") }
        val episodesJson = root.optJSONObject("episodes")
        val grouped = linkedMapOf<Int, MutableList<XtEpisode>>()
        if (episodesJson != null) {
            val keys = (0 until episodesJson.length()).map { it.toString() }
            for (key in keys) {
                val season = key.toIntOrNull() ?: continue
                val arr = episodesJson.optJSONArray(key) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val info = o.optJSONObject("info")
                    val seasonNum = o.optInt("season", season)
                    grouped.getOrPut(seasonNum) { mutableListOf() }.add(
                        XtEpisode(
                            id = id,
                            title = clean(o.optString("title")),
                            season = seasonNum,
                            number = o.optInt("episode_num", 0),
                            extension = o.optString("container_extension", "mp4").ifBlank { "mp4" },
                            image = info?.let { MirnahCodes.opt(it, "movie_image", "cover_big") },
                            durationSecs = info?.optLong("duration_secs", 0L)?.takeIf { it > 0 },
                            plot = info?.optString("plot", "")?.takeIf { it.isNotBlank() },
                        )
                    )
                }
            }
        }
        grouped.values.forEach { it.sortBy { e -> e.number } }
        XtSeriesInfo(seasons = grouped.keys.sorted(), episodes = grouped)
    }

    // ---- stream URLs ------------------------------------------------------

    /** Live channels on this server are MPEG-TS (m3u8 is rejected with 403). */
    fun liveUrl(streamId: String, extension: String = "ts"): String =
        "$base/live/$username/$password/$streamId.$extension"

    fun movieUrl(streamId: String, extension: String): String =
        "$base/movie/$username/$password/$streamId.$extension"

    fun episodeUrl(episodeId: String, extension: String): String =
        "$base/series/$username/$password/$episodeId.$extension"

    // ---- plumbing ---------------------------------------------------------

    private suspend fun categories(action: String): List<XtCategory> = withContext(Dispatchers.IO) {
        val arr = arrayGet(action)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("category_id")
            if (id.isBlank()) null else XtCategory(id = id, name = clean(o.optString("category_name")))
        }
    }

    private suspend fun arrayGet(action: String, params: Map<String, String> = emptyMap()): JSONArray {
        val text = jsonGet(action, params)
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) {
            // Some servers answer errors as {"status":"Failed"...}
            throw IOException("BADRESPONSE")
        }
        return runCatching { JSONArray(trimmed) }.getOrElse { throw IOException("BADRESPONSE") }
    }

    private suspend fun jsonGet(action: String?, params: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = "$base/player_api.php".toHttpUrlOrNull()?.newBuilder()
                ?: throw IOException("BADDNS")
            builder.addQueryParameter("username", username)
            builder.addQueryParameter("password", password)
            if (action != null) builder.addQueryParameter("action", action)
            params.forEach { (k, v) -> builder.addQueryParameter(k, v) }
            val request = Request.Builder()
                .url(builder.build())
                .header("User-Agent", Net.USER_AGENT)
                .build()
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP${response.code}")
                response.body?.string() ?: throw IOException("BADRESPONSE")
            }
        }

    private companion object {
        /** Tidy IPTV names: collapse blank runs and trim pipe/space noise. */
        fun clean(raw: String): String = raw
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim(' ', '|')
            .ifBlank { raw.trim() }
    }
}
