package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.util.Log
import com.nutomic.syncthingandroid.util.EInkUtil
import com.nutomic.syncthingandroid.webdav.model.SyncProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * E-Ink optimized sync manager
 *
 * Provides sync optimizations specifically for E-Ink displays
 * including batched UI updates, reduced refreshes, and power-efficient operations.
 */
class EInkSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "EInkSyncManager"

        // E-Ink specific settings
        private const val DEFAULT_UI_BATCH_SIZE = 10
        private const val DEFAULT_UI_UPDATE_INTERVAL = 3000L  // 3 seconds
        private const val DEFAULT_GLOBAL_REFRESH_INTERVAL = 30000L  // 30 seconds
    }

    private val isEInkDevice = EInkUtil.isEInkDevice(context)
    private val syncScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // UI update batching
    private val pendingUIUpdates = mutableMapOf<String, SyncProgress>()
    private var lastUIUpdateTime = 0L

    // Global refresh tracking
    private var lastGlobalRefreshTime = 0L
    private var globalRefreshInterval = DEFAULT_GLOBAL_REFRESH_INTERVAL

    // UI update state
    private val _uiUpdateState = MutableStateFlow<UIUpdateState>(UIUpdateState.Idle)
    val uiUpdateState: StateFlow<UIUpdateState> = _uiUpdateState

    /**
     * Optimize sync folder config for E-Ink device
     *
     * @param config Original folder config
     * @return Optimized config for E-Ink
     */
    fun optimizeSyncConfig(config: SyncFolderConfig): SyncFolderConfig {
        if (!isEInkDevice) {
            return config
        }

        return config.copy(
            // Use larger batch size to reduce UI updates
            syncMode = if (config.syncMode == com.nutomic.syncthingandroid.webdav.model.SyncMode.BIDIRECTIONAL) {
                com.nutomic.syncthingandroid.webdav.model.SyncMode.BIDIRECTIONAL
            } else {
                config.syncMode
            }
        )
    }

    /**
     * Batch UI updates for E-Ink devices
     *
     * @param folderId Folder identifier
     * @param progress Sync progress
     * @param forceUpdate Force immediate update
     */
    fun updateUIProgress(
        folderId: String,
        progress: SyncProgress,
        forceUpdate: Boolean = false
    ) {
        if (!isEInkDevice) {
            // Normal devices: immediate update
            _uiUpdateState.value = UIUpdateState.Update(progress)
            return
        }

        // E-Ink devices: batch updates
        pendingUIUpdates[folderId] = progress

        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastUIUpdateTime
        val shouldUpdate = forceUpdate ||
                          timeSinceLastUpdate >= DEFAULT_UI_UPDATE_INTERVAL ||
                          progress.status == com.nutomic.syncthingandroid.webdav.SyncStatus.COMPLETED ||
                          progress.status == com.nutomic.syncthingandroid.webdav.SyncStatus.ERROR

        if (shouldUpdate) {
            // Flush all pending updates
            flushUIUpdates()
            lastUIUpdateTime = currentTime
        }
    }

    /**
     * Flush all pending UI updates
     */
    private fun flushUIUpdates() {
        if (pendingUIUpdates.isEmpty()) {
            return
        }

        // Get the most recent update for each folder
        val latestUpdates = pendingUIUpdates.values.toList()
        pendingUIUpdates.clear()

        _uiUpdateState.value = UIUpdateState.BatchUpdate(latestUpdates)

        Log.d(TAG, "Flushed ${latestUpdates.size} batched UI updates")
    }

    /**
     * Check if global refresh should be performed
     *
     * @return true if global refresh is needed
     */
    fun needsGlobalRefresh(): Boolean {
        return EInkUtil.shouldPerformGlobalRefresh(
            context,
            lastGlobalRefreshTime,
            globalRefreshInterval
        )
    }

    /**
     * Mark global refresh as performed
     */
    fun markGlobalRefreshPerformed() {
        lastGlobalRefreshTime = System.currentTimeMillis()
        Log.d(TAG, "Global refresh performed")
    }

    /**
     * Get recommended sync batch size
     *
     * @return Optimal batch size for E-Ink
     */
    fun getRecommendedBatchSize(): Int {
        return EInkUtil.getOptimalSyncBatchSize(context, 10)
    }

    /**
     * Check if sync should be paused due to user interaction
     * (to avoid disrupting reading on E-Ink)
     *
     * @return true if sync should be paused
     */
    fun shouldPauseSync(): Boolean {
        if (!isEInkDevice) {
            return false
        }

        // Could check for user activity here
        // For now, return false
        return false
    }

    /**
     * Get optimal sync schedule for E-Ink devices
     *
     * @return Sync schedule configuration
     */
    fun getOptimalSyncSchedule(): SyncSchedule {
        return if (isEInkDevice) {
            SyncSchedule(
                syncInterval = 3600000L,  // 1 hour
                preferredTimes = listOf(
                    "02:00",  // 2 AM
                    "06:00",  // 6 AM
                    "12:00",  // 12 PM
                    "18:00"   // 6 PM
                ),
                avoidUserActiveHours = true,
                batchUpdates = true
            )
        } else {
            SyncSchedule(
                syncInterval = 1800000L,  // 30 minutes
                preferredTimes = emptyList(),
                avoidUserActiveHours = false,
                batchUpdates = false
            )
        }
    }

    /**
     * Check if sync should use aggressive power saving
     * (sync only when charging, or during specific times)
     *
     * @return true if aggressive power saving should be used
     */
    fun shouldUseAggressivePowerSaving(): Boolean {
        return isEInkDevice
    }

    /**
     * Get notification update strategy for E-Ink
     *
     * @return Notification strategy
     */
    fun getNotificationStrategy(): NotificationStrategy {
        return if (isEInkDevice) {
            NotificationStrategy.Minimal(
                updateInterval = 10000L,  // Update every 10 seconds max
                batchUpdates = true,
                hideProgress = false,     // Show progress but update infrequently
                prioritizeCompletion = true
            )
        } else {
            NotificationStrategy.Normal(
                updateInterval = 1000L,   // Update every second
                batchUpdates = false,
                hideProgress = false,
                prioritizeCompletion = false
            )
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        syncScope.cancel()
    }

    /**
     * UI update state
     */
    sealed class UIUpdateState {
        object Idle : UIUpdateState()
        data class Update(val progress: SyncProgress) : UIUpdateState()
        data class BatchUpdate(val updates: List<SyncProgress>) : UIUpdateState()
    }

    /**
     * Sync schedule configuration
     */
    data class SyncSchedule(
        val syncInterval: Long,
        val preferredTimes: List<String>,
        val avoidUserActiveHours: Boolean,
        val batchUpdates: Boolean
    )

    /**
     * Notification update strategy
     */
    sealed class NotificationStrategy {
        data class Normal(
            val updateInterval: Long,
            val batchUpdates: Boolean,
            val hideProgress: Boolean,
            val prioritizeCompletion: Boolean
        ) : NotificationStrategy()

        data class Minimal(
            val updateInterval: Long,
            val batchUpdates: Boolean,
            val hideProgress: Boolean,
            val prioritizeCompletion: Boolean
        ) : NotificationStrategy()
    }

    /**
     * Check if device is E-Ink
     *
     * @return true if device has E-Ink display
     */
    fun isEInkDevice(): Boolean = isEInkDevice

    /**
     * Get E-Ink device brand
     *
     * @return Device brand or "unknown"
     */
    fun getDeviceBrand(): String {
        return EInkUtil.getEInkBrand(context)
    }

    /**
     * Get device optimizations as map
     *
     * @return Map of optimization settings
     */
    fun getDeviceOptimizations(): Map<String, Boolean> {
        return EInkUtil.getDeviceOptimizations(context).mapValues { it.value as Boolean }
    }
}
