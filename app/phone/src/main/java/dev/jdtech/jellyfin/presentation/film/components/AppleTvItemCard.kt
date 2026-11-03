package dev.jdtech.jellyfin.presentation.film.components

import android.content.Context
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
import androidx.compose.foundation.layout.width
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
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import java.io.File

private val CardBackground = Color(0xFF1C1C1E)
private val TitleWhite = Color(0xFFFFFFFF)
private val SubtitleGray = Color(0xFFD1D1D6)

/**
 * 全局图片解析器：本地磁盘文件、在线全量缓存与相对路径智能降级
 */
fun resolveItemImage(context: Context, item: FindroidItem): Any? {
    // 1. 本地磁盘已缓存图片
    val localPrimary = File(context.filesDir, "images/${item.id}/primary")
    if (localPrimary.exists()) return localPrimary

    val localBackdrop = File(context.filesDir, "images/${item.id}/backdrop")
    if (localBackdrop.exists()) return localBackdrop

    // 2. 全局预加载缓存中的在线完整 HTTP URL
    val cachedItem = AppleTvMediaCache.cachedItems.firstOrNull { it.id == item.id }
    if (cachedItem != null) {
        cachedItem.images.primary?.takeIf { it.scheme != null }?.let { return it }
        cachedItem.images.backdrop?.takeIf { it.scheme != null }?.let { return it }
    }

    // 3. 自身携带的合法网络 URI
    item.images.primary?.takeIf { it.scheme != null }?.let { return it }
    item.images.backdrop?.takeIf { it.scheme != null }?.let { return it }

    // 4. 自身相对路径转 filesDir
    item.images.primary?.path?.let { path ->
        val file = File(context.filesDir, path.trimStart('/'))
        if (file.exists()) return file
    }
    item.images.backdrop?.path?.let { path ->
        val file = File(context.filesDir, path.trimStart('/'))
        if (file.exists()) return file
    }

    // 5. 剧集特殊处理
    if (item is FindroidEpisode) {
        item.images.showPrimary?.let { return it }
    }

    return item.images.primary ?: item.images.backdrop
}

fun splitTitle(rawName: String): Pair<String, String?> {
    val clean = rawName.trim()
    val parts = clean.split(" ", limit = 2)
    if (parts.size == 2 && parts[0].any { it.code in 0x4E00..0x9FFF } && parts[1].isNotEmpty()) {
        return Pair(parts[0], parts[1].uppercase())
    }
    val yearRegex = Regex("""^(.*?)\s*\((\d{4})\)$""")
    val match = yearRegex.matchEntire(clean)
    if (match != null) {
        return Pair(match.groupValues[1], match.groupValues[2])
    }
    if (clean.contains("_")) {
        return Pair(clean.replace("_", " "), null)
    }
    return Pair(clean, null)
}

/**
 * 纵向竖版海报卡片 (2:3 黄金比例)
 */
@Composable
fun AppleTvItemCard(
    item: FindroidItem,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageModel = resolveItemImage(context, item)
    val (mainTitle, subTitle) = splitTitle(item.name)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.675f)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .clickable { onClick(item) }
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
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

        // 右上角 Apple TV 徽标
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_apple_tv),
                contentDescription = "Apple TV",
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(17.dp),
            )
        }

        // 底部高对比度暗渐变遮罩与中英双行片名
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xDD000000),
                            Color(0xFF000000),
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

/**
 * 横向宽屏短片卡片 (16:10 影视比例，还原图3下方热播轨道)
 */
@Composable
fun AppleTvLandscapeCard(
    item: FindroidItem,
    onClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageModel = resolveItemImage(context, item)
    val (mainTitle, subTitle) = splitTitle(item.name)

    Box(
        modifier = modifier
            .width(184.dp)
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(10.dp))
            .background(CardBackground)
            .clickable { onClick(item) }
    ) {
        if (imageModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
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
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // 右上角 Apple TV 水印徽标
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 6.dp)
        ) {
            Icon(
                painter = painterResource(CoreR.drawable.ic_apple_tv),
                contentDescription = "Apple TV",
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(16.dp),
            )
        }

        // 底部渐变与片名
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xCC000000),
                            Color(0xFF000000),
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text(
                    text = mainTitle,
                    color = TitleWhite,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subTitle != null) {
                    Text(
                        text = subTitle,
                        color = SubtitleGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
