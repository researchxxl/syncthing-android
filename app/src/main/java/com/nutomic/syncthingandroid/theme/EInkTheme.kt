package com.nutomic.syncthingandroid.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nutomic.syncthingandroid.util.EInkUtil

/**
 * E-Ink optimized theme for electronic ink displays
 *
 * Provides high contrast, minimal animation, and optimized colors
 * for E-Ink screens found in e-readers like Onyx Boox, Kindle, etc.
 */
object EInkTheme {

    /**
     * High contrast light theme for E-Ink displays
     * (black text on white background for best readability)
     */
    private val EInkLightColorScheme = lightColorScheme(
        primary = Color.Black,
        onPrimary = Color.White,
        primaryContainer = Color.White,
        onPrimaryContainer = Color.Black,

        secondary = Color(0xFF404040),  // Dark gray instead of pure black
        onSecondary = Color.White,

        tertiary = Color(0xFF606060),  // Medium gray
        onTertiary = Color.White,

        error = Color(0xFFCCCCCC),  // Light gray for error backgrounds
        onError = Color.Black,

        background = Color.White,
        onBackground = Color.Black,

        surface = Color.White,
        onSurface = Color.Black,

        surfaceVariant = Color(0xFFF5F5F5),  // Very light gray
        onSurfaceVariant = Color.Black,

        outline = Color(0xFF808080),  // Medium gray for borders
        outlineVariant = Color(0xFFCCCCCC),  // Light gray for subtle borders
    )

    /**
     * High contrast dark theme for E-Ink displays
     * (white text on black background)
     */
    private val EInkDarkColorScheme = darkColorScheme(
        primary = Color.White,
        onPrimary = Color.Black,
        primaryContainer = Color.Black,
        onPrimaryContainer = Color.White,

        secondary = Color(0xFFB0B0B0),  // Light gray
        onSecondary = Color.Black,

        tertiary = Color(0xFF909090),  // Medium-light gray
        onTertiary = Color.Black,

        error = Color(0xFF404040),  // Dark gray for error backgrounds
        onError = Color.White,

        background = Color.Black,
        onBackground = Color.White,

        surface = Color.Black,
        onSurface = Color.White,

        surfaceVariant = Color(0xFF1A1A1A),  // Very dark gray
        onSurfaceVariant = Color.White,

        outline = Color(0xFF808080),  // Medium gray for borders
        outlineVariant = Color(0xFF404040),  // Dark gray for subtle borders
    )

    /**
     * Get optimized color scheme for E-Ink devices
     *
     * @param darkTheme Whether to use dark theme
     * @return Optimized color scheme
     */
    @Composable
    fun getEInkColorScheme(darkTheme: Boolean): ColorScheme {
        return if (darkTheme) {
            EInkDarkColorScheme
        } else {
            EInkLightColorScheme
        }
    }

    /**
     * Check if E-Ink theme should be used
     *
     * @param context Application context
     * @return true if E-Ink theme should be used
     */
    fun shouldUseEInkTheme(context: Context): Boolean {
        return EInkUtil.isEInkDevice(context)
    }

    /**
     * Configure activity for E-Ink display
     *
     * @param activity Activity to configure
     */
    fun configureForEInk(activity: Activity) {
        if (!EInkUtil.isEInkDevice(activity)) {
            return
        }

        val window = activity.window

        // Keep screen on while syncing (E-Ink doesn't use power for static display)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Disable hardware acceleration (may improve E-Ink rendering)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            try {
                window.setFlags(
                    0,
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                )
            } catch (e: Exception) {
                // Some devices don't allow disabling hardware acceleration
            }
        }

        // Set full screen mode to maximize reading area
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.flags = window.flags or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
    }

    /**
     * Get recommended text style for E-Ink displays
     * (larger, high contrast text)
     *
     * @param context Application context
     * @param baseStyle Base material typography
     * @return Optimized typography
     */
    fun getEInkTypography(context: Context, baseStyle: Typography): Typography {
        val scale = EInkUtil.getOptimalTextSize(context, 1.0f)

        return baseStyle.copy(
            displayLarge = baseStyle.displayLarge.copy(fontSize = baseStyle.displayLarge.fontSize * scale),
            displayMedium = baseStyle.displayMedium.copy(fontSize = baseStyle.displayMedium.fontSize * scale),
            displaySmall = baseStyle.displaySmall.copy(fontSize = baseStyle.displaySmall.fontSize * scale),

            headlineLarge = baseStyle.headlineLarge.copy(fontSize = baseStyle.headlineLarge.fontSize * scale),
            headlineMedium = baseStyle.headlineMedium.copy(fontSize = baseStyle.headlineMedium.fontSize * scale),
            headlineSmall = baseStyle.headlineSmall.copy(fontSize = baseStyle.headlineSmall.fontSize * scale),

            titleLarge = baseStyle.titleLarge.copy(fontSize = baseStyle.titleLarge.fontSize * scale),
            titleMedium = baseStyle.titleMedium.copy(fontSize = baseStyle.titleMedium.fontSize * scale),
            titleSmall = baseStyle.titleSmall.copy(fontSize = baseStyle.titleSmall.fontSize * scale),

            bodyLarge = baseStyle.bodyLarge.copy(fontSize = baseStyle.bodyLarge.fontSize * scale),
            bodyMedium = baseStyle.bodyMedium.copy(fontSize = baseStyle.bodyMedium.fontSize * scale),
            bodySmall = baseStyle.bodySmall.copy(fontSize = baseStyle.bodySmall.fontSize * scale),

            labelLarge = baseStyle.labelLarge.copy(fontSize = baseStyle.labelLarge.fontSize * scale),
            labelMedium = baseStyle.labelMedium.copy(fontSize = baseStyle.labelMedium.fontSize * scale),
            labelSmall = baseStyle.labelSmall.copy(fontSize = baseStyle.labelSmall.fontSize * scale),
        )
    }

    /**
     * Get E-Ink optimized shape (minimal rounded corners)
     *
     * @return Shape with minimal or no rounded corners
     */
    fun getEInkShapes(): Shapes {
        return Shapes(
            extraSmall = 0.dp,      // No rounding for small elements
            small = 2.dp,            // Minimal rounding for small elements
            medium = 4.dp,           // Minimal rounding for medium elements
            large = 6.dp,            // Minimal rounding for large elements
            extraLarge = 8.dp        // Minimal rounding for extra large elements
        )
    }
}

/**
 * E-Ink optimized application theme
 *
 * @param darkTheme Whether to use dark theme (recommended for E-Ink)
 * @param content Content to display
 */
@Composable
fun EInkApplicationTheme(
    darkTheme: Boolean = true,  // Default to dark theme for E-Ink
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useEInkTheme = EInkTheme.shouldUseEInkTheme(context)

    val colorScheme = if (useEInkTheme) {
        EInkTheme.getEInkColorScheme(darkTheme)
    } else {
        // Use standard Material 3 dynamic color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme)
                MaterialTheme.dynamicDarkColorScheme(context)
            else
                MaterialTheme.dynamicLightColorScheme(context)
        } else {
            if (darkTheme)
                darkColorScheme()
            else
                lightColorScheme()
        }
    }

    val typography = if (useEInkTheme) {
        EInkTheme.getEInkTypography(context, MaterialTheme.typography)
    } else {
        MaterialTheme.typography
    }

    val shapes = if (useEInkTheme) {
        EInkTheme.getEInkShapes()
    } else {
        MaterialTheme.shapes
    }

    // Set system bars for E-Ink (high contrast)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (useEInkTheme) {
                // High contrast system bars for E-Ink
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content
    )
}

/**
 * E-Ink specific color extensions
 */
object EInkColors {
    // Pure black and white for maximum contrast
    val PureBlack = Color(0xFF000000)
    val PureWhite = Color(0xFFFFFFFF)

    // Grayscale colors for E-Ink
    val Gray10 = Color(0xFFE6E6E6)
    val Gray20 = Color(0xFFCCCCCC)
    val Gray30 = Color(0xFFB3B3B3)
    val Gray40 = Color(0xFF999999)
    val Gray50 = Color(0xFF808080)
    val Gray60 = Color(0xFF666666)
    val Gray70 = Color(0xFF4D4D4D)
    val Gray80 = Color(0xFF333333)
    val Gray90 = Color(0xFF1A1A1A)

    // High contrast accent colors (use sparingly)
    val AccentDark = Color(0xFF404040)    // Dark gray
    val AccentLight = Color(0xFFB0B0B0)   // Light gray

    // Status colors (high contrast)
    val Success = Color(0xFF404040)       // Dark gray for success on light
    val Warning = Color(0xFF808080)       // Medium gray
    val Error = Color(0xFF606060)         // Dark gray for errors
    val Info = Color(0xFF707070)          // Medium-dark gray
}

/**
 * Check if animations should be disabled for current device
 *
 * @return true if animations should be disabled
 */
@Composable
fun shouldDisableAnimations(): Boolean {
    val context = LocalContext.current
    return EInkUtil.shouldDisableAnimations(context)
}

/**
 * Get recommended animation duration multiplier
 * (longer for E-Ink to reduce ghosting)
 *
 * @return Duration multiplier (1.0 = normal)
 */
@Composable
fun getAnimationDurationMultiplier(): Float {
    val context = LocalContext.current
    return if (EInkUtil.isEInkDevice(context)) {
        0.0f  // Disable animations completely
    } else {
        1.0f  // Normal animations
    }
}
