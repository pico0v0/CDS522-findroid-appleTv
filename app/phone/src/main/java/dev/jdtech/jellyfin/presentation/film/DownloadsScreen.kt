package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.film.presentation.downloads.DownloadsViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.presentation.film.components.AppleTvDownloadQueue
import dev.jdtech.jellyfin.presentation.film.components.AppleTvDownloadTask
import dev.jdtech.jellyfin.presentation.film.components.TaskStatus
import dev.jdtech.jellyfin.presentation.film.components.resolveItemImage
import dev.jdtech.jellyfin.presentation.film.components.splitTitle

@Composable
fun DownloadsScreen(
    onItemClick: (item: FindroidItem) -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val queueTasks by AppleTvDownloadQueue.tasks.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadItems() }

    // 将历史本地已下载影片初始化到队列（状态为 COMPLETED）
    LaunchedEffect(state.sections) {
        val movies = state.sections.flatMap { it.items }.filterIsInstance<FindroidMovie>()
        if (movies.isNotEmpty()) {
            AppleTvDownloadQueue.initCompletedFromLocal(movies)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部 Apple TV 风格大标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "下载",
                    color = Color.White,
                    fontSize = 34.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
            }

            if (queueTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_download),
                            contentDescription = null,
                            tint = Color(0xFF48484A),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "暂无下载内容",
                            color = Color(0xFF8E8E93),
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "在影片详情页点击下载，即可离线缓存",
                            color = Color(0xFF636366),
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(queueTasks, key = { it.movie.id }) { task ->
                        AppleTvDownloadCard(
                            task = task,
                            onClick = { onItemClick(task.movie) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppleTvDownloadCard(
    task: AppleTvDownloadTask,
    onClick: () -> Unit,
) {
    val movie = task.movie
    val (cnName, enName) = remember(movie.name, movie.originalTitle) {
        val split = splitTitle(movie.name)
        if (split.second != null) split
        else {
            val orig = movie.originalTitle?.trim()?.uppercase()
            Pair(split.first, orig ?: "")
        }
    }

    val context = LocalContext.current
    val imageModel = resolveItemImage(context, movie)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1C1C1E))
        ) {
            if (imageModel != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageModel)
                        .crossfade(true)
                        .build(),
                    contentDescription = cnName,
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

            // 底部暗渐变
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC000000),
                                Color(0xFF000000)
                            )
                        )
                    )
            )

            // 右上角状态徽章（3. 缓存完绿色打勾 / 4. 正在缓存动态进度 / 5. 等待下载图标）
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                when (task.status) {
                    TaskStatus.COMPLETED -> {
                        // 3. 缓存完的绿色勾，代表下载完了
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xE61E3A2B))
                                .border(1.dp, Color(0xFF34C759), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_check),
                                contentDescription = "已下载",
                                tint = Color(0xFF34C759),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    TaskStatus.DOWNLOADING -> {
                        // 4. 正在缓存的用进度来显示
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { task.progress },
                                color = Color.White,
                                trackColor = Color(0x33FFFFFF),
                                strokeWidth = 2.dp,
                                modifier = Modifier.fillMaxSize().padding(2.dp)
                            )
                            Text(
                                text = "${(task.progress * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    TaskStatus.WAITING -> {
                        // 5. 等待下载图标（表示等待下载，顺序排队）
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC2C2C2E))
                                .border(0.8.dp, Color(0xFF636366), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_download_waiting),
                                contentDescription = "等待下载",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    TaskStatus.FAILED -> {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xCCFF3B30)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.ic_x),
                                contentDescription = "失败",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 正在下载底部亮蓝细进度条
            if (task.status == TaskStatus.DOWNLOADING) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = task.progress.coerceIn(0.01f, 1f))
                            .fillMaxHeight()
                            .background(Color(0xFF0A84FF))
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // 中文标题
        Text(
            text = cnName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // 状态说明文字
        val statusSubtitle = when (task.status) {
            TaskStatus.DOWNLOADING -> "下载中 ${(task.progress * 100).toInt()}% · ${task.speedText}"
            TaskStatus.WAITING -> "等待下载 · 排队中"
            TaskStatus.COMPLETED -> "已缓存完成"
            TaskStatus.FAILED -> "下载中断"
        }
        Text(
            text = statusSubtitle,
            color = when (task.status) {
                TaskStatus.COMPLETED -> Color(0xFF34C759)
                TaskStatus.DOWNLOADING -> Color(0xFF0A84FF)
                else -> Color(0xFF8E8E93)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
