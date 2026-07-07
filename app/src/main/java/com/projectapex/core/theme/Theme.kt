package com.projectapex.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ApexDarkColorScheme = darkColorScheme(
    primary = ApexPurple,
    onPrimary = Color.White,
    secondary = ApexPurpleLight,
    background = ApexBackground,
    onBackground = ApexOnBackground,
    surface = ApexSurface,
    onSurface = ApexOnBackground,
    surfaceVariant = ApexSurfaceVariant,
    onSurfaceVariant = ApexOnSurfaceVariant,
    error = ApexError,
)

private val ApexLightColorScheme = lightColorScheme(
    primary = ApexPurple,
    secondary = ApexPurpleLight,
)

/**
 * Project Apex is dark-mode-first by design: [dynamicColor] defaults to false so the
 * brand accent stays consistent instead of being overridden by Android 12+ wallpaper theming.
 */
@Composable
fun ProjectApexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ApexDarkColorScheme
        else -> ApexLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ApexTypography,
        content = content
    )
}
