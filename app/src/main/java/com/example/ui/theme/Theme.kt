package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GeoDarkPrimary,
    onPrimary = GeoDarkOnPrimary,
    primaryContainer = GeoDarkPrimaryContainer,
    onPrimaryContainer = GeoDarkOnPrimaryContainer,
    secondary = GeoDarkSecondary,
    onSecondary = GeoDarkOnSecondary,
    secondaryContainer = GeoDarkSecondaryContainer,
    onSecondaryContainer = GeoDarkOnSecondaryContainer,
    tertiary = GeoDarkTertiary,
    onTertiary = GeoDarkOnTertiary,
    tertiaryContainer = GeoDarkTertiaryContainer,
    onTertiaryContainer = GeoDarkOnTertiaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = GeoPrimary,
    onPrimary = GeoOnPrimary,
    primaryContainer = GeoPrimaryContainer,
    onPrimaryContainer = GeoOnPrimaryContainer,
    secondary = GeoSecondary,
    onSecondary = GeoOnSecondary,
    secondaryContainer = GeoSecondaryContainer,
    onSecondaryContainer = GeoOnSecondaryContainer,
    tertiary = GeoTertiary,
    onTertiary = GeoOnTertiary,
    tertiaryContainer = GeoTertiaryContainer,
    onTertiaryContainer = GeoOnTertiaryContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
