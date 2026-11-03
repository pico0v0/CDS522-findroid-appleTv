package dev.jdtech.jellyfin.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import dev.jdtech.jellyfin.core.presentation.theme.Spacings

val appleTvDarkScheme = darkScheme.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF),
)

@Composable
fun FindroidTheme(
    darkTheme: Boolean? = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // 强制使用 Apple TV OLED 纯黑沉浸式色彩空间
    val colorScheme = appleTvDarkScheme

    MaterialTheme(colorScheme = colorScheme, shapes = shapes) {
        CompositionLocalProvider(
            LocalContentColor provides contentColorFor(MaterialTheme.colorScheme.background),
            LocalSpacings provides Spacings,
        ) {
            content()
        }
    }
}
