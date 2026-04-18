package com.nutomic.syncthingandroid.webdav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.EInkUtil
import kotlinx.coroutines.*

/**
 * E-Ink optimized notification manager
 *
 * Provides notification optimizations specifically for E-Ink displays,
 * including reduced update frequency and batched notifications.
 */
class EInkNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "EInkNotificationManager"

        const val CHANNEL_ID_SYNC = "webdav_sync_eink_channel"
        const val CHANNEL_ID_CONFLICTS = "webdav_conflicts_eink_channel"
        const val NOTIFICATION_ID_SYNC = 2001

        // E-Ink specific settings
        private const val DEFAULT_UPDATE_INTERVAL = 5000L  // 5 seconds for E-Ink
        private const val DEFAULT_BATCH_SIZE = 5
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val isEInkDevice = EInkUtil.isEInkDevice(context)

    private var currentNotification: Notification? = null
    private var lastUpdateTime = 0L
    private var updateInterval = if (isEInkDevice) DEFAULT_UPDATE_INTERVAL else 1000L

    private val updateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var updateJob: Job? = null

    init {
        createNotificationChannels()
    }

    /**
     * Create notification channels optimized for E-Ink
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Sync notification channel
            val syncChannel = NotificationChannel(
                CHANNEL_ID_SYNC,
                "WebDAV Sync (E-Ink)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "WebDAV sync progress optimized for E-Ink displays"
                setShowBadge(false)
                setSound(null, null)  // No sound for E-Ink
                enableVibration(false)  // No vibration for E-Ink
                lightColor = if (isEInkDevice) {
                    android.graphics.Color.GRAY  // Gray for E-Ink
                } else {
                    android.graphics.Color.BLUE
                }
            }

            // Conflicts notification channel
            val conflictsChannel = NotificationChannel(
                CHANNEL_ID_CONFLICTS,
                "Sync Conflicts (E-Ink)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "File sync conflicts requiring attention"
                setShowBadge(true)
                setSound(null, null)  // No sound for E-Ink
                enableVibration(false)  // No vibration for E-Ink
            }

            notificationManager.createNotificationChannel(syncChannel)
            notificationManager.createNotificationChannel(conflictsChannel)
        }
    }

    /**
     * Show sync progress notification with E-Ink optimizations
     *
     * @param folderName Folder being synced
     * @param currentFile Current file being synced
     * @param progress Progress percentage (0-100)
     * @param forceUpdate Force immediate update
     */
    fun showSyncProgress(
        folderName: String,
        currentFile: String?,
        progress: Int,
        forceUpdate: Boolean = false
    ) {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastUpdate = currentTime - lastUpdateTime

        // For E-Ink, throttle updates unless forced
        if (isEInkDevice && !forceUpdate && timeSinceLastUpdate < updateInterval) {
            // Schedule update for later if not already scheduled
            if (updateJob == null) {
                updateJob = updateScope.launch {
                    delay(updateInterval - timeSinceLastUpdate)
                    showSyncProgress(folderName, currentFile, progress, forceUpdate = true)
                    updateJob = null
                }
            }
            return
        }

        // Cancel pending update job
        updateJob?.cancel()
        updateJob = null

        val notification = buildSyncProgressNotification(folderName, currentFile, progress)
        currentNotification = notification

        notificationManager.notify(NOTIFICATION_ID_SYNC, notification)
        lastUpdateTime = currentTime
    }

    /**
     * Build sync progress notification
     */
    private fun buildSyncProgressNotification(
        folderName: String,
        currentFile: String?,
        progress: Int
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle("Syncing: $folderName")
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // Content text
        if (currentFile != null) {
            builder.setContentText("File: $currentFile")
            if (isEInkDevice) {
                // For E-Ink, show simpler progress text
                builder.setContentText("$currentFile ($progress%)")
            }
        } else {
            builder.setContentText("Preparing...")
        }

        // Progress bar (disable for E-Ink to reduce refreshes)
        if (!isEInkDevice) {
            builder.setProgress(100, progress, false)
        } else {
            // For E-Ink, show progress in text instead of bar
            builder.setProgress(0, 0, false)
            if (currentFile != null) {
                builder.setContentText("$progress% - $currentFile")
            }
        }

        // High contrast for E-Ink
        if (isEInkDevice) {
            builder.setColorized(false)  // Disable icon colorization
        }

        return builder.build()
    }

    /**
     * Show sync completion notification
     *
     * @param folderName Folder that was synced
     * @param syncedCount Number of files synced
     * @param failedCount Number of files that failed
     * @param conflictsCount Number of conflicts
     */
    fun showSyncComplete(
        folderName: String,
        syncedCount: Int,
        failedCount: Int,
        conflictsCount: Int
    ) {
        val contentText = when {
            failedCount > 0 -> "Completed with errors: $syncedCount synced, $failedCount failed"
            conflictsCount > 0 -> "Completed with conflicts: $syncedCount synced, $conflictsCount conflicts"
            syncedCount == 0 -> "No files to sync"
            else -> "Synced $syncedCount files"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_SYNC)
            .setContentTitle("Sync Complete: $folderName")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(if (conflictsCount > 0) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        // High contrast for E-Ink
        if (isEInkDevice) {
            builder.setColorized(false)
        }

        notificationManager.notify(NOTIFICATION_ID_SYNC, builder.build())

        // Reset state
        currentNotification = null
        lastUpdateTime = 0L
    }

    /**
     * Show conflict notification with E-Ink optimizations
     *
     * @param folderName Folder with conflict
     * @param fileName Conflicting file name
     * @param conflictId Conflict identifier
     */
    fun showConflictNotification(
        folderName: String,
        fileName: String,
        conflictId: String
    ) {
        val intent = createConflictResolutionIntent(folderName, fileName, conflictId)
        val pendingIntent = PendingIntent.getActivity(
            context,
            conflictId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_CONFLICTS)
            .setContentTitle("Sync Conflict: $fileName")
            .setContentText("Folder: $folderName - Tap to resolve")
            .setSmallIcon(R.drawable.ic_warning)
            .setContentIntent(pendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)

        // Add action buttons (simplified for E-Ink)
        if (!isEInkDevice) {
            builder.addAction(
                R.drawable.ic_check,
                "Local",
                createConflictActionPendingIntent(conflictId, ConflictResolutionAction.LOCAL_WINS)
            )
            builder.addAction(
                R.drawable.ic_cloud_upload,
                "Remote",
                createConflictActionPendingIntent(conflictId, ConflictResolutionAction.REMOTE_WINS)
            )
        } else {
            // For E-Ink, just one action to open resolution
            builder.addAction(
                android.R.drawable.ic_menu_info_details,
                "Resolve",
                pendingIntent
            )
        }

        if (isEInkDevice) {
            builder.setColorized(false)
        }

        notificationManager.notify(conflictId.hashCode(), builder.build())
    }

    /**
     * Cancel sync notification
     */
    fun cancelSyncNotification() {
        notificationManager.cancel(NOTIFICATION_ID_SYNC)
        currentNotification = null
        lastUpdateTime = 0L

        // Cancel any pending update job
        updateJob?.cancel()
        updateJob = null
    }

    /**
     * Cancel conflict notification
     *
     * @param conflictId Conflict identifier
     */
    fun cancelConflictNotification(conflictId: String) {
        notificationManager.cancel(conflictId.hashCode())
    }

    /**
     * Update notification update interval
     *
     * @param interval Update interval in milliseconds
     */
    fun setUpdateInterval(interval: Long) {
        updateInterval = if (isEInkDevice) {
            maxOf(DEFAULT_UPDATE_INTERVAL, interval)  // Minimum 5 seconds for E-Ink
        } else {
            interval
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        updateJob?.cancel()
        updateScope.cancel()
    }

    /**
     * Create intent for conflict resolution activity
     */
    private fun createConflictResolutionIntent(
        folderName: String,
        fileName: String,
        conflictId: String
    ): Intent {
        // This would launch the conflict resolution activity
        // For now, return a dummy intent
        return Intent().apply {
            action = "com.nutomic.syncthingandroid.RESOLVE_CONFLICT"
            putExtra("folderName", folderName)
            putExtra("fileName", fileName)
            putExtra("conflictId", conflictId)
        }
    }

    /**
     * Create pending intent for conflict resolution action
     */
    private fun createConflictActionPendingIntent(
        conflictId: String,
        action: ConflictResolutionAction
    ): PendingIntent {
        val intent = Intent().apply {
            action = "com.nutomic.syncthingandroid.RESOLVE_CONFLICT_ACTION"
            putExtra("conflictId", conflictId)
            putExtra("resolutionAction", action.name)
        }

        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Check if notification updates should be batched
     *
     * @return true if batching should be used
     */
    fun shouldBatchNotifications(): Boolean {
        return isEInkDevice
    }

    /**
     * Get recommended notification batch size
     *
     * @return Number of updates to batch
     */
    fun getNotificationBatchSize(): Int {
        return if (isEInkDevice) {
            DEFAULT_BATCH_SIZE
        } else {
            1
        }
    }

    /**
     * Check if device is E-Ink
     *
     * @return true if device has E-Ink display
     */
    fun isEInkDevice(): Boolean = isEInkDevice
}

/**
 * Conflict resolution action
 */
enum class ConflictResolutionAction {
    LOCAL_WINS,
    REMOTE_WINS,
    NEWEST_WINS,
    KEEP_BOTH
}
