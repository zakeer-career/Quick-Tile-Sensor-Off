package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class AppColors(
    val bg: Color,
    val cardBg: Color,
    val softBg: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlue: Color, // Mapped to NeonCyan in dark / DeepCyan in light
    val accentCyan: Color,
    val accentPurple: Color,
    val accentRose: Color,
    val accentGreen: Color,
    val accentAmber: Color,
    val glowColor: Color,
    val isDark: Boolean
)

val FuturisticDarkAppColors = AppColors(
    bg = CyberVoid,
    cardBg = CyberDarkCard,
    softBg = CyberSoftCard,
    border = CyberBorder,
    textPrimary = NeonWhite,
    textSecondary = SlateSilver,
    textMuted = SlateDeep,
    accentBlue = NeonCyan,
    accentCyan = NeonCyan,
    accentPurple = HoloViolet,
    accentRose = LaserCrimson,
    accentGreen = PlasmaGreen,
    accentAmber = SolarAmber,
    glowColor = CyberBorderGlow,
    isDark = true
)

val FuturisticLightAppColors = AppColors(
    bg = TitaniumBg,
    cardBg = TitaniumCard,
    softBg = TitaniumSoft,
    border = TitaniumBorder,
    textPrimary = TitaniumTextPrimary,
    textSecondary = TitaniumTextSecondary,
    textMuted = TitaniumTextMuted,
    accentBlue = DeepCyan,
    accentCyan = DeepCyan,
    accentPurple = ElectricIndigo,
    accentRose = LaserCrimson,
    accentGreen = CyberEmerald,
    accentAmber = SolarAmber,
    glowColor = Color(0x3300C4D6),
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { FuturisticDarkAppColors }

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = HoloViolet,
    tertiary = LaserCrimson,
    background = CyberVoid,
    surface = CyberDarkCard,
    surfaceVariant = CyberSoftCard,
    onPrimary = CyberVoid,
    onSecondary = Color.White,
    onBackground = NeonWhite,
    onSurface = NeonWhite,
    outline = CyberBorder
)

private val LightColorScheme = lightColorScheme(
    primary = DeepCyan,
    secondary = ElectricIndigo,
    tertiary = LaserCrimson,
    background = TitaniumBg,
    surface = TitaniumCard,
    surfaceVariant = TitaniumSoft,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TitaniumTextPrimary,
    onSurface = TitaniumTextPrimary,
    outline = TitaniumBorder
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "system", // "system", "light", "dark", "dynamic"
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemInDark
    }

    val useDynamic = (themeMode == "dynamic") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val colorScheme = when {
        useDynamic -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    val appColors = if (useDynamic) {
        AppColors(
            bg = colorScheme.background,
            cardBg = colorScheme.surface,
            softBg = colorScheme.surfaceVariant,
            border = colorScheme.outlineVariant,
            textPrimary = colorScheme.onSurface,
            textSecondary = colorScheme.onSurfaceVariant,
            textMuted = colorScheme.outline,
            accentBlue = colorScheme.primary,
            accentCyan = colorScheme.primary,
            accentPurple = colorScheme.secondary,
            accentRose = LaserCrimson,
            accentGreen = PlasmaGreen,
            accentAmber = SolarAmber,
            glowColor = colorScheme.primary.copy(alpha = 0.25f),
            isDark = isDark
        )
    } else if (isDark) {
        FuturisticDarkAppColors
    } else {
        FuturisticLightAppColors
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
