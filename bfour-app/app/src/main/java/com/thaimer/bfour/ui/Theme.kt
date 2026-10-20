package com.thaimer.bfour.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours for cooking progress that Material's scheme has no slot for. */
@Immutable
data class CookColors(
    val almost: Color,
    val onAlmost: Color,
    val done: Color,
    val onDone: Color,
    val ok: Color,
    val wait: Color,
    val off: Color,
)

private val DarkCook = CookColors(
    almost = Color(0xFF4A3A00), onAlmost = Color(0xFFFFE08A),
    done = Color(0xFF7A1A12), onDone = Color(0xFFFFDAD4),
    ok = Color(0xFF5BD47A), wait = Color(0xFFFFC14D), off = Color(0xFF8A8580),
)

private val LightCook = CookColors(
    almost = Color(0xFFFFE9A8), onAlmost = Color(0xFF3B2D00),
    done = Color(0xFFFFD2CB), onDone = Color(0xFF5C0F08),
    ok = Color(0xFF1E8E3E), wait = Color(0xFFB57600), off = Color(0xFF8A8580),
)

val LocalCookColors = staticCompositionLocalOf { DarkCook }

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A4C),
    onPrimary = Color(0xFF3A1400),
    primaryContainer = Color(0xFF5C2A0E),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE6BEAA),
    secondaryContainer = Color(0xFF4A3328),
    onSecondaryContainer = Color(0xFFFFDBCA),
    background = Color(0xFF14110F),
    onBackground = Color(0xFFEDE0DA),
    surface = Color(0xFF14110F),
    onSurface = Color(0xFFEDE0DA),
    surfaceVariant = Color(0xFF2A2421),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainer = Color(0xFF211C19),
    surfaceContainerHigh = Color(0xFF2B2522),
    surfaceContainerLow = Color(0xFF1B1714),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFA6430F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF390C00),
    secondary = Color(0xFF77574A),
    secondaryContainer = Color(0xFFFFDBCA),
    onSecondaryContainer = Color(0xFF2C160D),
    background = Color(0xFFFFF8F5),
    surface = Color(0xFFFFF8F5),
    surfaceContainer = Color(0xFFF7ECE7),
    surfaceContainerHigh = Color(0xFFF1E6E1),
    surfaceContainerLow = Color(0xFFFCF1EC),
)

@Composable
fun BfourTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalCookColors provides if (dark) DarkCook else LightCook
    ) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}
