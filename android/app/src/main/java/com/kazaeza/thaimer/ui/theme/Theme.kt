package com.kazaeza.thaimer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kazaeza.thaimer.R
import com.kazaeza.thaimer.model.Kind

@Immutable
data class Palette(
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
)

private val Light = Palette(
    dark = false,
    background = Color(0xFFF5F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFECECF0),
    text = Color(0xFF121216),
    muted = Color(0xFF6E6E7A),
    faint = Color(0xFFB4B4BE),
    track = Color(0xFFE6E6EC),
    accent = Color(0xFFFF7A00),
    onAccent = Color.White,
)

private val Dark = Palette(
    dark = true,
    background = Color(0xFF0B0B0E),
    surface = Color(0xFF17171C),
    surfaceAlt = Color(0xFF222229),
    text = Color(0xFFF4F4F6),
    muted = Color(0xFF9A9AA6),
    faint = Color(0xFF55555F),
    track = Color(0xFF26262E),
    accent = Color(0xFFFF8A1F),
    onAccent = Color.White,
)

val LocalPalette = staticCompositionLocalOf { Light }

object Thaimer {
    val palette: Palette
        @Composable @ReadOnlyComposable get() = LocalPalette.current
}

/** Kind colours: punchy in the dark, a notch deeper on white so text stays readable. */
fun Kind.color(dark: Boolean): Color = when (this) {
    Kind.PREP -> if (dark) Color(0xFFA1A1AA) else Color(0xFF8E8E99)
    Kind.WARMUP -> if (dark) Color(0xFF2DD4BF) else Color(0xFF0D9E92)
    Kind.HANDS -> if (dark) Color(0xFF38BDF8) else Color(0xFF0B8FD6)
    Kind.LEGS -> if (dark) Color(0xFFA3E635) else Color(0xFF5B9A0A)
    Kind.CLINCH -> if (dark) Color(0xFFC084FC) else Color(0xFF9333EA)
    Kind.FIGHT -> if (dark) Color(0xFFFB923C) else Color(0xFFF2700F)
    Kind.BURST -> if (dark) Color(0xFFFF4D6A) else Color(0xFFE5163D)
    Kind.PRESSURE -> if (dark) Color(0xFFFACC15) else Color(0xFFCC9600)
    Kind.CARDIO -> if (dark) Color(0xFFF472B6) else Color(0xFFDB2777)
    Kind.STRENGTH -> if (dark) Color(0xFF818CF8) else Color(0xFF4F46E5)
    Kind.REST -> if (dark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    Kind.COOLDOWN -> if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
}

val Kind.emoji: String
    get() = when (this) {
        Kind.PREP -> "⏳"
        Kind.WARMUP -> "🤸"
        Kind.HANDS -> "🥊"
        Kind.LEGS -> "🦵"
        Kind.CLINCH -> "🤼"
        Kind.FIGHT -> "👊"
        Kind.BURST -> "💥"
        Kind.PRESSURE -> "😤"
        Kind.CARDIO -> "🔥"
        Kind.STRENGTH -> "💪"
        Kind.REST -> "💨"
        Kind.COOLDOWN -> "🧘"
    }

@Composable
@ReadOnlyComposable
fun kindColor(kind: Kind): Color = kind.color(LocalPalette.current.dark)

@OptIn(ExperimentalTextApi::class)
private fun oswald(weight: Int) = Font(
    R.font.oswald,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Condensed sporty face for numbers and headings; body text stays on the system font. */
val Oswald = FontFamily(oswald(400), oswald(500), oswald(600), oswald(700))

object Type {
    val hero = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp)
    val title = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp)
    val cardTitle = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 25.sp)
    val label = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.6.sp)
    val stat = TextStyle(fontFamily = Oswald, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
}

@Composable
fun ThaimerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (dark) Dark else Light
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, onSurfaceVariant = p.muted,
            surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surfaceAlt, surfaceContainerHighest = p.surfaceAlt,
            outline = p.faint, outlineVariant = p.track,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent,
            background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text, onSurfaceVariant = p.muted,
            surfaceContainerLow = p.surface, surfaceContainer = p.surface,
            surfaceContainerHigh = p.surface, surfaceContainerHighest = p.surfaceAlt,
            outline = p.faint, outlineVariant = p.track,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
