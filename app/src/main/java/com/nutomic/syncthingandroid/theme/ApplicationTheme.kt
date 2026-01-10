package com.nutomic.syncthingandroid.theme

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

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDE9FF),
    onPrimaryContainer = Color(0xFF001F2C),
    secondary = Color(0xFF4F6168),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5ED),
    onSecondaryContainer = Color(0xFF0B1E25),
    tertiary = Color(0xFF5D5B7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3DFFF),
    onTertiaryContainer = Color(0xFF1A1937),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF81D4FF),
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D65),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFB7C9D1),
    onSecondary = Color(0xFF21333A),
    secondaryContainer = Color(0xFF374A51),
    onSecondaryContainer = Color(0xFFD3E5ED),
    tertiary = Color(0xFFC6C2EA),
    onTertiary = Color(0xFF2F2E4D),
    tertiaryContainer = Color(0xFF454365),
    onTertiaryContainer = Color(0xFFE3DFFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF121417),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF43474E)
)

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val sharedPreferences = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
    val dynamicColorsEnabled = sharedPreferences.getBoolean("dynamic_colors", true)
    
    val colorScheme =
        if (dynamicColorsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDarkTheme)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        } else {
            if (isDarkTheme)
                DarkColorScheme
            else
                LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
