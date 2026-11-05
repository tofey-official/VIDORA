package com.tensei.ytxtube

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the activation and session state for the "مرناة" live TV feature.
 */
object MirnahManager {
    private const val PREFS_NAME = "mirnah_settings"
    private const val KEY_ACTIVATION_CODE = "activation_code"
    private const val KEY_IS_ACTIVE = "is_active"

    fun getSavedCode(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVATION_CODE, null)
    }

    fun isActivated(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_ACTIVE, false)
    }

    fun saveActivation(context: Context, code: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ACTIVATION_CODE, code)
            .putBoolean(KEY_IS_ACTIVE, true)
            .apply()
    }

    fun logout(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ACTIVATION_CODE)
            .putBoolean(KEY_IS_ACTIVE, false)
            .apply()
    }
}

/**
 * Model representing a live TV channel.
 */
data class MirnahChannel(
    val id: String,
    val name: String,
    val category: String,
    val resolution: String = "1080p FHD",
    val isLive: Boolean = true,
)

/** Same live red used by DurationPill across the app. */
private val MirnahLiveRed = Color(0xFFCC0000)

/**
 * Main Mirnah Screen, drawn entirely with the shared UiKit vocabulary
 * (ScreenHeader, EmptyState, SectionTitle, theme chips and pill buttons)
 * so it looks like every other page of the app.
 *
 * If not activated: shows the standard app activation state with a code input.
 * If activated: shows the subscription status, category chips and live channels.
 */
@Composable
fun MirnahScreen(
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isActivated by remember { mutableStateOf(MirnahManager.isActivated(context)) }
    var currentCode by remember { mutableStateOf(MirnahManager.getSavedCode(context) ?: "") }
    var inputCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Categories (plain text, matching the app's chip rows).
    val categories = remember {
        listOf("الكل", "رياضة", "أخبار", "ترفيه", "وثائقي", "أطفال")
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Sample live channels for demonstration.
    val sampleChannels = remember {
        listOf(
            MirnahChannel("1", "beIN SPORTS 1 HD", "رياضة", "4K HDR"),
            MirnahChannel("2", "beIN SPORTS 2 HD", "رياضة", "1080p FHD"),
            MirnahChannel("3", "SSC 1 Sports HD", "رياضة", "1080p FHD"),
            MirnahChannel("4", "العربية الحدث", "أخبار", "1080p FHD"),
            MirnahChannel("5", "الجزيرة الإخبارية", "أخبار", "1080p FHD"),
            MirnahChannel("6", "MBC 1 HD", "ترفيه", "1080p FHD"),
            MirnahChannel("7", "MBC العراق HD", "ترفيه", "1080p FHD"),
            MirnahChannel("8", "ناشيونال جيوغرافيك أبوظبي", "وثائقي", "1080p FHD"),
            MirnahChannel("9", "سبيستون SpaceToon", "أطفال", "1080p FHD"),
            MirnahChannel("10", "كارتون نتورك بالعربية", "أطفال", "1080p FHD"),
        )
    }

    val filteredChannels = remember(selectedCategoryIndex) {
        if (selectedCategoryIndex == 0) sampleChannels
        else sampleChannels.filter { it.category == categories[selectedCategoryIndex] }
    }

    CollapsingHeaderLayout(
        resetKey = scrollToTop to isActivated,
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
            if (!isActivated) {
                // ----------------------------------------------
                // Not activated: the same EmptyState pattern used
                // everywhere else in the app.
                // ----------------------------------------------
                item(key = "activation") {
                    EmptyState(
                        icon = Icons.Rounded.LiveTv,
                        title = "مرناة",
                        body = "أدخل كود الاشتراك لتفعيل البث التلفزيوني المباشر",
                    ) {
                        ActivationCard(
                            inputCode = inputCode,
                            onInputCodeChange = {
                                inputCode = it
                                errorMessage = null
                            },
                            errorMessage = errorMessage,
                            isLoading = isLoading,
                            onClear = {
                                inputCode = ""
                                errorMessage = null
                            },
                            onPaste = {
                                runCatching {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                    if (!clip.isNullOrBlank()) {
                                        inputCode = clip
                                        errorMessage = null
                                    }
                                }
                            },
                            onActivate = {
                                if (inputCode.isBlank()) {
                                    errorMessage = "يرجى إدخال كود الاشتراك أولاً"
                                } else {
                                    isLoading = true
                                    scope.launch {
                                        delay(700)
                                        MirnahManager.saveActivation(context, inputCode)
                                        currentCode = inputCode
                                        isActivated = true
                                        isLoading = false
                                    }
                                }
                            },
                        )
                    }
                }
            } else {
                // ----------------------------------------------
                // Activated: subscription status, category chips
                // and the live channel list.
                // ----------------------------------------------
                item(key = "subscription_status") {
                    SubscriptionStatusCard(
                        code = currentCode,
                        onChange = {
                            MirnahManager.logout(context)
                            isActivated = false
                            currentCode = ""
                            inputCode = ""
                        },
                    )
                }

                item(key = "categories") {
                    MirnahCategoryChips(
                        categories = categories,
                        selected = selectedCategoryIndex,
                        onSelect = { selectedCategoryIndex = it },
                    )
                }

                item(key = "section_title") {
                    SectionTitle("القنوات المباشرة")
                }

                items(filteredChannels, key = { it.id }) { channel ->
                    ChannelCard(
                        channel = channel,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            Toast.makeText(context, "جاري فتح ${channel.name}...", Toast.LENGTH_SHORT).show()
                        },
                    )
                }

                item(key = "bottom_space") {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Code input and activate button, styled like the app's shared
 * fields and PillButton (loading state replaces the label).
 */
@Composable
private fun ActivationCard(
    inputCode: String,
    onInputCodeChange: (String) -> Unit,
    errorMessage: String?,
    isLoading: Boolean,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onActivate: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Code input field.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(c.surfaceContainerLow)
                .border(
                    width = 1.dp,
                    color = if (errorMessage != null) c.error else c.outlineVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.VpnKey,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(20.dp),
            )

            Spacer(Modifier.width(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (inputCode.isEmpty()) {
                    Text(
                        text = "مثال: VID-2026-LIVE",
                        color = c.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                BasicTextField(
                    value = inputCode,
                    onValueChange = onInputCodeChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onActivate() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (inputCode.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Rounded.Clear,
                    contentDescription = "Clear",
                    tint = c.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClear),
                )
            }

            Spacer(Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Rounded.ContentPaste,
                contentDescription = "Paste",
                tint = c.primary,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPaste),
            )
        }

        val error = errorMessage
        if (error != null) {
            Text(
                text = error,
                color = c.error,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        // Primary action pill — same shape and colors as PillButton,
        // with a spinner while the code is being verified.
        Box(
            modifier = Modifier
                .pressable(enabled = !isLoading, onClick = onActivate)
                .height(44.dp)
                .clip(CircleShape)
                .background(if (isLoading) c.primary.copy(alpha = 0.6f) else c.primary)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = c.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = c.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "تفعيل الدخول",
                        color = c.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/**
 * Subscription status card: SunnyShape icon box, masked code and a
 * CircleAction to change the code — the app's standard row vocabulary.
 */
@Composable
private fun SubscriptionStatusCard(code: String, onChange: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val masked = if (code.length > 6) "${code.take(4)}••••${code.takeLast(2)}" else "••••"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.7f))
            .border(1.dp, c.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(SunnyShape)
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "الاشتراك نشط",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = c.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "الكود: $masked",
                fontSize = 11.5.sp,
                color = c.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }

        CircleAction(
            icon = Icons.Rounded.Logout,
            contentDescription = "تغيير الكود",
            onClick = onChange,
        )
    }
}

/**
 * Category chips using exactly the HomeFilterChips style of the home page.
 */
@Composable
private fun MirnahCategoryChips(
    categories: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val isDark = isDarkTheme()
    val isAmoled = ThemeSettings.mode == "amoled"
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories.size) { index ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> if (isDark || isAmoled) Color.White else Color.Black
                            isAmoled -> Color(0xFF161616)
                            isDark -> Color(0xFF272727)
                            else -> Color(0xFFE5E5E5)
                        }
                    )
                    .pressable(pressedScale = 0.94f) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = categories[index],
                    color = when {
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

/**
 * Channel row card using theme colors only (primaryContainer icon box,
 * live dot in the app's canonical live red, primary play pill).
 */
@Composable
private fun ChannelCard(
    channel: MirnahChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(1.dp, c.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel icon box.
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = c.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.isLive) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MirnahLiveRed),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "مباشر",
                        fontSize = 11.sp,
                        color = MirnahLiveRed,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = channel.resolution,
                    fontSize = 10.5.sp,
                    color = c.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Play pill button.
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = c.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
