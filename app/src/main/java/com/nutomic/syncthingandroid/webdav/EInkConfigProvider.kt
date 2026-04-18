package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.content.SharedPreferences
import com.nutomic.syncthingandroid.util.EInkUtil

/**
 * E-Ink configuration provider
 *
 * Manages E-Ink specific settings and optimizations
 */
class EInkConfigProvider(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "eink_config"
        private const val KEY_EINK_ENABLED = "eink_enabled"
        private const val KEY_EINK_DETECTED = "eink_detected"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_DISABLE_ANIMATIONS = "disable_animations"
        private const val KEY_REDUCED_REFRESH = "reduced_refresh"
        private const val KEY_OPTIMIZED_SYNC = "optimized_sync"
        private const val KEY_DARK_THEME = "dark_theme"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val isEInkDevice = EInkUtil.isEInkDevice(context)

    init {
        // Auto-detect E-Ink on first run
        if (!prefs.contains(KEY_EINK_DETECTED)) {
            prefs.edit().putBoolean(KEY_EINK_DETECTED, isEInkDevice).apply()
            Log.i("EInkConfig", "E-Ink device detected: $isEInkDevice")
        }
    }

    /**
     * Check if E-Ink optimizations are enabled
     */
    fun isEInkEnabled(): Boolean {
        return prefs.getBoolean(KEY_EINK_ENABLED, isEInkDevice)
    }

    /**
     * Enable or disable E-Ink optimizations
     */
    fun setEInkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EINK_ENABLED, enabled).apply()
        Log.i("EInkConfig", "E-Ink optimizations: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if high contrast mode is enabled
     */
    fun isHighContrastEnabled(): Boolean {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, isEInkDevice)
    }

    /**
     * Set high contrast mode
     */
    fun setHighContrastEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
    }

    /**
     * Check if animations are disabled
     */
    fun areAnimationsDisabled(): Boolean {
        return prefs.getBoolean(KEY_DISABLE_ANIMATIONS, isEInkDevice)
    }

    /**
     * Set animations disabled
     */
    fun setAnimationsDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_ANIMATIONS, disabled).apply()
    }

    /**
     * Check if reduced refresh mode is enabled
     */
    fun isReducedRefreshEnabled(): Boolean {
        return prefs.getBoolean(KEY_REDUCED_REFRESH, isEInkDevice)
    }

    /**
     * Set reduced refresh mode
     */
    fun setReducedRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REDUCED_REFRESH, enabled).apply()
    }

    /**
     * Check if optimized sync is enabled
     */
    fun isOptimizedSyncEnabled(): Boolean {
        return prefs.getBoolean(KEY_OPTIMIZED_SYNC, isEInkDevice)
    }

    /**
     * Set optimized sync
     */
    fun setOptimizedSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPTIMIZED_SYNC, enabled).apply()
    }

    /**
     * Check if dark theme is preferred
     */
    fun isDarkThemePreferred(): Boolean {
        return prefs.getBoolean(KEY_DARK_THEME, isEInkDevice)
    }

    /**
     * Set dark theme preference
     */
    fun setDarkThemePreferred(preferred: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_THEME, preferred).apply()
    }

    /**
     * Get all E-Ink settings as map
     */
    fun getAllSettings(): Map<String, Boolean> {
        return mapOf(
            "eink_enabled" to isEInkEnabled(),
            "eink_detected" to isEInkDevice,
            "high_contrast" to isHighContrastEnabled(),
            "disable_animations" to areAnimationsDisabled(),
            "reduced_refresh" to isReducedRefreshEnabled(),
            "optimized_sync" to isOptimizedSyncEnabled(),
            "dark_theme" to isDarkThemePreferred()
        )
    }

    /**
     * Apply recommended E-Ink settings
     */
    fun applyRecommendedSettings() {
        if (!isEInkDevice) {
            return
        }

        prefs.edit().apply {
            putBoolean(KEY_EINK_ENABLED, true)
            putBoolean(KEY_HIGH_CONTRAST, true)
            putBoolean(KEY_DISABLE_ANIMATIONS, true)
            putBoolean(KEY_REDUCED_REFRESH, true)
            putBoolean(KEY_OPTIMIZED_SYNC, true)
            putBoolean(KEY_DARK_THEME, true)
            apply()
        }

        Log.i("EInkConfig", "Applied recommended E-Ink settings")
    }

    /**
     * Reset to default settings
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        Log.i("EInkConfig", "Reset E-Ink settings to defaults")
    }

    /**
     * Get device information
     */
    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "manufacturer" to android.os.Build.MANUFACTURER,
            "model" to android.os.Build.MODEL,
            "device" to android.os.Build.DEVICE,
            "eink_brand" to EInkUtil.getEInkBrand(context),
            "is_eink" to isEInkDevice.toString(),
            "supports_partial_refresh" to EInkUtil.supportsPartialRefresh(context).toString(),
            "supports_pen" to EInkUtil.supportsPenInput(context).toString()
        )
    }
}
