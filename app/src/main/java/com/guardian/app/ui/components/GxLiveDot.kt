package com.guardian.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxWarning

@Composable
fun GxLiveDot(
    modifier: Modifier = Modifier,
    color: Color = GxSafe,
    size: Dp = 8.dp,
    pulsing: Boolean = true
) {
    if (!pulsing) {
        Box(
            modifier = modifier
                .size(size)
                .background(color, CircleShape)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "live-dot-transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 2.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse-scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse-alpha"
    )

    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        // Glowing Halo
        Box(
            modifier = Modifier
                .size(size)
                .scale(pulseScale)
                .alpha(pulseAlpha)
                .background(color, CircleShape)
        )
        // Core Dot
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}
