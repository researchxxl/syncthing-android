package com.nutomic.syncthingandroid.util

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.ViewConfiguration
import java.io.File

/**
 * E-Ink screen utilities for electronic ink display optimization
 *
 * Provides device detection and optimization for E-Ink displays
 * commonly found in e-readers like Onyx Boox, Kindle, Kobo, etc.
 */
object EInkUtil {

    private const val TAG = "EInkUtil"

    // Known E-Ink device manufacturers and models
    private val E_INK_MANUFACTURERS = listOf(
        "onyx",     // Onyx Boox
        "fiction",  // Onyx Boox (alternative)
        "android",  // Generic Android (fallback)
        "kobo",     // Kobo
        "pocketbook", // PocketBook
        "tolino",   // Tolino
        "sony",     // Sony PRS
        "amazon",   // Kindle (old)
        "asus",     // Some Asus models have E-Ink displays
        "hisense",  // Hisense A5/A9 Pro CC
        "bigme",    // Bigme
        "fujitsu",  // Fujitsu Quaderno
        "reMarkable" // reMarkable tablets
    )

    private val E_INK_DEVICE_PREFIXES = listOf(
        "onyx",
        "eboox",   // Onyx Boox variations
        "kindle",  // Kindle devices
        "kobo",
        "pocketbook",
        "tolino",
        "hisense",
        "bigme",
        "remarkable"
    )

    // E-Ink specific build properties
    private val E_INK_BUILD_PROPERTIES = listOf(
        "ro.build.characteristics=nomodel,emulator,e-ink",
        "ro.sf.lcd_density",  // Often 160-300 for E-Ink
        "ro.product.cpu.abilty",  // E-Ink devices often have "64" or "32"
        "ro.build.product.e-ink"
    )

    /**
     * Check if current device has an E-Ink display
     *
     * @param context Application context
     * @return true if device has E-Ink display
     */
    fun isEInkDevice(context: Context): Boolean {
        // Check manufacturer
        val manufacturer = Build.MANUFACTURER.lowercase()
        if (E_INK_MANUFACTURERS.any { manufacturer.contains(it) }) {
            Log.d(TAG, "Detected E-Ink device by manufacturer: $manufacturer")
            return true
        }

        // Check device name
        val device = Build.DEVICE.lowercase()
        if (E_INK_DEVICE_PREFIXES.any { device.startsWith(it) }) {
            Log.d(TAG, "Detected E-Ink device by device name: $device")
            return true
        }

        // Check model name
        val model = Build.MODEL.lowercase()
        if (E_INK_DEVICE_PREFIXES.any { model.contains(it) }) {
            Log.d(TAG, "Detected E-Ink device by model name: $model")
            return true
        }

        // Check build properties
        if (hasEInkBuildProperties()) {
            Log.d(TAG, "Detected E-Ink device by build properties")
            return true
        }

        // Check screen density (E-Ink typically has lower density)
        val density = context.resources.displayMetrics.densityDpi
        if (density <= 300) {  // E-Ink usually 160-300 dpi
            val isLowDensityDevice = density <= 300
            if (isLowDensityDevice && hasLongPressTimeout()) {
                Log.d(TAG, "Detected potential E-Ink device by density: $density dpi")
                return true
            }
        }

        return false
    }

    /**
     * Check if device supports partial refresh
     * (faster refresh but may leave ghosting)
     *
     * @param context Application context
     * @return true if partial refresh is supported
     */
    fun supportsPartialRefresh(context: Context): Boolean {
        if (!isEInkDevice(context)) {
            return false
        }

        // Most modern E-Ink devices support partial refresh
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }

    /**
     * Get recommended refresh interval for E-Ink devices
     * Longer than normal LCD to reduce ghosting
     *
     * @param context Application context
     * @return Refresh interval in milliseconds
     */
    fun getRecommendedRefreshInterval(context: Context): Long {
        return if (isEInkDevice(context)) {
            // E-Ink devices need longer refresh intervals
            2000L  // 2 seconds
        } else {
            500L   // 0.5 seconds for normal devices
        }
    }

    /**
     * Check if animation should be disabled
     * (E-Ink devices should minimize animations)
     *
     * @param context Application context
     * @return true if animations should be disabled
     */
    fun shouldDisableAnimations(context: Context): Boolean {
        return isEInkDevice(context)
    }

    /**
     * Check if high contrast mode is recommended
     * (E-Ink displays work better with high contrast)
     *
     * @param context Application context
     * @return true if high contrast is recommended
     */
    fun shouldUseHighContrast(context: Context): Boolean {
        return isEInkDevice(context)
    }

    /**
     * Get recommended UI update frequency
     * (Reduce updates to minimize refreshes)
     *
     * @param context Application context
     * @return Update interval in milliseconds
     */
    fun getRecommendedUIUpdateInterval(context: Context): Long {
        return if (isEInkDevice(context)) {
            3000L  // Update every 3 seconds max
        } else {
            500L   // Update every 0.5 seconds
        }
    }

    /**
     * Check if device has E-Ink specific build properties
     */
    private fun hasEInkBuildProperties(): Boolean {
        return try {
            val properties = File("/system/build.prop")
            if (properties.exists()) {
                properties.readText().lines().any { line ->
                    E_INK_BUILD_PROPERTIES.any { prop ->
                        line.startsWith(prop.split("=")[0]) &&
                        line.lowercase().contains("e-ink")
                    }
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if device has long press timeout (common on E-Ink)
     */
    private fun hasLongPressTimeout(): Boolean {
        return try {
            val pressTimeout = ViewConfiguration.getLongPressTimeout()
            pressTimeout > 500  // E-Ink devices often have >500ms timeout
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get device type for logging/analytics
     *
     * @param context Application context
     * @return Device type string
     */
    fun getDeviceType(context: Context): String {
        return if (isEInkDevice(context)) {
            "eink"
        } else {
            "lcd"
        }
    }

    /**
     * Check if global refresh should be performed
     * (to clear ghosting on E-Ink displays)
     *
     * @param context Application context
     * @param lastGlobalRefreshTime Last global refresh timestamp
     * @param globalRefreshInterval Interval between global refreshes
     * @return true if global refresh should be performed
     */
    fun shouldPerformGlobalRefresh(
        context: Context,
        lastGlobalRefreshTime: Long,
        globalRefreshInterval: Long = 30000L  // 30 seconds default
    ): Boolean {
        if (!isEInkDevice(context)) {
            return false
        }

        val currentTime = System.currentTimeMillis()
        return (currentTime - lastGlobalRefreshTime) >= globalRefreshInterval
    }

    /**
     * Get optimal text size for E-Ink displays
     * (E-Ink benefits from slightly larger text)
     *
     * @param context Application context
     * @param defaultSize Default text size in SP
     * @return Optimal text size in SP
     */
    fun getOptimalTextSize(context: Context, defaultSize: Float): Float {
        return if (isEInkDevice(context)) {
            defaultSize * 1.1f  // 10% larger for E-Ink
        } else {
            defaultSize
        }
    }

    /**
     * Check if batch UI updates are recommended
     * (to reduce number of screen refreshes)
     *
     * @param context Application context
     * @return true if batch updates should be used
     */
    fun shouldBatchUIUpdates(context: Context): Boolean {
        return isEInkDevice(context)
    }

    /**
     * Get recommended batch size for UI updates
     *
     * @param context Application context
     * @return Number of updates to batch
     */
    fun getRecommendedUIBatchSize(context: Context): Int {
        return if (isEInkDevice(context)) {
            10  // Batch up to 10 updates
        } else {
            1   // Update immediately
        }
    }

    /**
     * Check if progressive rendering should be used
     * (show content incrementally to reduce refreshes)
     *
     * @param context Application context
     * @return true if progressive rendering should be used
     */
    fun shouldUseProgressiveRendering(context: Context): Boolean {
        return isEInkDevice(context)
    }

    /**
     * Get optimal sync batch size for E-Ink devices
     * (reduce sync operations to minimize UI updates)
     *
     * @param context Application context
     * @param defaultBatchSize Default batch size
     * @return Optimal batch size
     */
    fun getOptimalSyncBatchSize(context: Context, defaultBatchSize: Int): Int {
        return if (isEInkDevice(context)) {
            maxOf(5, defaultBatchSize * 2)  // Larger batches to reduce UI updates
        } else {
            defaultBatchSize
        }
    }

    /**
     * Check if device needs grayscale optimization
     * (most E-Ink displays are grayscale)
     *
     * @param context Application context
     * @return true if grayscale optimization should be applied
     */
    fun shouldUseGrayscale(context: Context): Boolean {
        return isEInkDevice(context)
    }

    /**
     * Get supported refresh modes for this device
     *
     * @param context Application context
     * @return List of supported refresh modes
     */
    fun getSupportedRefreshModes(context: Context): List<RefreshMode> {
        return if (isEInkDevice(context)) {
            listOf(
                RefreshMode.GLOBAL,     // Full refresh (slow, clears ghosting)
                RefreshMode.PARTIAL,    // Partial refresh (fast, may ghost)
                RefreshMode.A2          // Fast mode for simple changes (A2 waveform)
            )
        } else {
            listOf(RefreshMode.NORMAL)  // Normal LCD refresh
        }
    }

    /**
     * Refresh mode for E-Ink displays
     */
    enum class RefreshMode {
        GLOBAL,      // Global refresh (full screen, clears ghosting)
        PARTIAL,     // Partial refresh (faster, may have ghosting)
        A2,          // A2 waveform (very fast, black/white only)
        NORMAL       // Normal refresh for LCD displays
    }

    /**
     * Check if the device is a known E-Ink reader brand
     * (for special optimizations)
     *
     * @param context Application context
     * @return Device brand or "unknown"
     */
    fun getEInkBrand(context: Context): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()

        return when {
            manufacturer.contains("onyx") || model.contains("eboox") -> "onyx"
            model.contains("kindle") -> "kindle"
            model.contains("kobo") -> "kobo"
            model.contains("pocketbook") -> "pocketbook"
            model.contains("tolino") -> "tolino"
            manufacturer.contains("hisense") -> "hisense"
            model.contains("bigme") -> "bigme"
            model.contains("remarkable") -> "remarkable"
            isEInkDevice(context) -> "generic"
            else -> "unknown"
        }
    }

    /**
     * Get device-specific optimizations
     *
     * @param context Application context
     * @return Map of optimization settings
     */
    fun getDeviceOptimizations(context: Context): Map<String, Any> {
        return if (isEInkDevice(context)) {
            mapOf(
                "disable_animations" to true,
                "use_high_contrast" to true,
                "use_grayscale" to true,
                "reduce_ui_updates" to true,
                "batch_operations" to true,
                "longer_refresh_intervals" to true,
                "larger_text" to true,
                "prefer_dark_theme" to true,
                "minimize_scroll" to true,
                "use_progressive_loading" to true
            )
        } else {
            emptyMap()
        }
    }

    /**
     * Check if the device is likely an Onyx Boox device
     *
     * @param context Application context
     * @return true if device is Onyx Boox
     */
    fun isOnyxBoox(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val device = Build.DEVICE.lowercase()

        return manufacturer.contains("onyx") ||
               manufacturer.contains("fiction") ||
               model.contains("eboox") ||
               device.contains("onyx")
    }

    /**
     * Check if device supports WACOM pen input
     * (common on Onyx Boox devices)
     *
     * @param context Application context
     * @return true if device supports pen input
     */
    fun supportsPenInput(context: Context): Boolean {
        if (!isEInkDevice(context)) {
            return false
        }

        // Most Onyx Boox devices support WACOM pen
        val model = Build.MODEL.lowercase()
        return model.contains("note") ||
               model.contains("tab") ||
               model.contains("palma") ||
               model.contains("page") ||
               model.contains("kon-tiki")
    }
}
