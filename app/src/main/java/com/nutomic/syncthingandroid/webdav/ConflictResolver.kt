package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.util.Log
import com.nutomic.syncthingandroid.webdav.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Conflict resolution handler
 *
 * Detects and resolves file conflicts between local and remote files
 * using various strategies.
 */
class ConflictResolver(private val context: Context) {

    companion object {
        private const val TAG = "ConflictResolver"

        // Conflict file name format
        private const val CONFLICT_FILE_FORMAT = "conflict_%s_%s"

        // Date format for conflict files
        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    }

    /**
     * Detect if there's a conflict between local and remote files
     *
     * @param localFile Local file
     * @param remoteFile Remote file
     * @return Conflict type or null if no conflict
     */
    suspend fun detectConflict(
        localFile: LocalFile,
        remoteFile: WebDAVFile
    ): ConflictType? = withContext(Dispatchers.IO) {
        try {
            // Case 1: Both files modified since last sync
            if (localFile.hasBeenModified() &&
                remoteFile.lastModified > (localFile.lastSyncTime)) {

                // Check if content is actually different
                if (localFile.lastModified != remoteFile.lastModified) {
                    Log.d(TAG, "Conflict detected: Both modified - ${localFile.name}")
                    return@withContext ConflictType.BOTH_MODIFIED
                }
            }

            // Case 2: Both deleted
            if (!localFile.exists()) {
                // Local file doesn't exist, check remote
                // This would be handled by the sync engine
                return@withContext null
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting conflict", e)
            null
        }
    }

    /**
     * Resolve file conflict using specified strategy
     *
     * @param conflict File conflict information
     * @param strategy Conflict resolution strategy
     * @return Conflict resolution result
     */
    suspend fun resolveConflict(
        conflict: FileConflict,
        strategy: ConflictStrategy
    ): Result<ConflictResolution> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Resolving conflict using strategy: $strategy")

        try {
            val result = when (strategy) {
                ConflictStrategy.LOCAL_WINS -> resolveLocalWins(conflict)
                ConflictStrategy.REMOTE_WINS -> resolveRemoteWins(conflict)
                ConflictStrategy.NEWEST_WINS -> resolveNewestWins(conflict)
                ConflictStrategy.KEEP_BOTH -> resolveKeepBoth(conflict)
                ConflictStrategy.MANUAL_RESOLVE -> Result.success(ConflictResolution.ManualResolve(conflict.fileName()))
            }

            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve conflict: ${conflict.fileName()}", e)
            Result.failure(e)
        }
    }

    /**
     * Local wins strategy - keep local version
     */
    private suspend fun resolveLocalWins(conflict: FileConflict): Result<ConflictResolution> {
        Log.d(TAG, "Resolving conflict: Local wins - ${conflict.fileName()}")

        return Result.success(
            ConflictResolution.LocalWins(conflict.localFile.path)
        )
    }

    /**
     * Remote wins strategy - keep remote version
     */
    private suspend fun resolveRemoteWins(conflict: FileConflict): Result<ConflictResolution> {
        Log.d(TAG, "Resolving conflict: Remote wins - ${conflict.fileName()}")

        return Result.success(
            ConflictResolution.RemoteWins(conflict.remoteFile.path)
        )
    }

    /**
     * Newest wins strategy - keep the version with the newest modification time
     */
    private suspend fun resolveNewestWins(conflict: FileConflict): Result<ConflictResolution> {
        val localIsNewer = conflict.localFile.lastModified > conflict.remoteFile.lastModified

        Log.d(TAG, "Resolving conflict: Newest wins - ${conflict.fileName()}, " +
                "local is ${if (localIsNewer) "newer" else "older"}")

        return Result.success(
            ConflictResolution.NewestWins(
                path = if (localIsNewer) conflict.localFile.path else conflict.remoteFile.path,
                isLocal = localIsNewer
            )
        )
    }

    /**
     * Keep both strategy - create conflict copies of both versions
     */
    private suspend fun resolveKeepBoth(conflict: FileConflict): Result<ConflictResolution> {
        Log.d(TAG, "Resolving conflict: Keep both - ${conflict.fileName()}")

        val timestamp = DATE_FORMAT.format(Date())

        // Create local conflict copy
        val localConflictPath = generateConflictCopyPath(
            conflict.localFile.path,
            timestamp,
            "local"
        )

        // Create remote conflict copy path
        val remoteConflictPath = generateConflictCopyPath(
            conflict.remoteFile.path,
            timestamp,
            "remote"
        )

        return Result.success(
            ConflictResolution.KeepBoth(
                localPath = conflict.localFile.path,
                remotePath = conflict.remoteFile.path,
                localCopyPath = localConflictPath,
                remoteCopyPath = remoteConflictPath
            )
        )
    }

    /**
     * Generate conflict copy file path
     *
     * @param originalPath Original file path
     * @param timestamp Conflict timestamp
     * @param side Conflict side (local or remote)
     * @return Conflict copy path
     */
    private fun generateConflictCopyPath(
        originalPath: String,
        timestamp: String,
        side: String
    ): String {
        val file = File(originalPath)
        val parent = file.parent ?: ""
        val name = file.nameWithoutExtension
        val extension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""

        val conflictName = String.format(
            CONFLICT_FILE_FORMAT,
            "${name}_$side",
            timestamp
        )

        return "$parent/${conflictName}$extension"
    }

    /**
     * Create conflict copy file
     *
     * @param sourcePath Source file path
     * @param targetPath Target conflict copy path
     * @return Result indicating success or failure
     */
    suspend fun createConflictCopy(
        sourcePath: String,
        targetPath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourcePath)
            val targetFile = File(targetPath)

            // Check if source exists
            if (!sourceFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Source file does not exist: $sourcePath")
                )
            }

            // Create parent directories
            targetFile.parentFile?.mkdirs()

            // Copy file
            sourceFile.copyTo(targetFile, overwrite = true)

            Log.d(TAG, "Created conflict copy: $targetPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create conflict copy: $targetPath", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a file is a conflict copy
     *
     * @param fileName File name to check
     * @return true if file is a conflict copy
     */
    fun isConflictCopy(fileName: String): Boolean {
        return fileName.startsWith("conflict_") && fileName.contains("_local_") ||
               fileName.startsWith("conflict_") && fileName.contains("_remote_")
    }

    /**
     * Extract original file name from conflict copy
     *
     * @param conflictFileName Conflict file name
     * @return Original file name or null
     */
    fun extractOriginalFileName(conflictFileName: String): String? {
        if (!isConflictCopy(conflictFileName)) {
            return null
        }

        // Remove "conflict_" prefix
        val withoutPrefix = conflictFileName.removePrefix("conflict_")

        // Remove side and timestamp
        val parts = withoutPrefix.split("_local_", "_remote_")
        if (parts.isNotEmpty()) {
            val originalName = parts[0]
            // Extract extension
            val extension = conflictFileName.substringAfterLast('.', "")
            return if (extension.isNotEmpty()) {
                "$originalName.$extension"
            } else {
                originalName
            }
        }

        return null
    }

    /**
     * Compare file contents by ETag
     *
     * @param localFile Local file
     * @param remoteFile Remote file
     * @return true if files have the same content
     */
    suspend fun areFilesEqual(
        localFile: LocalFile,
        remoteFile: WebDAVFile
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Compare file sizes
            if (localFile.size != remoteFile.size) {
                return@withContext false
            }

            // Compare modification times
            if (localFile.lastModified == remoteFile.lastModified) {
                return@withContext true
            }

            // If ETag is available, use it
            if (remoteFile.etag != null) {
                // ETag comparison would be more reliable
                // For now, we'll use size and modification time
                return@withContext false
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing files", e)
            false
        }
    }

    /**
     * Get conflict description for user display
     *
     * @param conflict File conflict
     * @return Human-readable conflict description
     */
    fun getConflictDescription(conflict: FileConflict): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return buildString {
            append("File: ${conflict.fileName()}\n")
            append("Local: ${dateFormat.format(Date(conflict.localFile.lastModified))}, ")
            append("${formatFileSize(conflict.localFile.size)}\n")
            append("Remote: ${dateFormat.format(Date(conflict.remoteFile.lastModified))}, ")
            append("${formatFileSize(conflict.remoteFile.size)}\n")
            append("Type: ${conflict.conflictType}")
        }
    }

    /**
     * Format file size for human display
     *
     * @param size File size in bytes
     * @return Formatted file size
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
}
