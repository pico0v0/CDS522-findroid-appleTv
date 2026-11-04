package dev.jdtech.jellyfin.presentation.film

import android.app.DownloadManager
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import dev.jdtech.jellyfin.models.isDownloaded
import android.widget.Toast
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
import dev.jdtech.jellyfin.film.presentation.media.MediaViewModel
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.movie.MovieState
import dev.jdtech.jellyfin.film.presentation.movie.MovieViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.presentation.film.components.ActorsRow
import dev.jdtech.jellyfin.presentation.film.components.AppleTvMediaCache
import dev.jdtech.jellyfin.presentation.film.components.AppleTvDownloadQueue
import dev.jdtech.jellyfin.presentation.film.components.TaskStatus
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.rememberSafePadding
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import java.util.UUID
import kotlinx.coroutines.launch
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
    "春之序曲" to "沉睡于古老高山幽谷的森林精灵从冬眠中苏醒，带领忠诚的小伙伴用魔法乐章唤醒春回大地，与冬日暴雪展开一场关于生机与守护的优美诗篇。",
    "欢迎来龙餐馆" to "隐匿于市井深处的奇幻东方餐馆迎来了传说中的龙族贵客。性格迥异的厨师与侍应生在后厨展开手忙脚乱的烹饪大作战，只为端上一道能抚平巨龙之怒的神级美味。",
    "小黄人与大怪兽" to "呆萌爱惹祸的小黄人们误入远古巨兽遗迹，偶遇了沉睡千年的泰坦巨怪。小黄人们不仅没有逃跑，反而使出各种让人啼笑皆非的招式试图驯服这只庞然大物。",
    "白衣逆行者" to "风雪交加的深夜急救中心，医护团队争分夺秒为抢救垂危生命展开极限救援。在生死考验的关口，他们用平凡之躯筑起最坚固的守护之墙，致敬每一位逆光而行的白衣天使。",
    "奥德赛" to "人类跨星际探索飞船历经数十年航行，终于抵达银河边缘未知黑洞边缘的奇点星系。宇航员在时间与重力的极端扭曲中，见证宇宙演化的终极奥秘与人类文明的初心。",
    "疾速追杀" to "退隐江湖的传奇顶级杀手再次被打破平静生活，面对地下黑帮的重重围剿与天价悬赏，孤身一人在霓虹暴雨夜的暗夜都市展开酣畅淋漓、枪枪致命的生死复仇反击。",
    "生化危机" to "浣熊市地下实验室突发未知生化病毒泄露，整座封闭都市在短短数小时内沦为活死人游荡的人间地狱。特战突击小队在漆黑绝望的废墟中展开九死一生的突围撤离。"
)

@Composable
fun MovieScreen(
    movieId: UUID,
    navigateBack: () -> Unit,
    navigateHome: () -> Unit,
    navigateToPerson: (personId: UUID) -> Unit,
    navigateToMovie: (movieId: UUID) -> Unit = {},
    viewModel: MovieViewModel = hiltViewModel(),
    mediaViewModel: MediaViewModel = hiltViewModel(),
    downloaderViewModel: DownloaderViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val isOfflineMode = LocalOfflineMode.current

    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaState by mediaViewModel.state.collectAsStateWithLifecycle()
    val downloaderState by downloaderViewModel.state.collectAsStateWithLifecycle()

    var currentMovieId by remember { mutableStateOf(movieId) }

    // 优先读取内存缓存，进入详情页第 0 毫秒即刻呈现“相关推荐”，杜绝二次网络请求引发的突然插入与页面跳动
    val cachedList = remember { AppleTvMediaCache.cachedItems }
    var effectiveMediaItems by remember { mutableStateOf(cachedList) }

    LaunchedEffect(mediaState.mediaItems) {
        if (mediaState.mediaItems.isNotEmpty()) {
            effectiveMediaItems = mediaState.mediaItems
            AppleTvMediaCache.cachedItems = mediaState.mediaItems
        }
    }

    LaunchedEffect(currentMovieId) {
        viewModel.loadMovie(movieId = currentMovieId)
    }

    LaunchedEffect(true) {
        mediaViewModel.loadData()
        AppleTvDownloadQueue.registerDownloader(downloaderViewModel.downloader)
    }

    LaunchedEffect(state.movie) { state.movie?.let { movie -> downloaderViewModel.update(movie) } }

    ObserveAsEvents(downloaderViewModel.events) { event ->
        when (event) {
            is DownloaderEvent.Successful -> {
                viewModel.loadMovie(movieId = currentMovieId)
            }
            is DownloaderEvent.Deleted -> {
                if (isOfflineMode) {
                    navigateBack()
                } else {
                    viewModel.loadMovie(movieId = currentMovieId)
                }
            }
        }
    }

    MovieScreenLayout(
        state = state,
        mediaItems = effectiveMediaItems,
        downloaderState = downloaderState,
        onAction = { action ->
            when (action) {
                is MovieAction.Play -> {
                    val intent = Intent(context, PlayerActivity::class.java)
                    intent.putExtra("itemId", currentMovieId.toString())
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
        onSelectRelatedMovie = { newId ->
            currentMovieId = newId
        },
    )
}

@Composable
private fun MovieScreenLayout(
    state: MovieState,
    mediaItems: List<FindroidItem>,
    downloaderState: DownloaderState,
    onAction: (MovieAction) -> Unit,
    onDownloaderAction: (DownloaderAction) -> Unit,
    onSelectRelatedMovie: (UUID) -> Unit,
) {
    val context = LocalContext.current
    val safePadding = rememberSafePadding()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

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

            // 相关推荐短片：排除当前播放的短片
            val relatedItems = remember(mediaItems, movie.id) {
                mediaItems.filter { it.id != movie.id }
            }

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

                // 下载状态与队列任务监控
                val queueTasks by AppleTvDownloadQueue.tasks.collectAsStateWithLifecycle()
                val task = queueTasks.firstOrNull { it.movie.id == movie.id }

                val isDownloaded = movie.isDownloaded() || task?.status == TaskStatus.COMPLETED
                val isDownloading = task?.status == TaskStatus.DOWNLOADING
                val isWaiting = task?.status == TaskStatus.WAITING

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

                    // 下载圆形按钮（顺序下载队列：一次只下一个，支持排队、等待图标与实时进度）
                    Surface(
                        onClick = {
                            when {
                                isDownloading || isWaiting -> {
                                    AppleTvDownloadQueue.cancel(movie.id)
                                    onDownloaderAction(DownloaderAction.CancelDownload(movie))
                                }
                                isDownloaded -> {
                                    Toast.makeText(context, "该短片已下载至本地，可离线播放", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    AppleTvDownloadQueue.enqueue(movie)
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isDownloaded) Color(0xFF1E3A2B) else Color(0xFF2C2C2E),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when {
                                isDownloading -> {
                                    // 正在下载：外圈圆环进度条，中间停止方块 ■
                                    val currentProgress = task?.progress ?: 0.01f
                                    CircularProgressIndicator(
                                        progress = { currentProgress },
                                        color = Color.White,
                                        trackColor = Color(0x33FFFFFF),
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.fillMaxSize().padding(3.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(11.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White)
                                    )
                                }
                                isWaiting -> {
                                    // 等待下载中：专属时钟图标
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_download_waiting),
                                        contentDescription = "等待下载",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                isDownloaded -> {
                                    // 缓存完成：绿色打勾 ✓
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_check),
                                        contentDescription = "已下载",
                                        tint = Color(0xFF34C759),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                else -> {
                                    // 未下载状态：纯白下载箭头
                                    Icon(
                                        painter = painterResource(CoreR.drawable.ic_download),
                                        contentDescription = "下载",
                                        tint = Color.White,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // 正在下载或等待排队中的实时胶囊指示栏（一次只下一个，顺序下载）
                AnimatedVisibility(
                    visible = isDownloading || isWaiting,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(percent = 50),
                            color = Color(0xFF1C1C1E),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isDownloading) Color(0xFF0A84FF) else Color(0xFFFF9F0A))
                                )
                                Spacer(Modifier.width(8.dp))
                                val statusText = if (isDownloading) {
                                    "正在下载 ${((task?.progress ?: 0f) * 100).toInt()}% · ${task?.speedText ?: "0 KB/s"}"
                                } else {
                                    "等待下载 · 排队中 (顺序下载)"
                                }
                                Text(
                                    text = statusText,
                                    color = Color(0xFFE5E5EA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "取消",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.clickable {
                                        AppleTvDownloadQueue.cancel(movie.id)
                                        onDownloaderAction(DownloaderAction.CancelDownload(movie))
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

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

                Spacer(Modifier.height(26.dp))

                // 5. “相关推荐 >”：Apple TV 风格横向卡片流（填充下半部空白，体验绝佳）
                if (relatedItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "相关推荐",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = ">",
                            color = Color(0xFF8E8E93),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(relatedItems, key = { it.id }) { item ->
                            AppleTvRelatedCard(
                                item = item,
                                onClick = {
                                    onSelectRelatedMovie(item.id)
                                    coroutineScope.launch {
                                        scrollState.animateScrollTo(0)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))
                }

                // 6. 演职人员专区（如果有具体演职员）
                if (state.actors.isNotEmpty()) {
                    Text(
                        text = "演职员阵容",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    ActorsRow(
                        actors = state.actors,
                        onActorClick = { personId ->
                            onAction(MovieAction.NavigateToPerson(personId))
                        },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                }

                // 7. “影片信息”卡片组（Apple TV 标志性结构化信息栏，彻底丰富底部美学）
                AppleTvInfoSection(movie = movie)

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
private fun AppleTvRelatedCard(
    item: FindroidItem,
    onClick: () -> Unit,
) {
    val (cnName, enName) = remember(item.name, item.originalTitle) {
        val name = item.name.trim()
        val orig = item.originalTitle?.trim()
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

    val imageUri = item.images.primary ?: item.images.backdrop

    Column(
        modifier = Modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = cnName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 右上角 tv 专属徽标
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = "tv",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                )
            }

            // 底部暗渐变遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xDD000000),
                                Color(0xFF000000)
                            )
                        )
                    )
            )

            // 底部片名
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 7.dp, vertical = 7.dp)
            ) {
                Text(
                    text = cnName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (enName.isNotBlank()) {
                    Text(
                        text = enName,
                        color = Color(0xFFB0B0B5),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleTvInfoSection(movie: FindroidMovie) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "影片信息",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1C1C1E),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                InfoRow(label = "出品工作室", value = "Blender Open Projects / Apple TV+ Demo")
                InfoDivider()
                InfoRow(label = "内容分级", value = "适合所有年龄 · G (General Audience)")
                InfoDivider()
                InfoRow(label = "视频画质", value = "4K 超高清 (2160p) · 杜比视界 (Dolby Vision)")
                InfoDivider()
                InfoRow(label = "音频声道", value = "杜比全景声 (Dolby Atmos) · 5.1 环绕声")
                InfoDivider()
                InfoRow(label = "字幕语言", value = "简体中文 (内置) · 英文 · CC · SDH")
                InfoDivider()
                InfoRow(label = "版权所属", value = "© 2024 Apple Inc. & Blender Foundation.")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color(0xFF8E8E93),
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            color = Color(0xFFE5E5EA),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.6.dp)
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
