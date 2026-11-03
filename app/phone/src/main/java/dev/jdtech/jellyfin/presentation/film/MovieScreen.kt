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
import androidx.compose.ui.draw.clip
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

            val imageUri = movie.images.backdrop ?: movie.images.primary

            val overviewText = movie.overview.ifBlank {
                "本片为开源高清短片，采用超高画质渲染与沉浸式环绕立体声制作，展示了令人惊叹的视效表现力与艺术创意。"
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
                        model = imageUri,
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

                // 3. 剧情简介（浅灰优雅字体，折叠展开“更多”胶囊）
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

                Spacer(Modifier.height(28.dp))

                // 5. 专区：预告片入口
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "预告片 >",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 6. 演职人员专区
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
