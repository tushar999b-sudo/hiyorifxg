package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoodBad
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.avatar.HiyoriExpression
import com.example.ui.theme.HiyoriThemeTokens

@Composable
fun AvatarViewport(
    expression: HiyoriExpression,
    isSpeaking: Boolean,
    isThinking: Boolean,
    isListening: Boolean,
    onPoke: () -> Unit,
    onSelectExpression: (HiyoriExpression) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors
    val infiniteTransition = rememberInfiniteTransition(label = "hiyori_breathing")

    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.022f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    val breathingOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_offset_y"
    )

    val statusText = when {
        isThinking -> "Sensing mood & thinking"
        isListening -> "Listening..."
        isSpeaking -> "Speaking..."
        else -> expression.title
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Centered Glass Character Card (24dp corners, translucent fill, 1dp border, soft shadow)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.glassSurface,
            border = BorderStroke(1.dp, colors.glassBorder),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(276.dp)
                .clickable { onPoke() }
                .testTag("hiyori_character_card")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.glassSurface,
                                colors.surfaceVariant.copy(alpha = if (colors.isDark) 0.25f else 0.45f),
                                colors.glassSurface
                            )
                        )
                    )
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.primary.copy(alpha = if (colors.isDark) 0.12f else 0.08f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.68f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = expression,
                    animationSpec = tween(durationMillis = 280),
                    label = "hiyori_portrait_crossfade"
                ) { currentExpr ->
                    Image(
                        painter = painterResource(id = currentExpr.baseDrawableRes),
                        contentDescription = "Hiyori ${currentExpr.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(252.dp)
                            .graphicsLayer {
                                scaleX = breathingScale
                                scaleY = breathingScale
                                translationY = breathingOffsetY
                            }
                            .testTag("hiyori_avatar_image")
                    )
                }

                // Top-right Small Gray Text Chip for Mood / Status
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.surfaceVariant,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .testTag("hiyori_status_badge")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.onBackground)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            color = colors.onBackground,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bottom-left subtle mood caption
                Surface(
                    shape = RoundedCornerShape(50),
                    color = colors.glassSurface,
                    border = BorderStroke(1.dp, colors.glassBorder),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = expression.emotionNote,
                        color = colors.outline,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Expression / Mood Chips beneath her
        MoodChipsRow(
            selectedExpression = expression,
            onSelectExpression = onSelectExpression
        )
    }
}

@Composable
fun MoodChipsRow(
    selectedExpression: HiyoriExpression,
    onSelectExpression: (HiyoriExpression) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HiyoriThemeTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HiyoriExpression.entries.forEach { expr ->
            val isSelected = expr == selectedExpression
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) colors.primary else colors.glassSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) colors.primary else colors.glassBorder
                ),
                modifier = Modifier
                    .height(36.dp)
                    .clickable { onSelectExpression(expr) }
                    .testTag("expr_btn_${expr.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = expr.lucideStyleIcon(),
                        contentDescription = expr.title,
                        tint = if (isSelected) colors.onPrimary else colors.outline,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = expr.title,
                        color = if (isSelected) colors.onPrimary else colors.outline,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

private fun HiyoriExpression.lucideStyleIcon(): ImageVector = when (this) {
    HiyoriExpression.NEUTRAL -> Icons.Outlined.AutoAwesome
    HiyoriExpression.HAPPY -> Icons.Outlined.SentimentSatisfied
    HiyoriExpression.SHY -> Icons.Outlined.SentimentVerySatisfied
    HiyoriExpression.ANGRY -> Icons.Outlined.Whatshot
    HiyoriExpression.SAD -> Icons.Outlined.SentimentDissatisfied
    HiyoriExpression.EXCITED -> Icons.Outlined.Bolt
    HiyoriExpression.SURPRISED -> Icons.Outlined.Psychology
    HiyoriExpression.SMUG -> Icons.Outlined.MoodBad
    HiyoriExpression.LOVE -> Icons.Outlined.FavoriteBorder
    HiyoriExpression.SLEEPY -> Icons.Outlined.Bedtime
    HiyoriExpression.THINKING -> Icons.Outlined.Psychology
}
