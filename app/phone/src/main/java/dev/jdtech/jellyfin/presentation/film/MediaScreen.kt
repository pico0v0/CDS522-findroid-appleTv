package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.media.MediaAction
import dev.jdtech.jellyfin.film.presentation.media.MediaViewModel
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.film.components.AppleTvItemCard
import kotlinx.coroutines.delay

private val AppleTvBlack = Color(0xFF000000)
private val AppleTvInputBg = Color(0xFF1C1C1E)
private val AppleTvAvatarBg = Color(0xFF2C2C2E)
private val AppleTvGrayText = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    onItemClick: (FindroidItem) -> Unit,
    onFavoritesClick: () -> Unit,
    searchExpanded: Boolean,
    onSearchExpand: (Boolean) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel: MediaViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current

    LaunchedEffect(true) {
        viewModel.loadData()
    }

    // 后台静默预加载所有短片的剧照背景与海报，直接缓存至本地，实现详情页点开秒显不黑屏
    LaunchedEffect(state.mediaItems) {
        if (state.mediaItems.isNotEmpty()) {
            val imageLoader = context.imageLoader
            state.mediaItems.forEach { item ->
                item.images.primary?.let { uri ->
                    imageLoader.enqueue(ImageRequest.Builder(context).data(uri).build())
                }
                item.images.backdrop?.let { uri ->
                    imageLoader.enqueue(ImageRequest.Builder(context).data(uri).build())
                }
            }
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            delay(minOf(50L + (searchQuery.count() * 50L), 300L))
            searchViewModel.onAction(SearchAction.Search(searchQuery))
        }
    }

    // 计算展示的项目列表：优先以服务器媒体为主，若有搜索关键词则进行即时过滤
    val displayedItems = remember(state.mediaItems, searchState.items, searchQuery) {
        if (searchQuery.isBlank()) {
            state.mediaItems
        } else {
            // 结合本地模糊匹配与远程搜索结果
            val localMatches = state.mediaItems.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
            if (localMatches.isNotEmpty()) {
                localMatches
            } else {
                searchState.items
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleTvBlack)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部大标题与用户头像（像素级复刻图2）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "搜索",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )

                // 右上角圆形用户头像
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppleTvAvatarBg)
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_user),
                        contentDescription = "User",
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }

            // 搜索输入框（圆角深灰矩形，放大镜，“节目、电影等”）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppleTvInputBg)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_apple_search),
                        contentDescription = null,
                        tint = AppleTvGrayText,
                        modifier = Modifier.size(18.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "节目、电影等",
                                color = AppleTvGrayText,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Normal,
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF3A3A3C))
                                .clickable { searchQuery = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = "Clear",
                                tint = Color(0xFFEBEBF5),
                                modifier = Modifier.size(10.dp),
                            )
                        }
                    }
                }
            }

            // 3列媒体卡片网格
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.onAction(MediaAction.OnRetryClick) },
                modifier = Modifier.weight(1f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 10.dp,
                        bottom = 16.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        AppleTvItemCard(
                            item = item,
                            onClick = onItemClick,
                        )
                    }
                }
            }
        }
    }
}
