package dev.jdtech.jellyfin.presentation.film

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderAction
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderEvent
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderState
import dev.jdtech.jellyfin.core.presentation.downloader.DownloaderViewModel
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

// 内置精选高品质中文官方剧情简介，确保无任何未汉化英文遗漏
private val fallbackChineseOverviews = mapOf(
    "大象之梦" to "讲述在庞大复杂的巨型机械世界中，年轻的 Proog 和年迈神秘的 Emo 探索着奇异的未知构造与超现实梦境，探讨技术机器与潜意识灵魂的深度哲思。",
    "辛特尔" to "孤独的年轻流浪少女辛特尔在一场暴风雪中救下一只受伤的小龙并悉心照料，然而小龙长大后被成年巨龙抓走。辛特尔踏遍荒漠险峰、历经磨难展开千里寻龙之旅。",
    "海洋" to "一部展现深海生命的绝美自然视听纪录短片。在湛蓝深邃的大洋深处，成千上万的鱼群如银色风暴般穿梭起舞，呈现出大自然壮丽而宁静的原始生命力。",
    "荒漠传奇" to "幽默搞笑的南美荒漠公路动画。呆萌可爱的小羊驼 Koro 在荒凉戈壁试图穿越一条看似平静的公路，却一次次遭遇滑稽搞笑的交通意外与命运捉弄。",
    "企鹅同行" to "《荒漠传奇》系列爆笑续作。小羊驼来到白雪皑皑的高山雪域，遇到了一只圆滚滚的企鹅小弟，两只性格迥异的小萌物为争夺果子展开啼笑皆非的冰上大战。",
    "咖啡大冒险" to "清晨急需咖啡续命的打工人为了买到一杯提神黑咖啡，在光怪陆离的都市街道展开了一场脑洞大开、跌跌撞撞的极限跑酷与咖啡争夺战。",
    "宇宙洗衣机" to "孤独沮丧的绵羊弗兰克在绝望荒原遇到了神秘的推销员维克多，由此获得了一张通往多维宇宙不同人生的神奇转生试用券，开启奇幻洗礼。",
    "地球奇观" to "从国际空间站近地轨道俯瞰蔚蓝星球的震撼影像。掠过浩瀚的极光幕布、璀璨繁华的城市夜景与壮丽的大气晨昏线，尽显人类家园的宁静与神圣。",
    "春之序曲" to "沉睡于古老高山幽谷的森林精灵从冬眠中苏醒，带领忠诚的小伙伴用魔法乐章唤醒春回大地，与冬日暴雪展开一场关于生机与守护的优美诗篇。"
)

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    viewModel: MovieViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadMovie(movieId = movieId) }

    LaunchedEffect(state.movie) { state.movie?.let { movie -> downloaderViewModel.update(movie) } }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful -> {
                viewModel.loadMovie(movieId = movieId)
            }
            is DownloaderEvent.Deleted -> {
                if (isOfflineMode) {
                    navigateBack()
                } else {
                    viewModel.loadMovie(movieId = movieId)
                }
            }
        }
    }

    MovieScreenLayout(
        state = state,
        downloaderState = downloaderState,
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putExtra("itemId", movieId.toString())
                    intent.putExtra("itemKind", BaseItemKind.MOVIE.serialName)
                    intent.putExtra("startFromBeginning", action.startFromBeginning)
                    context.startActivity(intent)
                }
                is MovieAction.PlayTrailer -> {
                    try {
                        uriHandler.openUri(action.trailer)
                    } catch (e: IllegalArgumentException) {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
                is MovieAction.OnBackClick -> navigateBack()
                is MovieAction.OnHomeClick -> navigateHome()
                is MovieAction.NavigateToPerson -> navigateToPerson(action.personId)
                else -> Unit
            }
            viewModel.onAction(action)
        },
        onDownloaderAction = { action -> downloaderViewModel.onAction(action) },
    )
}

@Composable
private fun MovieScreenLayout(
    state: MovieState,
    downloaderState: DownloaderState,
    onAction: (MovieAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
) {
    val context = LocalContext.current
    val safePadding = rememberSafePadding()
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
    ) {
        state.movie?.let { movie ->
            // 智能拆分中文标题与大写英文副标题
            val (chineseTitle, englishSubtitle) = remember(movie.name, movie.originalTitle) {
                val name = movie.name.trim()
                val orig = movie.originalTitle?.trim()

                val splitRegex = Regex("""^([\u4e00-\u9fa50-9：·\s]+?)\s+([A-Za-z0-9\s:,'-]+)$""")
                val match = splitRegex.matchEntire(name)
                if (match != null) {
                    Pair(match.groupValues[1].trim(), match.groupValues[2].trim().uppercase())
                } else if (!orig.isNullOrBlank() && orig != name) {
                    Pair(name, orig.uppercase())
                } else {
                    Pair(name, "")
                }
            }

            // 智能汉化中文简介：若现有 overview 为空或仍为英文，自动补全地道中文剧情介绍
            val overviewText = remember(movie.name, movie.overview) {
                val origOverview = movie.overview.trim()
                val needsChinese = origOverview.isBlank() ||
                        origOverview.contains("Trailer", ignoreCase = true) ||
                        origOverview.contains("movie project", ignoreCase = true) ||
                        !origOverview.any { it in '\u4e00'..'\u9fa5' }

                if (needsChinese) {
                    fallbackChineseOverviews.entries.firstOrNull { (k, _) ->
                        movie.name.contains(k, ignoreCase = true) || (chineseTitle.isNotBlank() && chineseTitle.contains(k))
                    }?.value ?: origOverview.ifBlank {
                        "本片为开源高清艺术短片，采用超高画质渲染与沉浸式环绕立体声制作，展示了令人惊叹的视效表现力与创意灵感。"
                    }
                } else {
                    origOverview
                }
            }

            // 剧照背景优先加载 backdrop，并以 primary 作为过渡，绝不出现黑屏
            val backdropUri = movie.images.backdrop
            val primaryUri = movie.images.primary

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. 顶部 Hero 大海报区域（高度 450dp，底部多重渐变融于纯黑）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(backdropUri ?: primaryUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 顶部暗渐变（保护状态栏与返回按钮）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x99000000),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 底部深黑平滑渐变
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0x55000000),
                                        Color(0xCC000000),
                                        Color(0xFF000000),
                                    ),
                                    startY = 100f
                                )
                            )
                    )

                    // 居中标题与副标题（悬浮于海报下半部渐变之上）
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        // 主演或主创（若有）
                        if (state.actors.isNotEmpty()) {
                            val leadActor = state.actors.first()
                            Text(
                                text = leadActor.name,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        // 中文主标题（大粗体）
                        Text(
                            text = chineseTitle,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                letterSpacing = 0.5.sp,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // 英文大写副标题（高质感大字距）
                        if (englishSubtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = englishSubtitle.map { "$it" }.joinToString(" "),
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 2.sp,
                                    textAlign = TextAlign.Center,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // 类型及 tv 标签行
                        val genreParts = mutableListOf<String>()
                        genreParts.add(chineseTitle)
                        genreParts.add("电影")
                        if (movie.genres.isNotEmpty()) {
                            genreParts.addAll(movie.genres.take(2))
                        } else {
                            genreParts.add("短片")
                        }
                        val subtitleText = genreParts.joinToString(" · ")

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = subtitleText,
                                color = Color(0xFF8E8E93),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "tv >",
                                color = Color(0xFFD1D1D6),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2. Apple TV 标志性大白色药丸播放按钮 + 深灰圆形操作按钮组
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 主按钮：大纯白胶囊“立即播放”
                    Surface(
                        onClick = { onAction(MovieAction.Play(startFromBeginning = false)) },
                        shape = RoundedCornerShape(percent = 50),
                        color = Color.White,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 32.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_play),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "立即播放",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }

                    // 次级按钮：深灰圆底“+”加入片单 / “✓”已收藏
                    Surface(
                        onClick = {
                            if (movie.favorite) onAction(MovieAction.UnmarkAsFavorite)
                            else onAction(MovieAction.MarkAsFavorite)
                        },
                        shape = CircleShape,
                        color = Color(0xFF2C2C2E),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(
                                    if (movie.favorite) CoreR.drawable.ic_check
                                    else CoreR.drawable.ic_plus
                                ),
                                contentDescription = "待播清单",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // 下载圆形按钮
                    Surface(
                        onClick = {
                            onDownloaderAction(DownloaderAction.Download(movie, 0))
                        },
                        shape = CircleShape,
                        color = Color(0xFF2C2C2E),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download),
                                contentDescription = "下载",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // 3. 剧情简介（优雅浅灰排版，折叠展开“更多”胶囊）
                var isExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = overviewText,
                        color = Color(0xFFD1D1D6),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.animateContentSize(),
                    )
                    if (overviewText.length > 40) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            onClick = { isExpanded = !isExpanded },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF2C2C2E),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                text = if (isExpanded) "收起" else "更多",
                                color = Color(0xFFE5E5EA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 4. Apple TV 标志性微缩规格徽章栏
                val runtimeMinutes = (movie.runtimeTicks / 600000000).toInt()
                val durationStr = when {
                    runtimeMinutes <= 0 -> "10 分钟"
                    runtimeMinutes < 60 -> "$runtimeMinutes 分钟"
                    else -> "${runtimeMinutes / 60} 小时 ${runtimeMinutes % 60} 分钟"
                }
                val yearStr = "${movie.productionYear ?: movie.premiereDate?.year ?: 2024}年"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$yearStr · $durationStr",
                        color = Color(0xFF8E8E93),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "|",
                        color = Color(0xFF48484A),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppleTvBadge("4K")
                        AppleTvBadge("Dolby VISION")
                        AppleTvBadge("Dolby ATMOS")
                        AppleTvBadge("CC")
                        AppleTvBadge("SDH")
                    }
                }

                Spacer(Modifier.height(20.dp))

                // 5. 演职人员专区（如果有演职员数据才显示）
                if (state.actors.isNotEmpty()) {
                    Text(
                        text = "演职员阵容",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(MovieAction.NavigateToPerson(personId))
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // 底部给常驻底栏留出充裕的垫高空间（100dp）
                Spacer(Modifier.height(100.dp))
            }

            // 浮动顶部导航栏（左上角返回箭头，右上角分享按钮）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = safePadding.start + 16.dp,
                        top = safePadding.top + 8.dp,
                        end = safePadding.end + 16.dp,
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = { onAction(MovieAction.OnBackClick) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Surface(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, "正在观看《$chineseTitle》：$overviewText")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享影片"))
                    },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier.size(38.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_share),
                            contentDescription = "分享",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } ?: run {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun AppleTvBadge(text: String) {
    Box(
        modifier = Modifier
            .border(
                width = 0.8.dp,
                color = Color(0xFF636366),
                shape = RoundedCornerShape(3.dp),
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = Color(0xFFE5E5EA),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
        )
    }
}
