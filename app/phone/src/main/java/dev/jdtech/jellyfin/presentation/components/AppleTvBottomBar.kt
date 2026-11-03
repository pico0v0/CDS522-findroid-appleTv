package dev.jdtech.jellyfin.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import dev.jdtech.jellyfin.DownloadsRoute
import dev.jdtech.jellyfin.HomeRoute
import dev.jdtech.jellyfin.MediaRoute
import dev.jdtech.jellyfin.core.R as CoreR

private val AppleTvBlack = Color(0xFF000000)
private val AppleTvDivider = Color(0xFF2C2C2E)
private val AppleTvPill = Color(0xFF2C2C2E)
private val AppleTvInactive = Color(0xFF8E8E93)
private val AppleTvActive = Color(0xFFFFFFFF)

sealed class AppleTvTab(
    val title: String,
    val iconRes: Int,
    val route: NavKey,
) {
    data object AppleTv : AppleTvTab("Apple TV", CoreR.drawable.ic_apple_tv, HomeRoute)
    data object Downloads : AppleTvTab("下载", CoreR.drawable.ic_apple_download, DownloadsRoute)
    data object Search : AppleTvTab("搜索", CoreR.drawable.ic_apple_search, MediaRoute)
}

val appleTvTabs = listOf(
    AppleTvTab.AppleTv,
    AppleTvTab.Downloads,
    AppleTvTab.Search,
)

@Composable
fun AppleTvBottomBar(
    currentRoute: NavKey,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppleTvBlack)
            .navigationBarsPadding()
    ) {
        // 顶部超细分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(AppleTvDivider)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            appleTvTabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) {
                            onNavigate(tab.route)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (tab is AppleTvTab.Search && selected) {
                        // 选中的搜索 Tab 拥有 Apple TV 标志性的灰色药丸指示器胶囊
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(AppleTvPill)
                                .padding(horizontal = 18.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.title,
                                modifier = Modifier.size(20.dp),
                                tint = AppleTvActive,
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .height(26.dp)
                                .padding(vertical = 1.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.title,
                                modifier = Modifier.size(if (tab is AppleTvTab.AppleTv) 24.dp else 22.dp),
                                tint = if (selected) AppleTvActive else AppleTvInactive,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tab.title,
                        color = if (selected) AppleTvActive else AppleTvInactive,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

