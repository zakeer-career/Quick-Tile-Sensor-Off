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
    val accentBlue: Color,
    val accentRose: Color,
    val accentGreen: Color,
    val isDark: Boolean
)

val LightAppColors = AppColors(
    bg = Color(0xFFF7F9FC),
    cardBg = Color(0xFFFFFFFF),
    softBg = Color(0xFFF0F2F9),
    border = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF1E293B),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    accentBlue = Color(0xFF2563EB),
    accentRose = Color(0xFFE11D48),
    accentGreen = Color(0xFF22C55E),
    isDark = false
)

val DarkAppColors = AppColors(
    bg = Color(0xFF0F172A),
    cardBg = Color(0xFF1E293B),
    softBg = Color(0xFF334155),
    border = Color(0xFF334155),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFCBD5E1),
    textMuted = Color(0xFF64748B),
    accentBlue = Color(0xFF3B82F6),
    accentRose = Color(0xFFF43F5E),
    accentGreen = Color(0xFF4ADE80),
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

private val DarkColorScheme = darkColorScheme(
    primary = Blue500,
    secondary = Blue200,
    tertiary = Rose500,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
)

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    secondary = Blue500,
    tertiary = Rose600,
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF)
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
            accentRose = Rose600,
            accentGreen = Green500,
            isDark = isDark
        )
    } else if (isDark) {
        DarkAppColors
    } else {
        LightAppColors
    }

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
