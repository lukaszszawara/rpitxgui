package com.luksza.rpitxgui.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = Primary80,
    onPrimary        = OnPrimary80,
    primaryContainer = PrimaryContainer80,
    secondary        = Secondary80,
    tertiary         = Tertiary80,
    tertiaryContainer = TertiaryContainer80,
    background       = SurfaceDark,
    surface          = SurfaceDark,
    surfaceVariant   = SurfaceVariantDark,
    error            = ErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary          = Primary40,
    onPrimary        = OnPrimary40,
    primaryContainer = PrimaryContainer40,
    secondary        = Secondary40,
    tertiary         = Tertiary40,
    tertiaryContainer = TertiaryContainer40,
    background       = SurfaceLight,
    surface          = SurfaceLight,
    surfaceVariant   = SurfaceVariantLight,
    error            = ErrorLight
)

@Composable
fun RpitxguiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set to false to always use our branded palette instead of device wallpaper colours
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
