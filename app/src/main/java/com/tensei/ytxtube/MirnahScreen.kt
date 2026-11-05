package com.tensei.ytxtube

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the activation and session state for the "مرناة" (IPTV) feature.
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
 * Model representing an IPTV Live Channel.
 */
data class MirnahChannel(
    val id: String,
    val name: String,
    val category: String,
    val resolution: String = "1080p FHD",
    val isLive: Boolean = true,
    val accentColor: Color = Color(0xFFE53935)
)

/**
 * Main Mirnah Screen.
 * If not activated: shows code login.
 * If activated: shows live channels and subscription info.
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

    // Categories
    val categories = remember {
        listOf("الكل", "رياضة ⚽", "أخبار 🌍", "ترفيه 🎬", "وثائقي 🦁", "أطفال 🧸")
    }
    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    // Sample IPTV channels for demonstration
    val sampleChannels = remember {
        listOf(
            MirnahChannel("1", "beIN SPORTS 1 HD", "رياضة ⚽", "4K HDR", accentColor = Color(0xFF673AB7)),
            MirnahChannel("2", "beIN SPORTS 2 HD", "رياضة ⚽", "1080p FHD", accentColor = Color(0xFF512DA8)),
            MirnahChannel("3", "SSC 1 Sports HD", "رياضة ⚽", "1080p FHD", accentColor = Color(0xFF00897B)),
            MirnahChannel("4", "العربية الحدث", "أخبار 🌍", "1080p FHD", accentColor = Color(0xFFD32F2F)),
            MirnahChannel("5", "الجزيرة الإخبارية", "أخبار 🌍", "1080p FHD", accentColor = Color(0xFFE65100)),
            MirnahChannel("6", "MBC 1 HD", "ترفيه 🎬", "1080p FHD", accentColor = Color(0xFF1E88E5)),
            MirnahChannel("7", "MBC العراق HD", "ترفيه 🎬", "1080p FHD", accentColor = Color(0xFF0288D1)),
            MirnahChannel("8", "ناشيونال جيوغرافيك أبوظبي", "وثائقي 🦁", "1080p FHD", accentColor = Color(0xFFFBC02D)),
            MirnahChannel("9", "سبيستون SpaceToon", "أطفال 🧸", "1080p FHD", accentColor = Color(0xFFE91E63)),
            MirnahChannel("10", "كارتون نتورك بالعربية", "أطفال 🧸", "1080p FHD", accentColor = Color(0xFF00ACC1)),
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
                // ==========================
                // LOGIN VIA CODE SECTION
                // ==========================
                item(key = "login_card") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Glowing TV Icon Capsule
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF334B).copy(alpha = 0.35f),
                                            Color(0xFFFF334B).copy(alpha = 0.08f),
                                            Color.Transparent
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
                            text = "أدخل كود الاشتراك لتفعيل البث التلفزيوني المباشر",
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(26.dp))

                        // Glass Input Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(22.dp)
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

                            // Code Input Field
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .border(
                                        width = 1.dp,
                                        color = if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(14.dp)
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
                                            text = "مثال: VID-2026-LIVE",
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
                                        textStyle = androidx.compose.ui.text.TextStyle(
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
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                if (inputCode.isNotBlank()) {
                                                    isLoading = true
                                                    scope.launch {
                                                        delay(700)
                                                        MirnahManager.saveActivation(context, inputCode)
                                                        currentCode = inputCode
                                                        isActivated = true
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                if (inputCode.isNotEmpty()) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { inputCode = ""; errorMessage = null },
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }

                                // Paste button
                                Icon(
                                    imageVector = Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            runCatching {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
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
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            // Activate / Login Button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    .pressable(enabled = !isLoading) {
                                        if (inputCode.isBlank()) {
                                            errorMessage = "يرجى إدخال كود الاشتراك أولاً"
                                            return@pressable
                                        }
                                        isLoading = true
                                        scope.launch {
                                            delay(700) // smooth authentic verification feel
                                            MirnahManager.saveActivation(context, inputCode)
                                            currentCode = inputCode
                                            isActivated = true
                                            isLoading = false
                                        }
                                    },
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
                                            text = "تفعيل الدخول",
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // Features List
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MirnahFeatureRow("⚡", "سيرفرات بث فائقة الاستقرار والسرعة")
                            MirnahFeatureRow("📺", "قنوات رياضية وترفيهية وعالمية بجودة 4K / FHD")
                            MirnahFeatureRow("🔒", "تفعيل مباشر عبر الكود بدون الحاجة لبيانات شخصية")
                        }
                    }
                }
            } else {
                // ==========================
                // ACTIVATED / IPTV CHANNELS VIEW
                // ==========================
                item(key = "subscription_status") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        // Subscription info card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF132818)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "الاشتراك نشط",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF4CAF50).copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = "ONLINE",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                                Text(
                                    text = "الكود: ${currentCode.take(4)}****${currentCode.takeLast(2).ifEmpty { "" }}",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            // Logout Button
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .pressable {
                                        MirnahManager.logout(context)
                                        isActivated = false
                                        currentCode = ""
                                        inputCode = ""
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Logout,
                                        contentDescription = "Logout",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "تغيير",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // Category Chips
                item(key = "categories") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(categories.size) { index ->
                            val isSelected = selectedCategoryIndex == index
                            val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(bg)
                                    .pressable { selectedCategoryIndex = index }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = categories[index],
                                    color = fg,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Channel Cards
                items(filteredChannels, key = { it.id }) { channel ->
                    ChannelCard(channel = channel) {
                        Toast.makeText(context, "جاري فتح ${channel.name}...", Toast.LENGTH_SHORT).show()
                    }
                }

                item {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MirnahFeatureRow(icon: String, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ChannelCard(channel: MirnahChannel, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceContainerHigh.copy(alpha = 0.6f))
            .border(1.dp, c.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .pressable(pressedScale = 0.98f) { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel Icon Box
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(channel.accentColor.copy(alpha = 0.15f))
                .border(1.dp, channel.accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = channel.accentColor,
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
                            .background(Color(0xFFE53935))
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "مباشر",
                        fontSize = 11.sp,
                        color = Color(0xFFE53935),
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

        // Play Pill Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = c.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
