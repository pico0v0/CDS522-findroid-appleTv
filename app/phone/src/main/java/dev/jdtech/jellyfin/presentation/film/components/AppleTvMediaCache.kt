package dev.jdtech.jellyfin.presentation.film.components

import dev.jdtech.jellyfin.models.FindroidItem

/**
 * 全局媒体内存缓存，用于在进入影片详情页时瞬间秒显“相关推荐”，
 * 彻底消除因二次网络请求造成的布局跳动与突兀出现。
 */
object AppleTvMediaCache {
    var cachedItems: List<FindroidItem> = emptyList()
}

