package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem

private val CardBackground = Color(0xFF1C1C1E)
private val TitleWhite = Color(0xFFFFFFFF)
private val SubtitleGray = Color(0xFFD1D1D6)

@Composable
fun AppleTvItemCard(
    item: FindroidItem,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageUri = item.images.primary ?: item.images.backdrop

    if (item is FindroidEpisode) {
        imageUri = item.images.showPrimary ?: item.images.primary
    }

    if (imageUri?.scheme == null && imageUri?.path != null) {
        imageUri = Uri.Builder()
            .appendEncodedPath("${context.filesDir}")
            .appendEncodedPath(imageUri.path)
            .build()
    }

    val (mainTitle, subTitle) = splitTitle(item.name)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.675f)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .clickable { onClick(item) }
    ) {
        // 海报图片
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_film),
                    contentDescription = null,
                    tint = Color(0xFF48484A),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        // 右上角 Apple TV 徽标（像素级复刻图2中每个海报卡片的 tv 角标）
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_apple_tv),
                contentDescription = "tv",
                modifier = Modifier.size(width = 24.dp, height = 14.dp),
                tint = Color.White.copy(alpha = 0.95f),
            )
        }

        // 底部渐变遮罩与片名
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.55f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x77000000),
                            Color(0xEE000000),
                        )
                    )
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mainTitle,
                    color = TitleWhite,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 13.sp,
                )

                if (subTitle != null) {
                    Spacer(modifier = Modifier.height(1.5.dp))
                    Text(
                        text = subTitle,
                        color = SubtitleGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.4.sp,
                    )
                }
            }
        }
    }
}

private fun splitTitle(rawName: String): Pair<String, String?> {
    val clean = rawName.trim()
    // 匹配如 "辛特尔 Sintel"
    val parts = clean.split(" ", limit = 2)
    if (parts.size == 2 && parts[0].any { it.code in 0x4E00..0x9FFF } && parts[1].isNotEmpty()) {
        return Pair(parts[0], parts[1].uppercase())
    }
    // 匹配 "片名 (年份)"
    val yearRegex = Regex("""^(.*?)\s*\((\d{4})\)$""")
    val match = yearRegex.matchEntire(clean)
    if (match != null) {
        val title = match.groupValues[1]
        val year = match.groupValues[2]
        return Pair(title, year)
    }
    // 匹配下划线 "Demo_Movie_Alpha"
    if (clean.contains("_")) {
        val pretty = clean.replace("_", " ")
        return Pair(pretty, null)
    }
    return Pair(clean, null)
}

