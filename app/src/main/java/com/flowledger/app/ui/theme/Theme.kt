package com.flowledger.app.ui.theme

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
import androidx.compose.ui.text.TextStyle

val InflowGreen = Color(0xFF00C853)
val OutflowRed = Color(0xFFD50000)
val SurfaceDark = Color(0xFF121212)
val SurfaceLight = Color(0xFFF8F9FA)

val TabularFiguresStyle = TextStyle(
    fontFeatureSettings = "tnum"
)

private val DarkColorScheme = darkColorScheme(
    primary = InflowGreen,
    secondary = OutflowRed,
    background = SurfaceDark,
    surface = SurfaceDark,
)

private val LightColorScheme = lightColorScheme(
    primary = InflowGreen,
    secondary = OutflowRed,
    background = SurfaceLight,
    surface = SurfaceLight,
)

@Composable
fun FlowLedgerTheme(
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
        content = content
    )
}
