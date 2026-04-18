package com.nutomic.syncthingandroid.webdav.model

import java.util.Date

/**
 * WebDAV file information
 */
data class WebDAVFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val etag: String?,
    val contentType: String? = null
) {
    companion object {
        const val PATH_SEPARATOR = "/"
    }

    /**
     * Get the parent directory path
     */
    fun parentPath(): String {
        val lastSlash = path.lastIndexOf(PATH_SEPARATOR)
        return if (lastSlash > 0) {
            path.substring(0, lastSlash)
        } else {
            PATH_SEPARATOR
        }
    }

    /**
     * Get the file extension
     */
    fun extension(): String {
        if (isDirectory) return ""
        val lastDot = name.lastIndexOf('.')
        return if (lastDot > 0) {
            name.substring(lastDot + 1)
        } else {
            ""
        }
    }
}

/**
 * WebDAV file metadata
 */
data class WebDAVFileInfo(
    val path: String,
    val etag: String,
    val lastModified: Long,
    val contentType: String?,
    val size: Long
)

/**
 * Local file information
 */
data class LocalFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val lastSyncTime: Long = 0
) {
    /**
     * Get relative path from the sync folder root
     */
    fun relativePath(folderRoot: String): String {
        return if (path.startsWith(folderRoot)) {
            path.substring(folderRoot.trimEnd('/').length + 1)
        } else {
            path
        }
    }

    /**
     * Check if file has been modified since last sync
     */
    fun hasBeenModified(): Boolean {
        return lastModified > lastSyncTime
    }
}

/**
 * File change event
 */
sealed class FileChange {
    abstract val path: String

    data class Create(override val path: String, val file: LocalFile) : FileChange()
    data class Modify(override val path: String, val file: LocalFile) : FileChange()
    data class Delete(override val path: String) : FileChange()
    data class Move(override val path: String, val oldPath: String) : FileChange()
}

/**
 * Remote file change event
 */
sealed class RemoteFileChange {
    abstract val path: String

    data class Create(override val path: String, val file: WebDAVFile) : RemoteFileChange()
    data class Modify(override val path: String, val file: WebDAVFile) : RemoteFileChange()
    data class Delete(override val path: String) : RemoteFileChange()
    data class Move(override val path: String, val oldPath: String) : RemoteFileChange()
}

/**
 * Sync folder configuration
 */
data class SyncFolderConfig(
    val id: String,
    val localPath: String,
    val remotePath: String,
    val syncMode: SyncMode = SyncMode.BIDIRECTIONAL,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.NEWEST_WINS,
    val enabled: Boolean = true,
    val fileFilters: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList()
) {
    /**
     * Get local folder name from path
     */
    fun localFolderName(): String {
        return localPath.trimEnd('/').split('/').lastOrNull() ?: "Unknown"
    }

    /**
     * Get remote folder name from path
     */
    fun remoteFolderName(): String {
        return remotePath.trimEnd('/').split('/').lastOrNull() ?: "Unknown"
    }

    /**
     * Check if file should be synced based on filters
     */
    fun shouldSyncFile(fileName: String): Boolean {
        // Check exclude patterns first
        for (pattern in excludePatterns) {
            if (fileName.matches(pattern.toRegex())) {
                return false
            }
        }

        // If no filters specified, sync all files
        if (fileFilters.isEmpty()) return true

        // Check if file matches any include filter
        for (filter in fileFilters) {
            if (fileName.endsWith(filter)) {
                return true
            }
        }

        return false
    }
}

/**
 * Sync mode
 */
enum class SyncMode {
    /**
     * Bidirectional sync - changes propagate both ways
     */
    BIDIRECTIONAL,

    /**
     * Local to remote only - upload changes
     */
    LOCAL_TO_REMOTE,

    /**
     * Remote to local only - download changes
     */
    REMOTE_TO_LOCAL,

    /**
     * Mirror local to remote - remote matches local exactly
     */
    MIRROR_LOCAL,

    /**
     * Mirror remote to local - local matches remote exactly
     */
    MIRROR_REMOTE
}

/**
 * Conflict resolution strategy
 */
enum class ConflictStrategy {
    /**
     * Always keep local version
     */
    LOCAL_WINS,

    /**
     * Always keep remote version
     */
    REMOTE_WINS,

    /**
     * Keep the version with the newest modification time
     */
    NEWEST_WINS,

    /**
     * Keep both versions by creating a conflict copy
     */
    KEEP_BOTH,

    /**
     * Require manual conflict resolution
     */
    MANUAL_RESOLVE
}

/**
 * File conflict information
 */
data class FileConflict(
    val folderId: String,
    val relativePath: String,
    val localFile: LocalFile,
    val remoteFile: WebDAVFile,
    val conflictType: ConflictType
) {
    /**
     * Get the file name from the conflict path
     */
    fun fileName(): String {
        return relativePath.split('/').lastOrNull() ?: "Unknown"
    }
}

/**
 * Conflict type
 */
enum class ConflictType {
    /**
     * File was modified on both sides
     */
    BOTH_MODIFIED,

    /**
     * File was deleted on both sides
     */
    BOTH_DELETED,

    /**
     * File was deleted locally but modified remotely
     */
    LOCAL_DELETED_REMOTE_MODIFIED,

    /**
     * File was modified locally but deleted remotely
     */
    LOCAL_MODIFIED_REMOTE_DELETED
}

/**
 * Conflict resolution result
 */
sealed class ConflictResolution {
    data class LocalWins(val path: String) : ConflictResolution()
    data class RemoteWins(val path: String) : ConflictResolution()
    data class NewestWins(val path: String, val isLocal: Boolean) : ConflictResolution()
    data class KeepBoth(
        val localPath: String,
        val remotePath: String,
        val localCopyPath: String,
        val remoteCopyPath: String
    ) : ConflictResolution()

    data class ManualResolve(val conflictId: String) : ConflictResolution()
}

/**
 * Sync operation
 */
sealed class SyncItem {
    abstract val folderId: String

    data class Upload(
        override val folderId: String,
        val localFile: LocalFile,
        val remotePath: String
    ) : SyncItem()

    data class Download(
        override val folderId: String,
        val remoteFile: WebDAVFile,
        val localPath: String
    ) : SyncItem()

    data class Conflict(
        override val folderId: String,
        val localFile: LocalFile,
        val remoteFile: WebDAVFile
    ) : SyncItem()

    data class Delete(
        override val folderId: String,
        val path: String,
        val isLocal: Boolean
    ) : SyncItem()
}

/**
 * Sync result
 */
data class SyncResult(
    val folderId: String,
    val syncedFiles: Int = 0,
    val failedFiles: List<String> = emptyList(),
    val conflicts: List<FileConflict> = emptyList(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val bytesTransferred: Long = 0,
    val success: Boolean = true
) {
    /**
     * Get sync duration in milliseconds
     */
    fun duration(): Long {
        return endTime - startTime
    }

    /**
     * Get human-readable duration
     */
    fun formattedDuration(): String {
        val seconds = duration() / 1000
        return if (seconds < 60) {
            "${seconds}s"
        } else {
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            "${minutes}m ${remainingSeconds}s"
        }
    }

    /**
     * Get formatted bytes transferred
     */
    fun formattedBytesTransferred(): String {
        return when {
            bytesTransferred < 1024 -> "${bytesTransferred}B"
            bytesTransferred < 1024 * 1024 -> "${bytesTransferred / 1024}KB"
            bytesTransferred < 1024 * 1024 * 1024 -> "${bytesTransferred / (1024 * 1024)}MB"
            else -> "${bytesTransferred / (1024 * 1024 * 1024)}GB"
        }
    }
}

/**
 * Sync operation type for offline queue
 */
enum class SyncOperationType {
    UPLOAD,
    DELETE,
    MOVE
}

/**
 * Sync operation status
 */
enum class SyncOperationStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
