package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF25232A),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

private val GreyColorScheme = darkColorScheme(
    primary = Color(0xFFA8B2C1),
    onPrimary = Color(0xFF1E2530),
    primaryContainer = Color(0xFF2C3545),
    onPrimaryContainer = Color(0xFFD3DFEF),
    secondary = Color(0xFFB0BEC5),
    onSecondary = Color(0xFF263238),
    secondaryContainer = Color(0xFF37474F),
    onSecondaryContainer = Color(0xFFCFD8DC),
    background = Color(0xFF181C20),
    surface = Color(0xFF202428),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199)
)

private val WhiteColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFFAF9F6),
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F)
)

private val SepiaColorScheme = lightColorScheme(
    primary = Color(0xFF8D5B4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCD3),
    onPrimaryContainer = Color(0xFF3B0B00),
    secondary = Color(0xFF705C56),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFBDCD5),
    onSecondaryContainer = Color(0xFF281814),
    background = Color(0xFFFBF4EB),
    surface = Color(0xFFF5EBE1),
    onBackground = Color(0xFF201A18),
    onSurface = Color(0xFF201A18),
    surfaceVariant = Color(0xFFEFE0DC),
    onSurfaceVariant = Color(0xFF4C4441),
    outline = Color(0xFF837470)
)

private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF0C3813),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFE8F5E9),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B5E20),
    secondaryContainer = Color(0xFF2E7D32),
    onSecondaryContainer = Color(0xFFC8E6C9),
    background = Color(0xFF0D1B0F),
    surface = Color(0xFF152A18),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFF2D4E32),
    onSurfaceVariant = Color(0xFFC8E6C9),
    outline = Color(0xFF81C784)
)

private val OceanColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF0D47A1),
    secondaryContainer = Color(0xFF1E88E5),
    onSecondaryContainer = Color(0xFFBBDEFB),
    background = Color(0xFF050E1A),
    surface = Color(0xFF0A1E36),
    onBackground = Color(0xFFE3F2FD),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF154360),
    onSurfaceVariant = Color(0xFFD4E6F1),
    outline = Color(0xFF64B5F6)
)

@Composable
fun MyApplicationTheme(
    themeName: String = "Dark",
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeName) {
        "Grey" -> GreyColorScheme
        "White" -> WhiteColorScheme
        "Sepia" -> SepiaColorScheme
        "Forest" -> ForestColorScheme
        "Ocean" -> OceanColorScheme
        else -> DarkColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
