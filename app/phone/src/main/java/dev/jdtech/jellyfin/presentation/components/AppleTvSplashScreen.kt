package dev.jdtech.jellyfin.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R as CoreR

/**
 * Apple TV style splash / waiting screen shown when opening the app
 * and waiting for server and user information to load.
 */
@Composable
fun AppleTvSplashScreen(
    modifier: Modifier = Modifier,
    enablePulse: Boolean = true,
) {
    val scale = if (enablePulse) {
        val infiniteTransition = rememberInfiniteTransition(label = "AppleTvSplashPulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 0.97f,
            targetValue = 1.02f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "CardScale",
        )
        animatedScale
    } else {
        1.0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)),
        contentAlignment = Alignment.Center,
    ) {
        // Centered Dark Squircle Card
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(scale)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = Color(0x55FFFFFF),
                )
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF222225))
                .border(
                    BorderStroke(1.dp, Color(0x28FFFFFF)),
                    shape = RoundedCornerShape(32.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = CoreR.drawable.ic_appletv_splash_logo),
                contentDescription = "Apple TV",
                modifier = Modifier.size(130.dp),
            )
        }
    }
}

@Preview
@Composable
private fun AppleTvSplashScreenPreview() {
    AppleTvSplashScreen()
}
