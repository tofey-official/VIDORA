package com.tensei.ytxtube

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Same live red used by DurationPill across the app. */
private val MirnahLiveRed = Color(0xFFCC0000)

/** VIP badge gold and free-badge green, matching the app's dark palette. */
private val MirnahVipGold = Color(0xFFD9A62E)
private val MirnahFreeGreen = Color(0xFF3FAE6A)

/**
 * Dark cinematic gradients used as the fallback behind covers that failed
 * to load (many IPTV logos are missing) — mirrors the approved design.
 */
private val MirnahThumbGradients: List<List<Color>> = listOf(
    listOf(Color(0xFF54233F), Color(0xFF833761), Color(0xFF1C1017)),
    listOf(Color(0xFF123A35), Color(0xFF1F8A70), Color(0xFF0A1613)),
    listOf(Color(0xFF2A1064), Color(0xFF6C2BD9), Color(0xFF12081F)),
    listOf(Color(0xFF0D3B1E), Color(0xFF1E7A3C), Color(0xFF06130B)),
    listOf(Color(0xFF4A1010), Color(0xFF9E1C1C), Color(0xFF150404)),
    listOf(Color(0xFF1C2A3D), Color(0xFF3B5BDB), Color(0xFF0A0F1A)),
    listOf(Color(0xFF3D2610), Color(0xFFB0714A), Color(0xFF17100A)),
)

// ---------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------

/**
 * مرناة — the IPTV home of VIDORA.
 * Not activated: code login (VIP + free codes validated against the panel's
 * codes.json). Activated: movies & series + live streams from Xtream.
 */
@Composable
fun MirnahScreen(
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
    onPlay: (VideoItem) -> Unit = {},
) {
    val context = LocalContext.current
    var session by remember { mutableStateOf<MirnahSession?>(null) }
    var restoring by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        session = MirnahAuth.load(context)
        restoring = false
    }

    when {
        restoring -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
        }
        else -> {
            val active = session
            if (active == null) {
                MirnahLogin(contentPadding, onSearch, onAccount, onActivated = { session = it })
            } else {
                MirnahContent(
                    session = active,
                    contentPadding = contentPadding,
                    onSearch = onSearch,
                    onAccount = onAccount,
                    scrollToTop = scrollToTop,
                    onPlay = onPlay,
                    onLogout = { session = null },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Code login (VIP + free)
// ---------------------------------------------------------------------------

@Composable
private fun MirnahLogin(
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    onActivated: (MirnahSession) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputCode by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun submit() {
        if (isLoading) return
        if (inputCode.isBlank()) {
            errorMessage = "يرجى إدخال كود الاشتراك أولاً"
            return
        }
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { MirnahAuth.login(context, inputCode) }
            isLoading = false
            when (result) {
                is MirnahLoginResult.Success -> onActivated(result.session)
                is MirnahLoginResult.Failure -> errorMessage = result.message
            }
        }
    }

    CollapsingHeaderLayout(
        header = {
            ScreenHeader(title = "مرناة", brandMark = false) {
                HeaderActions(onSearch, onAccount)
            }
        },
    ) { top ->
        LazyColumn(
            contentPadding = contentPadding.withTop(top),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "top_space") { Spacer(Modifier.height(28.dp)) }

            // Glowing TV icon capsule
            item(key = "brand") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFF334B).copy(alpha = 0.35f),
                                        Color(0xFFFF334B).copy(alpha = 0.08f),
                                        Color.Transparent,
                                    )
                                )
                            )
                            .border(1.dp, Color(0xFFFF334B).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LiveTv,
                            contentDescription = null,
                            tint = Color(0xFFFF334B),
                            modifier = Modifier.size(42.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "مرناة IPTV",
                        fontFamily = RoundedHeavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = "أدخل كود الاشتراك لتفعيل الأفلام والمسلسلات والبث المباشر",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }

            // Glass input card
            item(key = "login_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(20.dp),
                ) {
                    Text(
                        text = "رمز الدخول / كود التفعيل",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(
                                width = 1.dp,
                                color = if (errorMessage != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(14.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )

                        Spacer(Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (inputCode.isEmpty()) {
                                Text(
                                    text = "مثال: VID-2026-VIP",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            BasicTextField(
                                value = inputCode,
                                onValueChange = {
                                    inputCode = it.trim()
                                    errorMessage = null
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (inputCode.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "مسح",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable { inputCode = ""; errorMessage = null },
                            )
                            Spacer(Modifier.width(8.dp))
                        }

                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = "لصق",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable {
                                    runCatching {
                                        val clipboard = context
                                            .getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                        if (!clip.isNullOrBlank()) {
                                            inputCode = clip
                                            errorMessage = null
                                        }
                                    }
                                },
                        )
                    }

                    if (errorMessage != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.primary
                            )
                            .pressable(enabled = !isLoading) { submit() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "تسجيل الدخول",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "hint") {
                Text(
                    text = "يدعم أكواد VIP والأكواد المجانية — الكود يفتح الأفلام والمسلسلات والبث المباشر دفعة واحدة",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 14.dp),
                )
            }

            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Content (Xtream) — MARKER
// ---------------------------------------------------------------------------

/** Loaded Xtream content for the whole session. */
private sealed interface MirnahContentState {
    object Loading : MirnahContentState
    data class Error(val message: String) : MirnahContentState
    data class Ready(
        val vodCats: List<XtCategory>,
        val serCats: List<XtCategory>,
        val liveCats: List<XtCategory>,
        val movies: Map<String, List<XtMovie>> = emptyMap(),
        val series: Map<String, List<XtSeries>> = emptyMap(),
        val live: Map<String, List<XtLive>> = emptyMap(),
    ) : MirnahContentState
}

/** Episodes of the currently opened series. */
private sealed interface MirnahEpisodesState {
    object Idle : MirnahEpisodesState
    object Loading : MirnahEpisodesState
    data class Error(val message: String) : MirnahEpisodesState
    data class Ready(val info: XtSeriesInfo) : MirnahEpisodesState
}

/** One selectable chip in the filter rows. */
private data class MirnahChip(val key: String, val label: String, val isKind: Boolean = false, val active: Boolean = false)

@Composable
private fun MirnahContent(
    session: MirnahSession,
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int,
    onPlay: (VideoItem) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val cacheDir = remember { MirnahCache.dir(context) }
    val xt = remember(session.dns to session.username, cacheDir) {
        XtreamClient(session.dns, session.username, session.password, cacheDir)
    }
    // Warm the IPTV host connection right before playback so weak networks get the
    // same head start (DNS+TCP already open) YouTube items already get.
    val playXt: (VideoItem) -> Unit = { item ->
        Net.prewarm(xt.prewarmUrl())
        onPlay(item)
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 = أفلام، 1 = مسلسلات
    var vodCat by rememberSaveable { mutableStateOf<String?>(null) }
    var serCat by rememberSaveable { mutableStateOf<String?>(null) }
    var liveCat by rememberSaveable { mutableStateOf<String?>(null) }
    var openSeries by remember { mutableStateOf<XtSeries?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var episodesRetry by remember { mutableIntStateOf(0) }

    var state by remember { mutableStateOf<MirnahContentState>(MirnahContentState.Loading) }
    var episodesState by remember { mutableStateOf<MirnahEpisodesState>(MirnahEpisodesState.Idle) }
    var catLoading by remember { mutableStateOf(false) }
    // Set right before a manual refresh / retry so the next load bypasses the
    // .txt cache and re-downloads everything once; consumed by the item loader.
    var forceFresh by remember { mutableStateOf(false) }

    // Phase 1 — categories only (a few KB each) so the screen appears fast.
    // Categories + category items are cached as .txt files (MirnahCache), so on
    // every reopen they come from disk instantly — no re-downloading. [reloadKey]
    // (manual refresh / retry) forces one fresh network pass that re-saves the cache.
    LaunchedEffect(session.dns, session.username, reloadKey) {
        state = MirnahContentState.Loading
        openSeries = null
        vodCat = null
        serCat = null
        liveCat = null
        val fresh = forceFresh
        forceFresh = false
        // Auth stays a live call (tiny), but only a real rejection stops the
        // screen — a network hiccup should not block cached content below.
        val auth = runCatching { xt.checkAuth() }
        if (auth.exceptionOrNull() is XtreamAuthException) {
            state = MirnahContentState.Error(friendlyXtreamError(auth.exceptionOrNull()!!))
            return@LaunchedEffect
        }
        val result = runCatching {
            coroutineScope {
                val vodCatsD = async { xt.vodCategories(fresh) }
                val serCatsD = async { xt.seriesCategories(fresh) }
                val liveCatsD = async { xt.liveCategories(fresh) }
                MirnahContentState.Ready(
                    vodCats = vodCatsD.await(),
                    serCats = serCatsD.await(),
                    liveCats = liveCatsD.await(),
                )
            }
        }
        result.fold(
            onSuccess = { ready ->
                // Warm the host so the first tap doesn't pay DNS+TCP latency.
                Net.prewarm(xt.prewarmUrl())
                // Open on the first category instead of a full "الكل" dump.
                vodCat = ready.vodCats.firstOrNull()?.id
                serCat = ready.serCats.firstOrNull()?.id
                liveCat = ready.liveCats.firstOrNull()?.id
                state = ready
            },
            onFailure = {
                state = MirnahContentState.Error(friendlyXtreamError(auth.exceptionOrNull() ?: it))
            },
        )
    }

    // Phase 2 — fetch the selected category's items on demand (tens of KB);
    // they are cached to .txt files, so a category visited once loads instantly
    // on every later app start.
    LaunchedEffect(state, selectedTab, kind, vodCat, serCat, liveCat) {
        val ready = state as? MirnahContentState.Ready ?: return@LaunchedEffect
        val fresh = forceFresh
        forceFresh = false
        val target = when (selectedTab) {
            0 -> if (kind == 0) vodCat else serCat
            else -> liveCat
        } ?: return@LaunchedEffect
        val cached = when (selectedTab) {
            0 -> if (kind == 0) ready.movies.containsKey(target) else ready.series.containsKey(target)
            else -> ready.live.containsKey(target)
        }
        if (cached && !fresh) return@LaunchedEffect
        catLoading = true
        runCatching {
            when (selectedTab) {
                0 -> if (kind == 0) {
                    val list = xt.vodStreams(target, fresh)
                    state = ready.copy(movies = ready.movies + (target to list))
                } else {
                    val list = xt.series(target, fresh)
                    state = ready.copy(series = ready.series + (target to list))
                }
                else -> {
                    val list = xt.liveStreams(target, fresh)
                    state = ready.copy(live = ready.live + (target to list))
                }
            }
        }.onFailure { state = MirnahContentState.Error(friendlyXtreamError(it)) }
        catLoading = false
    }

    LaunchedEffect(openSeries?.id, episodesRetry) {
        val series = openSeries
        if (series == null) {
            episodesState = MirnahEpisodesState.Idle
            return@LaunchedEffect
        }
        episodesState = MirnahEpisodesState.Loading
        runCatching { xt.seriesInfo(series.id, fresh = episodesRetry > 0) }
            .onSuccess { episodesState = MirnahEpisodesState.Ready(it) }
            .onFailure { episodesState = MirnahEpisodesState.Error(friendlyXtreamError(it)) }
    }

    val ready = state as? MirnahContentState.Ready
    val vodCatNames = remember(state) { ready?.vodCats?.associate { it.id to it.name } ?: emptyMap() }
    val serCatNames = remember(state) { ready?.serCats?.associate { it.id to it.name } ?: emptyMap() }
    val liveCatNames = remember(state) { ready?.liveCats?.associate { it.id to it.name } ?: emptyMap() }

    val filteredMovies = remember(state, vodCat) {
        val r = state as? MirnahContentState.Ready ?: return@remember emptyList<XtMovie>()
        vodCat?.let { r.movies[it] } ?: emptyList()
    }
    val filteredSeries = remember(state, serCat) {
        val r = state as? MirnahContentState.Ready ?: return@remember emptyList<XtSeries>()
        serCat?.let { r.series[it] } ?: emptyList()
    }
    val filteredLive = remember(state, liveCat) {
        val r = state as? MirnahContentState.Ready ?: return@remember emptyList<XtLive>()
        liveCat?.let { r.live[it] } ?: emptyList()
    }

    // Chip rows per tab
    val chips: List<MirnahChip> = when (selectedTab) {
        0 -> {
            val kindChips = listOf(
                MirnahChip("kind:0", "أفلام", isKind = true, active = kind == 0),
                MirnahChip("kind:1", "مسلسلات", isKind = true, active = kind == 1),
            )
            val cats = (if (kind == 0) ready?.vodCats else ready?.serCats).orEmpty()
            kindChips + cats.map { MirnahChip("cat:${it.id}", it.name) }
        }
        else -> {
            val cats = ready?.liveCats.orEmpty()
            cats.map { MirnahChip("cat:${it.id}", it.name) }
        }
    }
    val selectedChipKey = when (selectedTab) {
        0 -> (if (kind == 0) vodCat else serCat)?.let { "cat:$it" } ?: "cat:null"
        else -> liveCat?.let { "cat:$it" } ?: "cat:null"
    }

    CollapsingHeaderLayout(
        resetKey = listOf(scrollToTop, selectedTab, openSeries?.id),
        header = {
            ScreenHeader(title = "مرناة", brandMark = false) {
                HeaderActions(onSearch, onAccount)
            }
        },
    ) { top ->
        LazyColumn(
            contentPadding = contentPadding.withTop(top),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "account") {
                MirnahAccountStrip(session = session, onLogout = {
                    MirnahCache.clear(context)
                    MirnahAuth.logout(context)
                    onLogout()
                })
            }

            item(key = "tabs") {
                MirnahTabRow(
                    selected = selectedTab,
                    onSelect = { selectedTab = it; if (it == 1) openSeries = null },
                    onRefresh = {
                        openSeries = null
                        episodesRetry = 0
                        forceFresh = true
                        reloadKey++
                    },
                    refreshing = state is MirnahContentState.Loading || catLoading,
                )
            }

            val showChips = state !is MirnahContentState.Error && (openSeries == null || selectedTab == 1)
            if (showChips) {
                item(key = "chips") {
                    MirnahChips(
                        chips = chips,
                        selectedKey = selectedChipKey,
                        onSelect = { key ->
                            when {
                                key.startsWith("kind:") -> kind = key.removePrefix("kind:").toIntOrNull() ?: 0
                                key == "cat:null" -> when (selectedTab) {
                                    0 -> if (kind == 0) vodCat = null else serCat = null
                                    else -> liveCat = null
                                }
                                else -> {
                                    val id = key.removePrefix("cat:")
                                    when (selectedTab) {
                                        0 -> if (kind == 0) vodCat = id else serCat = id
                                        else -> liveCat = id
                                    }
                                }
                            }
                        },
                    )
                }
            }

            when {
                openSeries != null && selectedTab == 0 -> {
                    val series = openSeries
                    if (series != null) {
                        item(key = "back") {
                            MirnahBackRow(onBack = { openSeries = null })
                        }
                        item(key = "series_header") {
                            MirnahSeriesHeader(series, serCatNames[series.categoryId])
                        }
                        when (val eps = episodesState) {
                            MirnahEpisodesState.Loading -> item(key = "eps_loading") { MirnahLoadingRow("جاري تحميل الحلقات…") }
                            is MirnahEpisodesState.Error -> item(key = "eps_error") {
                                MirnahErrorCard(eps.message, retryLabel = "إعادة المحاولة") { episodesRetry++ }
                            }
                            is MirnahEpisodesState.Ready -> {
                                if (eps.info.seasons.isEmpty()) {
                                    item(key = "eps_empty") { MirnahEmptyRow("لا توجد حلقات متاحة لهذا المسلسل") }
                                } else {
                                    eps.info.seasons.forEach { season ->
                                        item(key = "season_$season") { MirnahSeasonHeader(season) }
                                        items(eps.info.episodes[season].orEmpty(), key = { "e${it.id}" }) { ep ->
                                            MirnahEpisodeRow(
                                                episode = ep,
                                                modifier = Modifier.animateItem(),
                                                onClick = { playXt(ep.videoItem(xt, series)) },
                                            )
                                        }
                                    }
                                }
                            }
                            MirnahEpisodesState.Idle -> {}
                        }
                    }
                }
                state is MirnahContentState.Loading -> item(key = "loading") { MirnahLoadingRow("جاري تحميل الأقسام…") }
                state is MirnahContentState.Error -> item(key = "error") {
                    MirnahErrorCard((state as MirnahContentState.Error).message, retryLabel = "إعادة المحاولة") { reloadKey++ }
                }
                catLoading -> item(key = "cat_loading") { MirnahLoadingRow("جاري تحميل القسم…") }
                selectedTab == 0 -> {
                    if (kind == 0) {
                        if (filteredMovies.isEmpty()) item(key = "empty_m") { MirnahEmptyRow("لا يوجد محتوى في هذا القسم") }
                        items(filteredMovies, key = { "m${it.id}" }) { movie ->
                            MirnahMovieCard(
                                movie = movie,
                                catName = vodCatNames[movie.categoryId],
                                modifier = Modifier.animateItem(),
                                onClick = { playXt(movie.videoItem(xt)) },
                            )
                        }
                    } else {
                        if (filteredSeries.isEmpty()) item(key = "empty_s") { MirnahEmptyRow("لا يوجد محتوى في هذا القسم") }
                        items(filteredSeries, key = { "s${it.id}" }) { series ->
                            MirnahSeriesCard(
                                series = series,
                                catName = serCatNames[series.categoryId],
                                modifier = Modifier.animateItem(),
                                onClick = { openSeries = series },
                            )
                        }
                    }
                }
                else -> {
                    if (filteredLive.isEmpty()) item(key = "empty_l") { MirnahEmptyRow("لا توجد قنوات في هذا القسم") }
                    items(filteredLive, key = { "l${it.id}" }) { channel ->
                        MirnahLiveCard(
                            channel = channel,
                            catName = liveCatNames[channel.categoryId],
                            modifier = Modifier.animateItem(),
                            onClick = { playXt(channel.videoItem(xt)) },
                        )
                    }
                }
            }

            item(key = "bottom_space") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards & rows
// ---------------------------------------------------------------------------

/** Account strip: code + VIP/free badge + expiry + logout. */
@Composable
private fun MirnahAccountStrip(session: MirnahSession, onLogout: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val expiryText = MirnahCodes.parseDate(session.expiresAt)
        ?.let { formatDate(it) }
        ?.let { "تنتهي بتاريخ $it" }
        ?: "بلا انتهاء"
    val planText = if (session.isVip) "اشتراك VIP" else "اشتراك مجاني"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.65f))
            .border(
                width = 1.dp,
                color = c.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (session.isVip) MirnahVipGold else MirnahFreeGreen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = session.code.trim().firstOrNull()?.toString() ?: "م",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.code,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = c.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                MirnahTypeBadge(session.isVip)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = "$planText • $expiryText",
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Icon(
            imageVector = Icons.Rounded.Logout,
            contentDescription = "تسجيل خروج",
            tint = c.error,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable { onLogout() },
        )
    }
}

@Composable
private fun MirnahTypeBadge(isVip: Boolean) {
    val color = if (isVip) MirnahVipGold else MirnahFreeGreen
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text = if (isVip) "VIP" else "مجاني",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

/**
 * The two tab buttons — "أفلام ومسلسلات" and "بث مباشر" — pill-shaped
 * like the app buttons.
 */
@Composable
private fun MirnahTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MirnahTabButton(
            icon = Icons.Rounded.Movie,
            label = "أفلام ومسلسلات",
            selected = selected == 0,
            onClick = { onSelect(0) },
            modifier = Modifier.weight(1f),
        )
        MirnahTabButton(
            icon = Icons.Rounded.LiveTv,
            label = "بث مباشر",
            selected = selected == 1,
            onClick = { onSelect(1) },
            modifier = Modifier.weight(1f),
        )
        // Manual refresh: bypasses the .txt cache once and re-saves fresh data.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerHigh)
                .border(
                    width = 1.dp,
                    color = c.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                )
                .pressable(pressedScale = 0.94f, enabled = !refreshing, onClick = onRefresh),
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "تحديث البيانات",
                    tint = c.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MirnahTabButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primary else c.surfaceContainerHigh)
            .then(
                if (!selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = c.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .pressable(pressedScale = 0.96f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) c.onPrimary else c.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) c.onPrimary else c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.5.sp,
        )
    }
}

/**
 * Category chips using exactly the HomeFilterChips style of the home page;
 * kind chips (أفلام/مسلسلات) get a primary-tinted style to stand apart.
 */
@Composable
private fun MirnahChips(
    chips: List<MirnahChip>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(chips, key = { it.key }) { chip ->
            val isSelected = chip.key == selectedKey
            val kindActive = chip.isKind && chip.active
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            kindActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                            isAmoled -> Color(0xFF161616)
                            isDark -> Color(0xFF272727)
                            else -> Color(0xFFE5E5E5)
                        }
                    )
                    .pressable(pressedScale = 0.94f) { onSelect(chip.key) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip.label,
                    color = when {
                        kindActive -> MaterialTheme.colorScheme.primary
                        isSelected -> if (isDark || isAmoled) Color.Black else Color.White
                        isDark || isAmoled -> Color.White
                        else -> Color(0xFF0F0F0F)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** Fallback-gradient cover with the big centered initial letter. */
@Composable
private fun MirnahCoverThumb(
    image: String?,
    name: String,
    seed: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MirnahThumbGradients[(seed.hashCode() and 0x7FFFFFFF) % MirnahThumbGradients.size]
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    0f to colors[0],
                    0.5f to colors[1],
                    1f to colors[2],
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().firstOrNull()?.toString() ?: "م",
            color = Color.White.copy(alpha = 0.94f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
        )
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/** Small colored circle with an initial letter. */
@Composable
private fun MirnahAvatar(avatar: String, avatarColor: Color) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = avatar,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

/** Red LIVE pill shown on channel thumbnails. */
@Composable
private fun BoxScope.MirnahLivePill() {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MirnahLiveRed)
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "مباشر",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Rating pill (bottom-end of a movie cover). */
@Composable
private fun BoxScope.MirnahRatingPill(rating: String) {
    Row(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.82f))
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.StarRate,
            contentDescription = null,
            tint = MirnahVipGold,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = rating,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Movie card, YouTube-style with the real cover and rating. */
@Composable
private fun MirnahMovieCard(
    movie: XtMovie,
    catName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MirnahCoverThumb(image = movie.icon, name = movie.name, seed = movie.id) {
            if (!movie.rating.isNullOrBlank()) MirnahRatingPill(movie.rating)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = movie.name,
            color = c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                movie.added?.let { append("أضيف ${formatDate(it)}"); append(" • ") }
                append(catName ?: "فيلم")
            },
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Series card; tapping opens the episodes view. */
@Composable
private fun MirnahSeriesCard(
    series: XtSeries,
    catName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MirnahCoverThumb(image = series.cover, name = series.name, seed = series.id) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
            ) {
                Text(
                    text = "مسلسل",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = series.name,
            color = c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!series.plot.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = series.plot,
                color = c.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOfNotNull(series.rating?.let { "★ $it" }, catName).joinToString(" • "),
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Live channel card with the red live badge. */
@Composable
private fun MirnahLiveCard(
    channel: XtLive,
    catName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        MirnahCoverThumb(image = channel.icon, name = channel.name, seed = channel.id) {
            MirnahLivePill()
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = channel.name,
            color = c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MirnahAvatar(
                avatar = (catName ?: "م").trim().firstOrNull()?.toString() ?: "م",
                avatarColor = MirnahThumbGradients[(channel.id.hashCode() and 0x7FFFFFFF) % MirnahThumbGradients.size][1],
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = catName ?: "قناة مباشرة",
                color = c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "• مباشر الآن",
                color = MirnahLiveRed,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Back row for the episodes view. */
@Composable
private fun MirnahBackRow(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.97f, onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "رجوع",
            tint = c.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "رجوع إلى القائمة",
            color = c.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
        )
    }
}

/** Series detail header: poster, rating, plot. */
@Composable
private fun MirnahSeriesHeader(series: XtSeries, catName: String?) {
    val c = MaterialTheme.colorScheme
    val colors = MirnahThumbGradients[(series.id.hashCode() and 0x7FFFFFFF) % MirnahThumbGradients.size]
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        0f to colors[0],
                        0.5f to colors[1],
                        1f to colors[2],
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = series.name.trim().firstOrNull()?.toString() ?: "م",
                color = Color.White.copy(alpha = 0.94f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 30.sp,
            )
            if (!series.cover.isNullOrBlank()) {
                AsyncImage(
                    model = series.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = series.name,
                color = c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!series.rating.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.StarRate,
                        contentDescription = null,
                        tint = MirnahVipGold,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = " ${series.rating}",
                        color = c.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = catName ?: "مسلسل",
                    color = c.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!series.plot.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = series.plot,
                    color = c.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MirnahSeasonHeader(season: Int) {
    Text(
        text = "الموسم $season",
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/** Compact episode row with its number thumbnail. */
@Composable
private fun MirnahEpisodeRow(
    episode: XtEpisode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val colors = MirnahThumbGradients[(episode.id.hashCode() and 0x7FFFFFFF) % MirnahThumbGradients.size]
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        0f to colors[0],
                        0.5f to colors[1],
                        1f to colors[2],
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (episode.number > 0) "${episode.number}" else "▶",
                color = Color.White.copy(alpha = 0.94f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
            )
            if (!episode.image.isNullOrBlank()) {
                AsyncImage(
                    model = episode.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (episode.durationSecs != null && episode.durationSecs > 0) {
                Text(
                    text = formatDuration(episode.durationSecs * 1000),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title.ifBlank { "الحلقة ${episode.number}" },
                color = c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "موسم ${episode.season} • الحلقة ${episode.number}",
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (!episode.plot.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = episode.plot,
                    color = c.onSurfaceVariant,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = c.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Small shared states
// ---------------------------------------------------------------------------

@Composable
private fun MirnahLoadingRow(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun MirnahEmptyRow(label: String) {
    Text(
        text = label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 34.dp, horizontal = 24.dp),
    )
}

@Composable
private fun MirnahErrorCard(message: String, retryLabel: String, onRetry: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.error.copy(alpha = 0.08f))
            .border(1.dp, c.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = c.error,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = c.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(c.primary)
                .pressable(pressedScale = 0.95f, onClick = onRetry)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = retryLabel,
                color = c.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatDate(millis: Long): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(millis))

private fun friendlyXtreamError(t: Throwable): String {
    val msg = t.message ?: ""
    return when {
        msg == "AUTH" -> "الخادم رفض بيانات الدخول — تأكد أن الكود ما زال فعّالاً"
        msg == "BADDNS" -> "عنوان الخادم غير صالح"
        msg == "BADRESPONSE" -> "استجابة غير متوقعة من الخادم، حاول مجدداً"
        msg.startsWith("HTTP") -> "تعذّر الوصول إلى الخادم (رمز ${msg.removePrefix("HTTP")})"
        else -> "تعذّر تحميل المحتوى، تحقق من اتصالك بالإنترنت"
    }
}

private fun XtMovie.videoItem(xt: XtreamClient): VideoItem = VideoItem(
    id = "mirnah_vod_$id",
    title = name,
    channel = "مرناة IPTV",
    channelUrl = null,
    thumbnail = icon,
    avatar = null,
    durationSec = 0,
    views = 0,
    uploaded = null,
    localUri = xt.movieUrl(id, extension),
    isLive = false,
)

private fun XtLive.videoItem(xt: XtreamClient): VideoItem = VideoItem(
    id = "mirnah_live_$id",
    title = name,
    channel = "مرناة IPTV",
    channelUrl = null,
    thumbnail = icon,
    avatar = null,
    durationSec = 0,
    views = 0,
    uploaded = null,
    localUri = xt.liveUrl(id),
    isLive = true,
)

private fun XtEpisode.videoItem(xt: XtreamClient, series: XtSeries): VideoItem = VideoItem(
    id = "mirnah_ep_$id",
    title = if (number > 0) "${series.name} — الحلقة $number" else series.name,
    channel = series.name,
    channelUrl = null,
    thumbnail = image ?: series.cover,
    avatar = null,
    durationSec = durationSecs ?: 0,
    views = 0,
    uploaded = null,
    localUri = xt.episodeUrl(id, extension),
    isLive = false,
)
