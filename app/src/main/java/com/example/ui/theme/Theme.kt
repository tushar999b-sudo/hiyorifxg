package com.example.ui.theme

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class HiyoriColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val glassSurface: Color,
    val glassBorder: Color,
    val userBubble: Color,
    val onUserBubble: Color
)

val LocalHiyoriColors = staticCompositionLocalOf {
    HiyoriColors(
        isDark = false,
        background = PureWhite,
        surfaceVariant = LightGray,
        onBackground = CharcoalBlack,
        outline = MediumGray,
        primary = CharcoalBlack,
        onPrimary = PureWhite,
        glassSurface = PureWhite.copy(alpha = 0.65f),
        glassBorder = CharcoalBlack.copy(alpha = 0.10f),
        userBubble = LightGray,
        onUserBubble = CharcoalBlack
    )
}

object HiyoriThemeTokens {
    val colors: HiyoriColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHiyoriColors.current
}

private val HiyoriLightColorScheme = lightColorScheme(
    primary = CharcoalBlack,
    onPrimary = PureWhite,
    primaryContainer = LightGray,
    onPrimaryContainer = CharcoalBlack,
    secondary = MediumGray,
    onSecondary = PureWhite,
    secondaryContainer = LightGray,
    onSecondaryContainer = CharcoalBlack,
    background = PureWhite,
    onBackground = CharcoalBlack,
    surface = PureWhite,
    onSurface = CharcoalBlack,
    surfaceVariant = LightGray,
    onSurfaceVariant = MediumGray,
    outline = MediumGray,
    outlineVariant = LightGray
)

private val HiyoriDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = CharcoalBlack,
    primaryContainer = MediumGray,
    onPrimaryContainer = PureWhite,
    secondary = LightGray,
    onSecondary = CharcoalBlack,
    secondaryContainer = MediumGray,
    onSecondaryContainer = PureWhite,
    background = CharcoalBlack,
    onBackground = PureWhite,
    surface = CharcoalBlack,
    onSurface = PureWhite,
    surfaceVariant = MediumGray,
    onSurfaceVariant = LightGray,
    outline = LightGray,
    outlineVariant = MediumGray
)

@Composable
fun HiyoriTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val animSpec = tween<Color>(durationMillis = 300)

    val bg by animateColorAsState(if (darkTheme) CharcoalBlack else PureWhite, animSpec, label = "bg")
    val surfaceVar by animateColorAsState(if (darkTheme) MediumGray else LightGray, animSpec, label = "surfaceVar")
    val onBg by animateColorAsState(if (darkTheme) PureWhite else CharcoalBlack, animSpec, label = "onBg")
    val out by animateColorAsState(if (darkTheme) LightGray else MediumGray, animSpec, label = "outline")
    val prim by animateColorAsState(if (darkTheme) PureWhite else CharcoalBlack, animSpec, label = "primary")
    val onPrim by animateColorAsState(if (darkTheme) CharcoalBlack else PureWhite, animSpec, label = "onPrimary")
    val glass by animateColorAsState(
        if (darkTheme) PureWhite.copy(alpha = 0.10f) else PureWhite.copy(alpha = 0.65f),
        animSpec,
        label = "glassSurface"
    )
    val glassBdr by animateColorAsState(
        if (darkTheme) PureWhite.copy(alpha = 0.10f) else CharcoalBlack.copy(alpha = 0.10f),
        animSpec,
        label = "glassBorder"
    )
    val uBubble by animateColorAsState(if (darkTheme) MediumGray else LightGray, animSpec, label = "userBubble")
    val onUBubble by animateColorAsState(if (darkTheme) PureWhite else CharcoalBlack, animSpec, label = "onUserBubble")

    val hiyoriColors = HiyoriColors(
        isDark = darkTheme,
        background = bg,
        surfaceVariant = surfaceVar,
        onBackground = onBg,
        outline = out,
        primary = prim,
        onPrimary = onPrim,
        glassSurface = glass,
        glassBorder = glassBdr,
        userBubble = uBubble,
        onUserBubble = onUBubble
    )

    CompositionLocalProvider(LocalHiyoriColors provides hiyoriColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) HiyoriDarkColorScheme else HiyoriLightColorScheme,
            typography = Typography
        ) {
            Crossfade(
                targetState = darkTheme,
                animationSpec = tween(durationMillis = 300),
                label = "theme_crossfade"
            ) {
                content()
            }
        }
    }
}
