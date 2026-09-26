package com.guardian.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.gxFancyGlow(
    shape: Shape = GxShapeLg,
    elevation: Dp = 24.dp
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = GxPrimaryGlow,
    spotColor = GxPrimaryGlow
)

fun Modifier.gxDangerGlow(
    shape: Shape = GxShapeLg,
    elevation: Dp = 24.dp
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = GxDangerSoft,
    spotColor = GxDangerSoft
)
