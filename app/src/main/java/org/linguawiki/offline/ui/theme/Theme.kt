package org.linguawiki.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.linguawiki.offline.data.ColorPalette
import org.linguawiki.offline.data.FontScale
import org.linguawiki.offline.data.LineSpacing
import org.linguawiki.offline.data.ThemeMode

private data class PaletteColors(
    val lightPrimary: Color,
    val lightContainer: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
)

private fun paletteColors(palette: ColorPalette, vivid: Boolean): PaletteColors {
    val soft = when (palette) {
        ColorPalette.GREEN -> PaletteColors(Color(0xFF365E55), Color(0xFFB9EBDD), Color(0xFF9DD0C2), Color(0xFF204E45))
        ColorPalette.BLUE -> PaletteColors(Color(0xFF315E8A), Color(0xFFD2E4FF), Color(0xFFA4CAFA), Color(0xFF174A72))
        ColorPalette.AMBER -> PaletteColors(Color(0xFF775A00), Color(0xFFFFE08A), Color(0xFFFFD95C), Color(0xFF5B4300))
        ColorPalette.PINK -> PaletteColors(Color(0xFF8A4D68), Color(0xFFFFD9E7), Color(0xFFFFB1CD), Color(0xFF6E284B))
        ColorPalette.PURPLE -> PaletteColors(Color(0xFF68527E), Color(0xFFEBDDF8), Color(0xFFD8BDF0), Color(0xFF4E3565))
        ColorPalette.TEAL -> PaletteColors(Color(0xFF27676B), Color(0xFFBCEBEA), Color(0xFF91D7D7), Color(0xFF084F53))
        ColorPalette.RED -> PaletteColors(Color(0xFF8A4A48), Color(0xFFFFDAD7), Color(0xFFFFB4AF), Color(0xFF702F2D))
    }
    if (!vivid) return soft
    return when (palette) {
        ColorPalette.GREEN -> PaletteColors(Color(0xFF006B45), Color(0xFF72FFC1), Color(0xFF4CFFA8), Color(0xFF00784F))
        ColorPalette.BLUE -> PaletteColors(Color(0xFF0057C8), Color(0xFF8DBBFF), Color(0xFF5AA1FF), Color(0xFF0064E7))
        ColorPalette.AMBER -> PaletteColors(Color(0xFF6A4B00), Color(0xFFFFC400), Color(0xFFFFC400), Color(0xFF765500))
        ColorPalette.PINK -> PaletteColors(Color(0xFFC60067), Color(0xFFFF8DC0), Color(0xFFFF5BA8), Color(0xFFD80070))
        ColorPalette.PURPLE -> PaletteColors(Color(0xFF6F22B8), Color(0xFFD99BFF), Color(0xFFC66BFF), Color(0xFF812DCE))
        ColorPalette.TEAL -> PaletteColors(Color(0xFF007078), Color(0xFF55F4F1), Color(0xFF22D9DA), Color(0xFF007E87))
        ColorPalette.RED -> PaletteColors(Color(0xFFB7191C), Color(0xFFFF938D), Color(0xFFFF625D), Color(0xFFC92325))
    }
}

private fun lightColors(palette: ColorPalette, vivid: Boolean) = paletteColors(palette, vivid).let { colors ->
    lightColorScheme(
        primary = colors.lightPrimary,
        onPrimary = Color.White,
        primaryContainer = colors.lightContainer,
        onPrimaryContainer = Color(0xFF071E18),
        secondary = Color(0xFF53645E),
        background = Color(0xFFF7FAF8),
        surface = Color(0xFFF7FAF8),
        surfaceVariant = Color(0xFFDCE5E1),
    )
}

private fun darkColors(palette: ColorPalette, vivid: Boolean) = paletteColors(palette, vivid).let { colors ->
    darkColorScheme(
        primary = colors.darkPrimary,
        onPrimary = Color(0xFF06241D),
        primaryContainer = colors.darkContainer,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFB9CCC4),
        background = Color(0xFF101412),
        surface = Color(0xFF101412),
        surfaceVariant = Color(0xFF3F4945),
    )
}

private val HighContrastLight = lightColorScheme(
    primary = Color(0xFF003D2F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5F6E8),
    onPrimaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE4E9E7),
    onSurfaceVariant = Color(0xFF171A19),
    outline = Color(0xFF303432),
)

private val HighContrastDark = darkColorScheme(
    primary = Color(0xFFB8FFE9),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF005B47),
    onPrimaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242927),
    onSurfaceVariant = Color.White,
    outline = Color(0xFFD8E0DC),
)

@Composable
fun LinguaWikiTheme(
    themeMode: ThemeMode,
    colorPalette: ColorPalette,
    highColorContrast: Boolean,
    fontScale: FontScale,
    lineSpacing: LineSpacing,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT, ThemeMode.HIGH_CONTRAST_LIGHT -> false
        ThemeMode.DARK, ThemeMode.HIGH_CONTRAST_DARK -> true
    }
    val colors = when (themeMode) {
        ThemeMode.HIGH_CONTRAST_LIGHT -> HighContrastLight
        ThemeMode.HIGH_CONTRAST_DARK -> HighContrastDark
        else -> if (dark) darkColors(colorPalette, highColorContrast) else lightColors(colorPalette, highColorContrast)
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = density.density,
            fontScale = density.fontScale * fontScale.multiplier,
        ),
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MaterialTheme.typography.withLineSpacing(lineSpacing.multiplier),
            content = content,
        )
    }
}

private fun Typography.withLineSpacing(multiplier: Float) = copy(
    displayLarge = displayLarge.copy(lineHeight = displayLarge.lineHeight * multiplier),
    displayMedium = displayMedium.copy(lineHeight = displayMedium.lineHeight * multiplier),
    displaySmall = displaySmall.copy(lineHeight = displaySmall.lineHeight * multiplier),
    headlineLarge = headlineLarge.copy(lineHeight = headlineLarge.lineHeight * multiplier),
    headlineMedium = headlineMedium.copy(lineHeight = headlineMedium.lineHeight * multiplier),
    headlineSmall = headlineSmall.copy(lineHeight = headlineSmall.lineHeight * multiplier),
    titleLarge = titleLarge.copy(lineHeight = titleLarge.lineHeight * multiplier),
    titleMedium = titleMedium.copy(lineHeight = titleMedium.lineHeight * multiplier),
    titleSmall = titleSmall.copy(lineHeight = titleSmall.lineHeight * multiplier),
    bodyLarge = bodyLarge.copy(lineHeight = bodyLarge.lineHeight * multiplier),
    bodyMedium = bodyMedium.copy(lineHeight = bodyMedium.lineHeight * multiplier),
    bodySmall = bodySmall.copy(lineHeight = bodySmall.lineHeight * multiplier),
    labelLarge = labelLarge.copy(lineHeight = labelLarge.lineHeight * multiplier),
    labelMedium = labelMedium.copy(lineHeight = labelMedium.lineHeight * multiplier),
    labelSmall = labelSmall.copy(lineHeight = labelSmall.lineHeight * multiplier),
)
