package com.guardian.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft

enum class GxChipVariant {
    Neutral,
    Safe,
    Warning,
    Danger,
    Brand
}

@Composable
fun GxChip(
    text: String,
    modifier: Modifier = Modifier,
    variant: GxChipVariant = GxChipVariant.Neutral,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    height: Dp = 28.dp
) {
    val (bgColor, textColor, borderColor) = when (variant) {
        GxChipVariant.Neutral -> Triple(GxSurfaceAlt, GxTextMid, GxBorder)
        GxChipVariant.Safe -> Triple(GxSafeSoft, GxSafe, GxSafe.copy(alpha = 0.4f))
        GxChipVariant.Warning -> Triple(GxWarningSoft, GxWarning, GxWarning.copy(alpha = 0.4f))
        GxChipVariant.Danger -> Triple(GxDangerSoft, GxDanger, GxDanger.copy(alpha = 0.4f))
        GxChipVariant.Brand -> Triple(GxPrimarySoft, GxPrimary, GxPrimary.copy(alpha = 0.4f))
    }

    Surface(
        modifier = modifier
            .height(height)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}
