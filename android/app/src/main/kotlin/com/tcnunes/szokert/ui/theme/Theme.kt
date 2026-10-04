package com.tcnunes.szokert.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The website's palette (src/app.css): neutral greys, fresh green primary, warm amber secondary. */
@Immutable
data class SzokertColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val secondary: Color,
    val secondarySoft: Color,
    val star: Color,
)

val DarkColors = SzokertColors(
    bg = Color(0xFF121212), surface = Color(0xFF1B1B1B), surface2 = Color(0xFF262626),
    text = Color(0xFFECECEC), muted = Color(0xFFB3B3B3), faint = Color(0xFF8F8F8F), border = Color(0xFF2E2E2E),
    accent = Color(0xFF8FE388), accentStrong = Color(0xFFB3F0AD), accentSoft = Color(0xFF1F3320), onAccent = Color(0xFF121212),
    secondary = Color(0xFFF5B94E), secondarySoft = Color(0xFF3A2C12), star = Color(0xFFF5B94E),
)

val LightColors = SzokertColors(
    bg = Color(0xFFF6F8F2), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF2E8),
    text = Color(0xFF18211B), muted = Color(0xFF56645A), faint = Color(0xFF626E66), border = Color(0xFFDDE5D7),
    accent = Color(0xFF257030), accentStrong = Color(0xFF1C5A24), accentSoft = Color(0xFFE2F5DF), onAccent = Color(0xFFFFFFFF),
    secondary = Color(0xFF8F5A00), secondarySoft = Color(0xFFFDF0D3), star = Color(0xFFB47A00),
)

val LocalSzokertColors = staticCompositionLocalOf { DarkColors }

/** Dark by default, like the website; a theme setting comes with the About screen. */
@Composable
fun SzokertTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentStrong,
            secondary = c.secondary, secondaryContainer = c.secondarySoft, onSecondaryContainer = c.secondary,
            background = c.bg, onBackground = c.text, surface = c.bg, onSurface = c.text,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, onSurfaceVariant = c.muted,
            outline = c.faint, outlineVariant = c.border,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentStrong,
            secondary = c.secondary, secondaryContainer = c.secondarySoft, onSecondaryContainer = c.secondary,
            background = c.bg, onBackground = c.text, surface = c.bg, onSurface = c.text,
            surfaceContainer = c.surface, surfaceContainerHigh = c.surface2, onSurfaceVariant = c.muted,
            outline = c.faint, outlineVariant = c.border,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSzokertColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object Szokert {
    val colors: SzokertColors
        @Composable get() = LocalSzokertColors.current
}
