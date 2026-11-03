package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.film.components.AppleTvLandscapeCard
import dev.jdtech.jellyfin.presentation.film.components.AppleTvMediaCache
import dev.jdtech.jellyfin.presentation.film.components.resolveItemImage
import dev.jdtech.jellyfin.presentation.film.components.splitTitle
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLibraryClick: (library: FindroidCollection) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageServers: () -> Unit,
    onItemClick: (item: FindroidItem) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(true) {
        viewModel.loadData()
    }

    // 汇总所有可用短片（融合推荐、视图与全局预加载缓存）
    val allShortFilms = remember(state, AppleTvMediaCache.cachedItems) {
        val items = mutableListOf<FindroidItem>()
        state.suggestionsSection?.items?.let { items.addAll(it) }
        state.resumeSection?.homeSection?.items?.let { items.addAll(it) }
        state.views.forEach { items.addAll(it.view.items) }
        items.addAll(AppleTvMediaCache.cachedItems)
        items.distinctBy { it.id }
    }

    // 保持全局媒体预加载缓存实时更新
    LaunchedEffect(allShortFilms) {
        if (allShortFilms.isNotEmpty()) {
            AppleTvMediaCache.cachedItems = allShortFilms
        }
    }

    // 主海报轮播影片（前 8 部精选作品）
    val heroItems = remember(allShortFilms) {
        if (allShortFilms.isNotEmpty()) allShortFilms.take(8) else emptyList()
    }

    var isMuted by remember { mutableStateOf(true) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 动态计算尺寸：给上方海报留出尽可能大的空间，下方热播栏沉底贴近底部导航栏，彻底消除黑边空隙
        val railHeight = 162.dp
        val heroHeight = (maxHeight - railHeight).coerceAtLeast(530.dp)

        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { viewModel.loadData() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                // 1. 顶部巨幕焦点海报区域（自适应撑满上方所有空间，尽展海报画质）
                item(key = "hero_carousel") {
                    AppleTvHeroSection(
                        items = heroItems,
                        isMuted = isMuted,
                        onMuteToggle = { isMuted = !isMuted },
                        onProfileClick = onSettingsClick,
                        onItemClick = onItemClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                    )
                }

                // 2. 唯一保留轨道：“好戲就在 Apple TV 串流熱播中”（贴底展示，包含全部短片）
                if (allShortFilms.isNotEmpty()) {
                    item(key = "section_rail_1") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(railHeight)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "好戲就在 Apple TV 串流熱播中",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                items(allShortFilms, key = { it.id }) { item ->
                                    AppleTvLandscapeCard(
                                        item = item,
                                        onClick = { onItemClick(item) },
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(1.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 像素级复刻官方 Apple TV+ 巨幕焦点海报区
 */
@Composable
private fun AppleTvHeroSection(
    items: List<FindroidItem>,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onProfileClick: () -> Unit,
    onItemClick: (FindroidItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .background(Color(0xFF141416))
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    // 自动平滑滚动（未被用户拖拽滑动时）
    LaunchedEffect(pagerState, isDragged) {
        if (!isDragged) {
            while (true) {
                delay(6500L)
                val next = if (pagerState.currentPage < items.size - 1) pagerState.currentPage + 1 else 0
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Box(
        modifier = modifier
    ) {
        // 主海报横滑 Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val item = items[pageIndex]
            val imageModel = resolveItemImage(context, item)

            Box(modifier = Modifier.fillMaxSize()) {
                if (imageModel != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onItemClick(item) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1C1C1E))
                    )
                }

                // 顶部文字暗渐变遮罩（确保状态栏与 Apple TV 标题清晰）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xE6000000),
                                    Color(0x77000000),
                                    Color.Transparent,
                                )
                            )
                        )
                )

                // 底部大范围暗影渐变遮罩，深沉融入纯黑背景
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0x22000000),
                                    Color(0x88000000),
                                    Color(0xEE000000),
                                    Color.Black,
                                )
                            )
                        )
                )

                // 海报底部居中信息展示层（完全对照官方界面）
                val (mainTitle, subTitle) = remember(item.name) { splitTitle(item.name) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // “新鲜出炉”专属小药丸标签
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color(0x40333333),
                        border = BorderStroke(0.6.dp, Color(0x55FFFFFF)),
                    ) {
                        Text(
                            text = "新鲜出炉",
                            color = Color(0xFFE5E5EA),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 中文主片名
                    Text(
                        text = mainTitle,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 英文副标题
                    if (subTitle != null) {
                        Text(
                            text = subTitle,
                            color = Color(0xFFC7C7CC),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // 影片元数据：tv 电影 · 短片 · 杜比视界
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(top = 5.dp),
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_apple_tv),
                            contentDescription = "Apple TV",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "短片 · 4K 超高清 · 杜比全景声",
                            color = Color(0xFF8E8E93),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    // 榜单标语
                    Text(
                        text = "Apple TV 上排行榜第 #1 节目",
                        color = Color(0xFFE5E5EA),
                        fontSize = 12.5.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // 标志性操作按钮组：大白胶囊“立即播放” + 深灰圆底“+”
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 白底播放按钮
                        Surface(
                            onClick = { onItemClick(item) },
                            shape = RoundedCornerShape(percent = 50),
                            color = Color.White,
                            modifier = Modifier.height(44.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_play),
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "立即播放",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }

                        // 待播清单按钮
                        Surface(
                            onClick = { onItemClick(item) },
                            shape = CircleShape,
                            color = Color(0xFF2C2C2E),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.ic_plus),
                                    contentDescription = "待播清单",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }

                    // 居中分页点指示器
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
                    ) {
                        repeat(items.size) { idx ->
                            val isCurrent = pagerState.currentPage == idx
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(if (isCurrent) 16.dp else 4.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(if (isCurrent) Color.White else Color(0x55FFFFFF))
                            )
                        }
                    }
                }
            }
        }

        // 顶层 Apple TV 专属导航栏（大标题 + 静音 + 用户头像）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Apple TV",
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 静音按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { onMuteToggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isMuted) CoreR.drawable.ic_volume_0 else CoreR.drawable.ic_volume),
                        contentDescription = "静音切换",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }

                // 个人中心按钮
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C2C2E))
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_user),
                        contentDescription = "个人中心",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}
