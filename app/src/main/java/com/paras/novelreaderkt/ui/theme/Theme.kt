package com.paras.novelreaderkt.ui.theme

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

// ═══════════════════════════════════════════════════════
//  THEME 1 — Night Dark  (improved)
//  Deep purple-black with refined violet accents
// ═══════════════════════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

// ═══════════════════════════════════════════════════════
//  THEME 2 — Slate Grey  (improved)
//  Cool steel-grey with blue undertones
// ═══════════════════════════════════════════════════════
private val GreyColorScheme = darkColorScheme(
    primary = Color(0xFFB8C4D4),
    onPrimary = Color(0xFF1B2536),
    primaryContainer = Color(0xFF2C3545),
    onPrimaryContainer = Color(0xFFD3DFEF),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF1E2D38),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFCFD8DC),
    tertiary = Color(0xFF90CAF9),
    onTertiary = Color(0xFF0D3B66),
    tertiaryContainer = Color(0xFF1A4A7A),
    onTertiaryContainer = Color(0xFFD6E8FF),
    background = Color(0xFF12161B),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF3A4250),
    onSurfaceVariant = Color(0xFFC3C7CF),
    surfaceContainerLowest = Color(0xFF0D1117),
    surfaceContainerLow = Color(0xFF181D24),
    surfaceContainer = Color(0xFF1C222A),
    surfaceContainerHigh = Color(0xFF272E38),
    surfaceContainerHighest = Color(0xFF333B47),
    outline = Color(0xFF6B7A8D),
    outlineVariant = Color(0xFF3A4250),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF2A3240),
    inversePrimary = Color(0xFF4A5C78),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ═══════════════════════════════════════════════════════
//  THEME 3 — Pristine Light  (improved)
//  Clean, high-contrast light with rich blue
// ═══════════════════════════════════════════════════════
private val WhiteColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5B8A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDDCFF),
    onTertiaryContainer = Color(0xFF261542),
    background = Color(0xFFF8F9FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E4EC),
    onSurfaceVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F8),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE0E2E6),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C7D0),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF9ECAFF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ═══════════════════════════════════════════════════════
//  THEME 4 — Warm Sepia  (improved)
//  Paper-like warmth with rich brown-copper accents
// ═══════════════════════════════════════════════════════
private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF8B5E3C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCD3),
    onPrimaryContainer = Color(0xFF3B1500),
    secondary = Color(0xFF7A5F52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF6B5840),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DFBF),
    onTertiaryContainer = Color(0xFF241A04),
    background = Color(0xFFFBF4EB),
    surface = Color(0xFFF5EDE2),
    onBackground = Color(0xFF201A15),
    onSurface = Color(0xFF201A15),
    surfaceVariant = Color(0xFFEFE0D8),
    onSurfaceVariant = Color(0xFF52443B),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F0E6),
    surfaceContainer = Color(0xFFF3EBE1),
    surfaceContainerHigh = Color(0xFFEDE5DB),
    surfaceContainerHighest = Color(0xFFE8DFD5),
    outline = Color(0xFF847469),
    outlineVariant = Color(0xFFD6C3B8),
    inverseSurface = Color(0xFF362F29),
    inverseOnSurface = Color(0xFFFAEEE4),
    inversePrimary = Color(0xFFFFB68C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ═══════════════════════════════════════════════════════
//  THEME 5 — Forest Green  (improved)
//  Deep woodland dark with emerald-gold accents
// ═══════════════════════════════════════════════════════
private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0C3313),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B4D22),
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = Color(0xFFFFD54F),
    onTertiary = Color(0xFF3E3000),
    tertiaryContainer = Color(0xFF5D4B00),
    onTertiaryContainer = Color(0xFFFFE173),
    background = Color(0xFF0A1810),
    onBackground = Color(0xFFE8F5E9),
    surface = Color(0xFF0F2118),
    onSurface = Color(0xFFE0F2E1),
    surfaceVariant = Color(0xFF2D4E32),
    onSurfaceVariant = Color(0xFFB8D4BA),
    surfaceContainerLowest = Color(0xFF06120B),
    surfaceContainerLow = Color(0xFF111E16),
    surfaceContainer = Color(0xFF16281E),
    surfaceContainerHigh = Color(0xFF213229),
    surfaceContainerHighest = Color(0xFF2D3E34),
    outline = Color(0xFF6B8F6E),
    outlineVariant = Color(0xFF2D4E32),
    inverseSurface = Color(0xFFE0F2E1),
    inverseOnSurface = Color(0xFF0D1F14),
    inversePrimary = Color(0xFF3A7D42),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ═══════════════════════════════════════════════════════
//  THEME 6 — Ocean Blue  (improved)
//  Deep ocean dark with cyan-blue gradients
// ═══════════════════════════════════════════════════════
private val OceanColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD6E8FF),
    secondary = Color(0xFF80DEEA),
    onSecondary = Color(0xFF003B46),
    secondaryContainer = Color(0xFF006064),
    onSecondaryContainer = Color(0xFFB2EBF2),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF4A1258),
    tertiaryContainer = Color(0xFF6A2D7A),
    onTertiaryContainer = Color(0xFFF0D4FF),
    background = Color(0xFF040D18),
    onBackground = Color(0xFFE3F2FD),
    surface = Color(0xFF081828),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF154360),
    onSurfaceVariant = Color(0xFFD4E6F1),
    surfaceContainerLowest = Color(0xFF020810),
    surfaceContainerLow = Color(0xFF0A1C30),
    surfaceContainer = Color(0xFF0F2440),
    surfaceContainerHigh = Color(0xFF192E4E),
    surfaceContainerHighest = Color(0xFF243A5C),
    outline = Color(0xFF4A7AAF),
    outlineVariant = Color(0xFF154360),
    inverseSurface = Color(0xFFE3F2FD),
    inverseOnSurface = Color(0xFF0A1929),
    inversePrimary = Color(0xFF1565C0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ═══════════════════════════════════════════════════════
//  THEME 7 — Cosmic Slate  (NEW)
//  Deep-space dark with amber warmth + teal audio accents.
//  "Reading Theater" aesthetic — immersive modernism.
// ═══════════════════════════════════════════════════════
private val CosmicSlateColorScheme = darkColorScheme(
    primary = Color(0xFFFFB77D),
    onPrimary = Color(0xFF4D2600),
    primaryContainer = Color(0xFFD97707),
    onPrimaryContainer = Color(0xFFFFE7CC),
    secondary = Color(0xFF89CEFF),
    onSecondary = Color(0xFF00344D),
    secondaryContainer = Color(0xFF00A2E6),
    onSecondaryContainer = Color(0xFFC9E6FF),
    tertiary = Color(0xFFC1C7CF),
    onTertiary = Color(0xFF2B3137),
    tertiaryContainer = Color(0xFF4B5159),
    onTertiaryContainer = Color(0xFFDDE3EB),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE0E3E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE0E3E8),
    surfaceVariant = Color(0xFF31353A),
    onSurfaceVariant = Color(0xFFDBC2B0),
    surfaceContainerLowest = Color(0xFF0B0F12),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2F),
    surfaceContainerHighest = Color(0xFF31353A),
    outline = Color(0xFFA38C7C),
    outlineVariant = Color(0xFF554336),
    inverseSurface = Color(0xFFE0E3E8),
    inverseOnSurface = Color(0xFF2D3135),
    inversePrimary = Color(0xFF904D00),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ═══════════════════════════════════════════════════════
//  THEME 8 — Bauhaus  (NEW)
//  High-contrast, geometric, bold primary reds + deep blacks.
//  Inspired by the Bauhaus design movement — stark, bold, functional.
// ═══════════════════════════════════════════════════════
private val BauhausColorScheme = lightColorScheme(
    primary = Color(0xFFD42B2B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF2B5EA7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD4E3FF),
    onSecondaryContainer = Color(0xFF001B3D),
    tertiary = Color(0xFFDAA520),
    onTertiary = Color(0xFF3E2800),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF3E2800),
    background = Color(0xFFFAFAF5),
    surface = Color(0xFFF5F5EE),
    onBackground = Color(0xFF1A1A18),
    onSurface = Color(0xFF1A1A18),
    surfaceVariant = Color(0xFFE2E2D8),
    onSurfaceVariant = Color(0xFF44483E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F0E8),
    surfaceContainer = Color(0xFFEAEADE),
    surfaceContainerHigh = Color(0xFFE4E4D8),
    surfaceContainerHighest = Color(0xFFDEDED2),
    outline = Color(0xFF747870),
    outlineVariant = Color(0xFFC4C8BC),
    inverseSurface = Color(0xFF2F302C),
    inverseOnSurface = Color(0xFFF2F1EA),
    inversePrimary = Color(0xFFFFB3AD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

// ═══════════════════════════════════════════════════════
//  Theme resolver
// ═══════════════════════════════════════════════════════
@Composable
fun NovelReaderV3Theme(
    themeName: String = "Dark",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeName) {
        "Grey"          -> GreyColorScheme
        "White"         -> WhiteColorScheme
        "Sepia"         -> SepiaColorScheme
        "Forest"        -> ForestColorScheme
        "Ocean"         -> OceanColorScheme
        "CosmicSlate"   -> CosmicSlateColorScheme
        "Bauhaus"       -> BauhausColorScheme
        else            -> DarkColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}