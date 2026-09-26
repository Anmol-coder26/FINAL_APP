package com.guardian.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid

object GxButton {

    @Composable
    fun Primary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        loading: Boolean = false,
        height: Dp = 52.dp
    ) {
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            shape = GxShapeMd,
            colors = ButtonDefaults.buttonColors(
                containerColor = GxPrimary,
                contentColor = GxTextHi,
                disabledContainerColor = GxSurfaceAlt,
                disabledContentColor = GxTextLo
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = modifier.height(height)
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = GxTextHi,
                    strokeWidth = 2.dp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    @Composable
    fun Danger(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        pulsing: Boolean = false,
        enabled: Boolean = true,
        height: Dp = 52.dp
    ) {
        val pulseAlpha by if (pulsing) {
            val infiniteTransition = rememberInfiniteTransition(label = "danger-pulse")
            infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 0.72f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse-alpha"
            )
        } else {
            rememberInfiniteTransition(label = "static").animateFloat(
                initialValue = 1f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(1000)), label = "static-alpha"
            )
        }

        Button(
            onClick = onClick,
            enabled = enabled,
            shape = GxShapeMd,
            colors = ButtonDefaults.buttonColors(
                containerColor = GxDanger,
                contentColor = Color.White,
                disabledContainerColor = GxSurfaceAlt,
                disabledContentColor = GxTextLo
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = modifier
                .height(height)
                .alpha(pulseAlpha)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun Ghost(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        icon: ImageVector? = null,
        enabled: Boolean = true,
        height: Dp = 52.dp
    ) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = GxShapeMd,
            border = BorderStroke(1.dp, GxBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = GxTextMid,
                disabledContentColor = GxTextLo
            ),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = modifier.height(height)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
