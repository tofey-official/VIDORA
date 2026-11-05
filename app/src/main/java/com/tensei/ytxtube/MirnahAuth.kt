package com.tensei.ytxtube

import android.content.Context
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved Mirnah session — an activated code bound to its Xtream server.
 */
data class MirnahSession(
    val code: String,
    val dns: String,
    val username: String,
    val password: String,
    val type: String,        // "vip" / "free"
    val expiresAt: String?,  // raw date string from the panel, null = unlimited
    val notes: String,
) {
    val isVip: Boolean get() = type == "vip"
    val expiresInMillis: Long? get() = MirnahCodes.parseDate(expiresAt)
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresInMillis?.let { it < now } ?: false
}

/** Outcome of a code login attempt, with Arabic messages ready for the UI. */
sealed class MirnahLoginResult {
    data class Success(val session: MirnahSession) : MirnahLoginResult()
    data class Failure(val message: String) : MirnahLoginResult()
}

/**
 * Code authentication for مرناة:
 * fetches the codes list from the panel frontend (Cloudflare Pages) and
 * validates a code exactly like the panel's api/validate.php does —
 * unknown code, already used, expired or not active are all rejected.
 *
 * VIP and free codes go through the same login; the type only decides the
 * badge shown on the subscription card.
 */
object MirnahAuth {

    private const val PREFS = "mirnah_session"
    private const val KEY = "session"

    fun load(context: Context): MirnahSession? {
        val store = SecureStore(context, PREFS)
        val raw = store.get(KEY) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val s = MirnahSession(
                code = o.optString("code"),
                dns = o.optString("dns"),
                username = o.optString("username"),
                password = o.optString("password"),
                type = o.optString("type", "free").ifBlank { "free" },
                expiresAt = o.optString("expires_at", "").takeIf { it.isNotBlank() },
                notes = o.optString("notes"),
            )
            if (s.code.isBlank() || s.dns.isBlank() || s.isExpired()) null else s
        }.getOrElse {
            store.clear()
            null
        }
    }

    fun logout(context: Context) {
        SecureStore(context, PREFS).clear()
    }

    private fun save(context: Context, session: MirnahSession) {
        val o = JSONObject()
            .put("code", session.code)
            .put("dns", session.dns)
            .put("username", session.username)
            .put("password", session.password)
            .put("type", session.type)
            .put("expires_at", session.expiresAt ?: "")
            .put("notes", session.notes)
        SecureStore(context, PREFS).put(KEY, o.toString())
    }

    /**
     * Validate [rawCode] against the remote codes.json and activate it.
     * Network work runs on the caller's dispatcher.
     */
    fun login(context: Context, rawCode: String): MirnahLoginResult {
        val code = rawCode.trim()
        if (code.isEmpty()) return MirnahLoginResult.Failure("أدخل الكود أولاً")

        val entries = MirnahCodes.fetch(context)
        return when {
            entries == null ->
                MirnahLoginResult.Failure("تعذّر الاتصال بالخادم، تأكد من الإنترنت وحاول مجدداً")
            entries is MirnahCodes.Bad ->
                MirnahLoginResult.Failure("ملف الأكواد على الخادم غير صالح أو مشفّر، راجع مدير اللوحة")
            else -> {
                val entry = (entries as MirnahCodes.Ok).list
                    .firstOrNull { it.optString("code", "").trim().equals(code, ignoreCase = true) }
                    ?: return MirnahLoginResult.Failure("الكود غير صحيح، تأكد من الكود وحاول مجدداً")

                val status = entry.optString("status", "active").lowercase(Locale.ROOT)
                val expires = MirnahCodes.opt(entry, "expires_at", "expire_date", "expires", "expiry")
                if (status == "used") {
                    return MirnahLoginResult.Failure("هذا الكود مستخدم من قبل")
                }
                val expMs = MirnahCodes.parseDate(expires)
                if (expMs != null && expMs < System.currentTimeMillis()) {
                    return MirnahLoginResult.Failure("انتهت صلاحية هذا الكود")
                }
                if (status != "active") {
                    return MirnahLoginResult.Failure("هذا الكود غير مفعّل حالياً")
                }

                val dns = MirnahCodes.normalizeDns(
                    MirnahCodes.opt(entry, "dns", "server", "host", "panel_dns") ?: ""
                )
                val username = MirnahCodes.opt(entry, "username", "user", "xtream_user") ?: ""
                val password = MirnahCodes.opt(entry, "password", "pass", "xtream_pass") ?: ""
                if (dns.isBlank() || username.isBlank() || password.isBlank()) {
                    return MirnahLoginResult.Failure("هذا الكود لا يحتوي بيانات خادم صالحة")
                }

                val typeRaw = (MirnahCodes.opt(entry, "type", "plan") ?: "")
                    .lowercase(Locale.ROOT)
                val type = when {
                    typeRaw == "vip" || typeRaw == "premium" -> "vip"
                    typeRaw == "free" || typeRaw == "free_trial" -> "free"
                    expires == null -> "vip"   // permanent codes are the premium tier
                    else -> "free"
                }

                val session = MirnahSession(
                    code = entry.optString("code").trim().ifBlank { code },
                    dns = dns,
                    username = username,
                    password = password,
                    type = type,
                    expiresAt = expires,
                    notes = MirnahCodes.opt(entry, "notes", "note") ?: "",
                )
                save(context, session)
                MirnahLoginResult.Success(session)
            }
        }
    }
}

/** Remote codes.json access + tolerant parsing of the panel's format. */
object MirnahCodes {

    private const val CODES_URL = "https://vidora1.pages.dev/codes.json"

    sealed class Fetch {
        data class Ok(val list: List<JSONObject>) : Fetch()
        object Bad : Fetch()
    }

    /** Fetch and parse the codes file; null = network failure, Bad = not valid JSON. */
    fun fetch(context: Context): Fetch? {
        return runCatching {
            val request = Request.Builder()
                .url(CODES_URL)
                .header("User-Agent", Net.USER_AGENT)
                .build()
            Net.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Fetch.Bad
                val body = response.body?.string() ?: return Fetch.Bad
                val trimmed = body.trim().trimStart('\uFEFF')
                if (!trimmed.startsWith("[") && !trimmed.startsWith("{")) return Fetch.Bad
                val list = when (val parsed = parseJson(trimmed)) {
                    is JSONArray -> (0 until parsed.length()).map { parsed.getJSONObject(it) }
                    is JSONObject -> listOf(parsed)
                    else -> return Fetch.Bad
                }
                Fetch.Ok(list)
            }
        }.getOrElse {
            if (it is IOException) null else Fetch.Bad
        }
    }

    private fun parseJson(text: String): Any? = runCatching {
        when {
            text.startsWith("[") -> JSONArray(text)
            else -> JSONObject(text)
        }
    }.getOrNull()

    /** First non-blank value among several possible keys. */
    fun opt(o: JSONObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k ->
            val v = o.optString(k, "")
            if (v.isNotBlank() && v != "null") v else null
        }

    fun normalizeDns(raw: String): String {
        var dns = raw.trim()
        if (dns.isEmpty()) return dns
        if (!dns.startsWith("http://") && !dns.startsWith("https://")) dns = "http://$dns"
        return dns.trimEnd('/')
    }

    /** Parse the panel's date strings; all-digit values are treated as epoch millis. */
    fun parseDate(raw: String?): Long? {
        val value = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        if (value.all { it.isDigit() }) {
            val asMillis = value.toLongOrNull() ?: return null
            return if (value.length <= 10) asMillis * 1000 else asMillis
        }
        val formats = arrayOf(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy HH:mm", "dd/MM/yyyy",
        )
        for (format in formats) {
            runCatching {
                val parser = SimpleDateFormat(format, Locale.US)
                parser.isLenient = false
                val date: Date = parser.parse(value) ?: return@runCatching
                return date.time
            }
        }
        return null
    }
}
