package com.tensei.ytxtube

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Same live red used by DurationPill across the app. */
private val MirnahLiveRed = Color(0xFFCC0000)

/**
 * Dark cinematic gradients drawn behind the sample thumbnails, one per
 * feed item — mirrors the approved design preview.
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

/**
 * Movie / series entry drawn YouTube-style: thumbnail, duration,
 * title, description, views and the channel byline.
 */
data class MirnahVideo(
    val id: String,
    val thumbTitle: String,
    val thumbSubtitle: String,
    val gradient: Int,
    val duration: String,
    val title: String,
    val description: String,
    val views: String,
    val date: String,
    val channel: String,
    val avatar: String,
    val avatarColor: Color,
    val category: String,
)

/**
 * Live channel entry: thumbnail with a live badge and viewer count,
 * title, description and the channel byline.
 */
data class MirnahLive(
    val id: String,
    val thumbTitle: String,
    val thumbSubtitle: String,
    val gradient: Int,
    val viewers: String,
    val title: String,
    val description: String,
    val channel: String,
    val avatar: String,
    val avatarColor: Color,
    val category: String,
    val quality: String,
)

private val MirnahMovieCategories = listOf("الكل", "أكشن", "دراما", "كوميديا", "جريمة", "وثائقي")
private val MirnahLiveCategories = listOf("الكل", "رياضة", "أخبار", "ترفيه", "أطفال")

// Sample live movies & series feed for demonstration.
private val MirnahMovies = listOf(
    MirnahVideo(
        "1", "الظل الأخير", "فيلم • 2026", 0, "2:08:45",
        "الظل الأخير | فيلم أكشن كامل بجودة 4K",
        "عندما يظهر شريط قديم يكشف حقيقة اختفاء شقيقه، ينطلق المراسل سامي في رحلة خطرة تقلب حياته رأساً على عقب…",
        "3.4 مليون مشاهدة", "قبل يومين", "قناة أفلام VIP", "ف", Color(0xFF833761), "أكشن",
    ),
    MirnahVideo(
        "2", "أزقة الحارة", "الموسم الثالث", 1, "45:12",
        "أزقة الحارة — الحلقة 8 | النهاية المفاجئة",
        "بعد قرار ود الصعب تتصاعد المواجهات بين عائلات الحارة وتظهر أسرار لم تُروَ من قبل…",
        "1.2 مليون مشاهدة", "قبل 5 أيام", "دار الدراما", "د", Color(0xFF1F8A70), "دراما",
    ),
    MirnahVideo(
        "3", "ما بعد العاصفة", "فيلم دراما", 2, "1:34:20",
        "ما بعد العاصفة | فيلم دراما عائلية مؤثر",
        "أسرة تمر بعاصفة صعبة تجمعها من جديد وتكتشف أن ما بعد العاصفة هو بداية الطريق الحقيقي…",
        "846 ألف مشاهدة", "قبل أسبوع", "بيت السينما", "ب", Color(0xFF6C2BD9), "دراما",
    ),
    MirnahVideo(
        "4", "ضحكة عام", "كوميديا 2026", 6, "1:42:10",
        "ضحكة عام | فيلم كوميدي جديد كامل",
        "مواقف يومية تتحول إلى فوضى مضحكة عندما يجتمع أربعة أصدقاء في مشروع عائلي فاشل…",
        "2.1 مليون مشاهدة", "قبل 3 أيام", "قناة الضحكة", "ض", Color(0xFFB0714A), "كوميديا",
    ),
    MirnahVideo(
        "5", "الطريق المسدود", "جريمة وغموض", 5, "1:58:35",
        "الطريق المسدود | فيلم جريمة وغموض",
        "محقق عائد من التقاعد لإكمال آخر ملف في حياته ليكتشف أن الحقيقة أقرب مما يظن…",
        "1.7 مليون مشاهدة", "قبل 4 أيام", "قناة الجريمة", "ج", Color(0xFF1E7A3C), "جريمة",
    ),
    MirnahVideo(
        "6", "كوكبنا العجيب", "وثائقي 4K", 1, "52:40",
        "كوكبنا العجيب | وثائقي الطبيعة بجودة 4K",
        "رحلة بصرية مذهلة عبر أندر الأماكن على وجه الأرض مصوّرة بأحدث تقنيات التصوير…",
        "5.2 مليون مشاهدة", "قبل شهر", "وثائقي بلس", "و", Color(0xFF1F8A70), "وثائقي",
    ),
)

// Sample live broadcast feed for demonstration.
private val MirnahLiveChannels = listOf(
    MirnahLive(
        "1", "دوري أبطال آسيا", "النهائي — مع التعليق العربي", 3, "128 ألف يشاهدون",
        "beIN SPORTS 1 HD — النهائي الكبير مباشرة",
        "تغطية شاملة لنهائي البطولة من الملعب مع التحليل والتعليق العربي قبل المباراة وبعدها…",
        "beIN SPORTS", "bS", Color(0xFF1E7A3C), "رياضة", "FHD 1080p",
    ),
    MirnahLive(
        "2", "دوري روشن", "الجولة 21 — مباشر", 4, "86 ألف يشاهدون",
        "SSC 1 Sports HD — مباراة القمة هذا المساء",
        "اللقاء المرتقب بين المتصدرين في الجولة الحاسمة من الدوري وبث حصري بتقنية 4K HDR…",
        "SSC 1", "S", Color(0xFF9E1C1C), "رياضة", "4K HDR",
    ),
    MirnahLive(
        "3", "تغطية خاصة", "آخر الأخبار الآن", 5, "21 ألف يشاهدون",
        "العربية الحدث — نشرة الأخبار المباشرة",
        "تغطية مباشرة ومستمرة لأهم الأحداث المحلية والعالمية على مدار الساعة…",
        "العربية", "ع", Color(0xFF3B5BDB), "أخبار", "FHD",
    ),
    MirnahLive(
        "4", "موجز الجزيرة", "أحدث التقارير", 1, "34 ألف يشاهدون",
        "الجزيرة الإخبارية — تغطية مستمرة",
        "أحدث التقارير والنشرات الإخبارية من مراكز الأخبار حول العالم…",
        "الجزيرة", "ج", Color(0xFF1F8A70), "أخبار", "FHD",
    ),
    MirnahLive(
        "5", "مساء ترفيه", "أمسية مع النجوم", 0, "45 ألف يشاهدون",
        "MBC 1 HD — أمسية ترفيهية مباشرة",
        "أجمل اللحظات مع ضيوف الفن والترفيه في برنامج الأمسية الأشهر…",
        "MBC 1", "M", Color(0xFF833761), "ترفيه", "FHD",
    ),
    MirnahLive(
        "6", "عالم كرتون", "بث مستمر للأطفال", 2, "18 ألف يشاهدون",
        "سبيستون — بث مباشر لبرامج الأطفال",
        "مزيج من الكرتون والبرامج التعليمية الممتعة لأطفالنا طوال اليوم…",
        "سبيستون", "س", Color(0xFF6C2BD9), "أطفال", "FHD",
    ),
)

/**
 * Main Mirnah Screen with two tabs — "أفلام ومسلسلات" and "بث مباشر" —
 * both drawn YouTube-style (thumbnail + description) using the shared
 * UiKit vocabulary so it looks like every other page of the app.
 */
@Composable
fun MirnahScreen(
    contentPadding: PaddingValues,
    onSearch: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var movieCategory by remember { mutableIntStateOf(0) }
    var liveCategory by remember { mutableIntStateOf(0) }

    val filteredMovies = remember(movieCategory) {
        if (movieCategory == 0) MirnahMovies
        else MirnahMovies.filter { it.category == MirnahMovieCategories[movieCategory] }
    }
    val filteredLive = remember(liveCategory) {
        if (liveCategory == 0) MirnahLiveChannels
        else MirnahLiveChannels.filter { it.category == MirnahLiveCategories[liveCategory] }
    }

    val chips = if (selectedTab == 0) MirnahMovieCategories else MirnahLiveCategories
    val chipIndex = if (selectedTab == 0) movieCategory else liveCategory

    CollapsingHeaderLayout(
        resetKey = scrollToTop to selectedTab,
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
            item(key = "tabs") {
                MirnahTabRow(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                )
            }

            item(key = "chips") {
                MirnahChips(
                    categories = chips,
                    selected = chipIndex,
                    onSelect = { if (selectedTab == 0) movieCategory = it else liveCategory = it },
                )
            }

            if (selectedTab == 0) {
                items(filteredMovies, key = { "m" + it.id }) { video ->
                    MirnahVideoCard(
                        video = video,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            Toast.makeText(context, "جاري فتح: ${video.thumbTitle}", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            } else {
                items(filteredLive, key = { "l" + it.id }) { live ->
                    MirnahLiveCard(
                        live = live,
                        modifier = Modifier.animateItem(),
                        onClick = {
                            Toast.makeText(context, "جاري فتح قناة ${live.channel}", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            item(key = "bottom_space") {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The two tab buttons that replace the old subscription status card:
 * "أفلام ومسلسلات" and "بث مباشر" — pill-shaped like the app buttons.
 */
@Composable
private fun MirnahTabRow(selected: Int, onSelect: (Int) -> Unit) {
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
 * Category chips using exactly the HomeFilterChips style of the home page.
 */
@Composable
private fun MirnahChips(
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
 * Shared gradient thumbnail with the big centered title, exactly like
 * the approved preview. [overlay] adds badges (duration / live / viewers).
 */
@Composable
private fun MirnahThumb(
    gradient: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MirnahThumbGradients[gradient]
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.94f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
        overlay()
    }
}

/** Small colored circle with the channel initial. */
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

/**
 * Movie / series card, YouTube-style: thumbnail with duration pill,
 * title, two-line description, views meta and the channel byline.
 */
@Composable
private fun MirnahVideoCard(
    video: MirnahVideo,
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
        MirnahThumb(
            gradient = video.gradient,
            title = video.thumbTitle,
            subtitle = video.thumbSubtitle,
        ) {
            Text(
                text = video.duration,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = video.title,
            color = c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = video.description,
            color = c.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${video.views} • ${video.date}",
            color = c.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MirnahAvatar(avatar = video.avatar, avatarColor = video.avatarColor)
            Spacer(Modifier.width(9.dp))
            Text(
                text = video.channel,
                color = c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
            )
        }
    }
}

/**
 * Live channel card: thumbnail with the red live badge and viewer count,
 * title, description and the channel byline with category and quality.
 */
@Composable
private fun MirnahLiveCard(
    live: MirnahLive,
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
        MirnahThumb(
            gradient = live.gradient,
            title = live.thumbTitle,
            subtitle = live.thumbSubtitle,
        ) {
            // Live badge — top corner (mirrored like YouTube RTL).
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

            // Current viewers — bottom corner.
            Text(
                text = live.viewers,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = live.title,
            color = c.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = live.description,
            color = c.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MirnahAvatar(avatar = live.avatar, avatarColor = live.avatarColor)
            Spacer(Modifier.width(9.dp))
            Text(
                text = live.channel,
                color = c.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "• ${live.category} • ${live.quality}",
                color = c.onSurfaceVariant,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
