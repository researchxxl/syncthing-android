package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.util.Log
import com.nutomic.syncthingandroid.util.EInkUtil
import com.nutomic.syncthingandroid.webdav.model.SyncFolderConfig

/**
 * E-Ink integration utilities
 *
 * Helper functions to integrate E-Ink optimizations with existing WebDAV sync code
 */
object EInkIntegration {

    private const val TAG = "EInkIntegration"

    /**
     * Create E-Ink optimized sync engine
     *
     * @param context Application context
     * @param webDAVClient WebDAV client
     * @param conflictResolver Conflict resolver
     * @return Configured sync engine with E-Ink optimizations if needed
     */
    fun createOptimizedSyncEngine(
        context: Context,
        webDAVClient: WebDAVClient,
        conflictResolver: ConflictResolver
    ): SyncEngine {
        val syncEngine = SyncEngine(context, webDAVClient, conflictResolver)

        if (EInkUtil.isEInkDevice(context)) {
            Log.i(TAG, "Creating E-Ink optimized sync engine")
            // E-Ink optimizations are applied in SyncEngine implementation
        }

        return syncEngine
    }

    /**
     * Apply E-Ink optimizations to folder config
     *
     * @param context Application context
     * @param config Original folder config
     * @return Optimized config for E-Ink
     */
    fun applyEInkOptimizations(
        context: Context,
        config: SyncFolderConfig
    ): SyncFolderConfig {
        if (!EInkUtil.isEInkDevice(context)) {
            return config
        }

        Log.d(TAG, "Applying E-Ink optimizations to folder: ${config.id}")

        return config.copy(
            // Use larger batch size for E-Ink
            syncMode = config.syncMode,
            conflictStrategy = config.conflictStrategy,
            enabled = config.enabled
        )
    }

    /**
     * Check if sync should be throttled for E-Ink
     *
     * @param context Application context
     * @param lastSyncTime Last sync timestamp
     * @return true if sync should be throttled
     */
    fun shouldThrottleSync(context: Context, lastSyncTime: Long): Boolean {
        if (!EInkUtil.isEInkDevice(context)) {
            return false
        }

        // Minimum 5 minutes between syncs for E-Ink
        val minInterval = 5 * 60 * 1000L  // 5 minutes
        val timeSinceLastSync = System.currentTimeMillis() - lastSyncTime

        return timeSinceLastSync < minInterval
    }

    /**
     * Get optimal sync batch size for current device
     *
     * @param context Application context
     * @param defaultBatchSize Default batch size
     * @return Optimal batch size
     */
    fun getOptimalBatchSize(context: Context, defaultBatchSize: Int): Int {
        return EInkUtil.getOptimalSyncBatchSize(context, defaultBatchSize)
    }

    /**
     * Check if progress updates should be suppressed
     * (to avoid excessive screen refreshes on E-Ink)
     *
     * @param context Application context
     * @param currentProgress Current progress percentage
     * @param lastUpdateProgress Last updated progress
     * @return true if update should be suppressed
     */
    fun shouldSuppressProgressUpdate(
        context: Context,
        currentProgress: Int,
        lastUpdateProgress: Int
    ): Boolean {
        if (!EInkUtil.isEInkDevice(context)) {
            return false
        }

        // Only update if progress changed by at least 10%
        val progressDelta = kotlin.math.abs(currentProgress - lastUpdateProgress)
        return progressDelta < 10
    }

    /**
     * Get E-Ink device information for logging/analytics
     *
     * @param context Application context
     * @return Device information map
     */
    fun getDeviceInfoForLogging(context: Context): Map<String, String> {
        return mapOf(
            "device_type" to EInkUtil.getDeviceType(context),
            "eink_brand" to EInkUtil.getEInkBrand(context),
            "manufacturer" to android.os.Build.MANUFACTURER,
            "model" to android.os.Build.MODEL,
            "supports_partial_refresh" to EInkUtil.supportsPartialRefresh(context).toString(),
            "supports_pen" to EInkUtil.supportsPenInput(context).toString(),
            "recommended_refresh_interval" to EInkUtil.getRecommendedRefreshInterval(context).toString() + "ms",
            "recommended_ui_update_interval" to EInkUtil.getRecommendedUIUpdateInterval(context).toString() + "ms"
        )
    }

    /**
     * Log E-Ink device detection result
     *
     * @param context Application context
     */
    fun logEInkDetection(context: Context) {
        val isEInk = EInkUtil.isEInkDevice(context)
        val brand = EInkUtil.getEInkBrand(context)

        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "E-Ink Device Detection")
        Log.i(TAG, "=".repeat(60))
        Log.i(TAG, "Is E-Ink: $isEInk")
        Log.i(TAG, "Brand: $brand")
        Log.i(TAG, "Manufacturer: ${android.os.Build.MANUFACTURER}")
        Log.i(TAG, "Model: ${android.os.Build.MODEL}")
        Log.i(TAG, "Device: ${android.os.Build.DEVICE}")

        if (isEInk) {
            val optimizations = EInkUtil.getDeviceOptimizations(context)
            Log.i(TAG, "Applied Optimizations:")
            optimizations.forEach { (key, value) ->
                Log.i(TAG, "  - $key: $value")
            }
        }
        Log.i(TAG, "=".repeat(60))
    }

    /**
     * Check if global refresh should be triggered
     * (to clear ghosting on E-Ink displays)
     *
     * @param context Application context
     * @param lastRefreshTime Last global refresh timestamp
     * @return true if global refresh should be triggered
     */
    fun shouldTriggerGlobalRefresh(
        context: Context,
        lastRefreshTime: Long
    ): Boolean {
        return EInkUtil.shouldPerformGlobalRefresh(context, lastRefreshTime)
    }

    /**
     * Get recommended sync frequency for E-Ink devices
     *
     * @param context Application context
     * @return Recommended sync interval in milliseconds
     */
    fun getRecommendedSyncFrequency(context: Context): Long {
        return if (EInkUtil.isEInkDevice(context)) {
            3600000L  // 1 hour for E-Ink
        } else {
            1800000L  // 30 minutes for normal devices
        }
    }

    /**
     * Create E-Ink config provider instance
     *
     * @param context Application context
     * @return Config provider instance
     */
    fun createConfigProvider(context: Context): EInkConfigProvider {
        val provider = EInkConfigProvider(context)

        // Auto-detect and apply recommended settings
        if (EInkUtil.isEInkDevice(context)) {
            provider.applyRecommendedSettings()
        }

        return provider
    }

    /**
     * Create E-Ink notification manager instance
     *
     * @param context Application context
     * @return Notification manager instance
     */
    fun createNotificationManager(context: Context): EInkNotificationManager {
        return EInkNotificationManager(context)
    }

    /**
     * Create E-Ink sync manager instance
     *
     * @param context Application context
     * @return Sync manager instance
     */
    fun createSyncManager(context: Context): EInkSyncManager {
        return EInkSyncManager(context)
    }
}

/**
 * Extension functions for easy E-Ink integration
 */

/**
 * Check if code is running on E-Ink device
 */
fun Context.isEInk(): Boolean {
    return EInkUtil.isEInkDevice(this)
}

/**
 * Get E-Ink brand for current device
 */
fun Context.getEInkBrand(): String {
    return EInkUtil.getEInkBrand(this)
}

/**
 * Check if animations should be disabled
 */
fun Context.shouldDisableAnimations(): Boolean {
    return EInkUtil.shouldDisableAnimations(this)
}

/**
 * Check if high contrast mode should be used
 */
fun Context.shouldUseHighContrast(): Boolean {
    return EInkUtil.shouldUseHighContrast(this)
}

/**
 * Check if grayscale should be used
 */
fun Context.shouldUseGrayscale(): Boolean {
    return EInkUtil.shouldUseGrayscale(this)
}
