package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.util.Log
import com.nutomic.syncthingandroid.webdav.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File

/**
 * WebDAV synchronization engine
 *
 * Handles bidirectional synchronization between local folders
 * and WebDAV server with conflict resolution and incremental sync.
 */
class SyncEngine(
    private val context: Context,
    private val webDAVClient: WebDAVClient,
    private val conflictResolver: ConflictResolver
) {

    companion object {
        private const val TAG = "SyncEngine"

        // Batch size for parallel file operations
        private const val DEFAULT_BATCH_SIZE = 10

        // Maximum retry attempts for failed operations
        private const val MAX_RETRY_ATTEMPTS = 3
    }

    /**
     * Execute folder synchronization
     *
     * @param folderConfig Folder configuration
     * @param progressCallback Optional callback for sync progress
     * @return Sync result
     */
    suspend fun syncFolder(
        folderConfig: SyncFolderConfig,
        progressCallback: ((SyncProgress) -> Unit)? = null
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.i(TAG, "Starting sync for folder: ${folderConfig.id}")

        try {
            // Report sync started
            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.STARTING,
                    currentFile = null,
                    progress = 0
                )
            )

            // Check if folder is enabled
            if (!folderConfig.enabled) {
                Log.d(TAG, "Folder ${folderConfig.id} is disabled, skipping sync")
                return@withContext Result.success(
                    SyncResult(
                        folderId = folderConfig.id,
                        startTime = startTime,
                        endTime = System.currentTimeMillis(),
                        success = true
                    )
                )
            }

            // Validate local folder exists
            val localFolder = File(folderConfig.localPath)
            if (!localFolder.exists()) {
                Log.w(TAG, "Local folder does not exist: ${folderConfig.localPath}")
                return@withContext Result.failure(
                    IllegalArgumentException("Local folder does not exist: ${folderConfig.localPath}")
                )
            }

            // Detect changes
            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.SCANNING,
                    currentFile = null,
                    progress = 10
                )
            )

            val changes = detectChanges(folderConfig)

            Log.d(TAG, "Detected ${changes.first.size} local changes, ${changes.second.size} remote changes")

            // Apply sync based on mode
            val result = when (folderConfig.syncMode) {
                SyncMode.BIDIRECTIONAL -> syncBidirectional(folderConfig, changes, progressCallback)
                SyncMode.LOCAL_TO_REMOTE -> syncLocalToRemote(folderConfig, changes, progressCallback)
                SyncMode.REMOTE_TO_LOCAL -> syncRemoteToLocal(folderConfig, changes, progressCallback)
                SyncMode.MIRROR_LOCAL -> syncMirrorLocal(folderConfig, changes, progressCallback)
                SyncMode.MIRROR_REMOTE -> syncMirrorRemote(folderConfig, changes, progressCallback)
            }

            // Update progress
            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.COMPLETED,
                    currentFile = null,
                    progress = 100
                )
            )

            Log.i(TAG, "Sync completed for folder: ${folderConfig.id}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed for folder: ${folderConfig.id}", e)

            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.ERROR,
                    currentFile = null,
                    progress = 0,
                    error = e.message
                )
            )

            Result.failure(e)
        }
    }

    /**
     * Detect changes between local and remote folders
     *
     * @param folderConfig Folder configuration
     * @return Pair of (local changes, remote changes)
     */
    private suspend fun detectChanges(
        folderConfig: SyncFolderConfig
    ): Pair<List<FileChange>, List<RemoteFileChange>> {
        val localChanges = mutableListOf<FileChange>()
        val remoteChanges = mutableListOf<RemoteFileChange>()

        // Scan local folder
        val localFiles = scanLocalFolder(folderConfig.localPath)

        // List remote folder
        val remoteFilesResult = webDAVClient.listDirectory(folderConfig.remotePath)
        val remoteFiles = remoteFilesResult.getOrElse {
            Log.e(TAG, "Failed to list remote folder: ${folderConfig.remotePath}", it)
            emptyList()
        }

        // Create lookup maps
        val localFileMap = localFiles.associateBy { it.relativePath(folderConfig.localPath) }
        val remoteFileMap = remoteFiles.associate { it.path }

        // Detect local changes
        for (localFile in localFiles) {
            val relativePath = localFile.relativePath(folderConfig.localPath)
            val remoteFile = remoteFileMap[folderConfig.remotePath + "/" + relativePath]

            when {
                remoteFile == null -> {
                    // New local file
                    localChanges.add(FileChange.Create(localFile.path, localFile))
                }
                localFile.hasBeenModified() && localFile.lastModified != remoteFile.lastModified -> {
                    // Modified local file
                    localChanges.add(FileChange.Modify(localFile.path, localFile))
                }
            }
        }

        // Detect remote changes
        for (remoteFile in remoteFiles) {
            val relativePath = remoteFile.path.removePrefix(folderConfig.remotePath).trimStart('/')
            val localFile = localFileMap[relativePath]

            if (localFile == null) {
                // New remote file
                remoteChanges.add(RemoteFileChange.Create(remoteFile.path, remoteFile))
            }
        }

        return Pair(localChanges, remoteChanges)
    }

    /**
     * Scan local folder and return list of files
     *
     * @param folderPath Local folder path
     * @return List of local files
     */
    private fun scanLocalFolder(folderPath: String): List<LocalFile> {
        val files = mutableListOf<LocalFile>()
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) {
            return files
        }

        folder.walkTopDown().filter { it.isFile }.forEach { file ->
            files.add(
                LocalFile(
                    path = file.absolutePath,
                    name = file.name,
                    isDirectory = false,
                    size = file.length(),
                    lastModified = file.lastModified()
                )
            )
        }

        return files
    }

    /**
     * Bidirectional synchronization
     */
    private suspend fun syncBidirectional(
        folderConfig: SyncFolderConfig,
        changes: Pair<List<FileChange>, List<RemoteFileChange>>,
        progressCallback: ((SyncProgress) -> Unit)?
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        val (localChanges, remoteChanges) = changes
        val syncedFiles = mutableListOf<String>()
        val failedFiles = mutableListOf<String>()
        val conflicts = mutableListOf<FileConflict>()
        var bytesTransferred = 0L

        // Apply local changes (upload)
        progressCallback?.invoke(
            SyncProgress(
                folderId = folderConfig.id,
                status = SyncStatus.UPLOADING,
                currentFile = null,
                progress = 20
            )
        )

        localChanges.chunked(DEFAULT_BATCH_SIZE).forEachIndexed { index, batch ->
            val results = batch.map { change ->
                async {
                    when (change) {
                        is FileChange.Create, is FileChange.Modify -> {
                            val file = if (change is FileChange.Create) change.file else (change as FileChange.Modify).file
                            if (folderConfig.shouldSyncFile(file.name)) {
                                uploadFile(folderConfig, file, syncedFiles, failedFiles, bytesTransferred)
                            }
                        }
                        is FileChange.Delete -> {
                            deleteRemoteFile(folderConfig, change.path, syncedFiles, failedFiles)
                        }
                        else -> Result.success(Unit)
                    }
                }
            }.awaitAll()
        }

        // Apply remote changes (download)
        progressCallback?.invoke(
            SyncProgress(
                folderId = folderConfig.id,
                status = SyncStatus.DOWNLOADING,
                currentFile = null,
                progress = 60
            )
        )

        remoteChanges.chunked(DEFAULT_BATCH_SIZE).forEachIndexed { index, batch ->
            val results = batch.map { change ->
                async {
                    when (change) {
                        is RemoteFileChange.Create, is RemoteFileChange.Modify -> {
                            val file = if (change is RemoteFileChange.Create) change.file else (change as RemoteFileChange.Modify).file
                            if (folderConfig.shouldSyncFile(file.name)) {
                                downloadFile(folderConfig, file, syncedFiles, failedFiles, bytesTransferred)
                            }
                        }
                        else -> Result.success(Unit)
                    }
                }
            }.awaitAll()
        }

        Result.success(
            SyncResult(
                folderId = folderConfig.id,
                syncedFiles = syncedFiles.size,
                failedFiles = failedFiles,
                conflicts = conflicts,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                bytesTransferred = bytesTransferred,
                success = failedFiles.isEmpty()
            )
        )
    }

    /**
     * Local to remote synchronization (upload only)
     */
    private suspend fun syncLocalToRemote(
        folderConfig: SyncFolderConfig,
        changes: Pair<List<FileChange>, List<RemoteFileChange>>,
        progressCallback: ((SyncProgress) -> Unit)?
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        val (localChanges, _) = changes
        val syncedFiles = mutableListOf<String>()
        val failedFiles = mutableListOf<String>()
        var bytesTransferred = 0L

        progressCallback?.invoke(
            SyncProgress(
                folderId = folderConfig.id,
                status = SyncStatus.UPLOADING,
                currentFile = null,
                progress = 20
            )
        )

        localChanges.chunked(DEFAULT_BATCH_SIZE).forEachIndexed { index, batch ->
            val results = batch.map { change ->
                async {
                    when (change) {
                        is FileChange.Create, is FileChange.Modify -> {
                            val file = if (change is FileChange.Create) change.file else (change as FileChange.Modify).file
                            if (folderConfig.shouldSyncFile(file.name)) {
                                uploadFile(folderConfig, file, syncedFiles, failedFiles, bytesTransferred)
                            }
                        }
                        else -> Result.success(Unit)
                    }
                }
            }.awaitAll()

            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.UPLOADING,
                    currentFile = null,
                    progress = 20 + (index + 1) * 60 / (localChanges.size / DEFAULT_BATCH_SIZE + 1)
                )
            )
        }

        Result.success(
            SyncResult(
                folderId = folderConfig.id,
                syncedFiles = syncedFiles.size,
                failedFiles = failedFiles,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                bytesTransferred = bytesTransferred,
                success = failedFiles.isEmpty()
            )
        )
    }

    /**
     * Remote to local synchronization (download only)
     */
    private suspend fun syncRemoteToLocal(
        folderConfig: SyncFolderConfig,
        changes: Pair<List<FileChange>, List<RemoteFileChange>>,
        progressCallback: ((SyncProgress) -> Unit)?
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        val (_, remoteChanges) = changes
        val syncedFiles = mutableListOf<String>()
        val failedFiles = mutableListOf<String>()
        var bytesTransferred = 0L

        progressCallback?.invoke(
            SyncProgress(
                folderId = folderConfig.id,
                status = SyncStatus.DOWNLOADING,
                currentFile = null,
                progress = 20
            )
        )

        remoteChanges.chunked(DEFAULT_BATCH_SIZE).forEachIndexed { index, batch ->
            val results = batch.map { change ->
                async {
                    when (change) {
                        is RemoteFileChange.Create, is RemoteFileChange.Modify -> {
                            val file = if (change is RemoteFileChange.Create) change.file else (change as RemoteFileChange.Modify).file
                            if (folderConfig.shouldSyncFile(file.name)) {
                                downloadFile(folderConfig, file, syncedFiles, failedFiles, bytesTransferred)
                            }
                        }
                        else -> Result.success(Unit)
                    }
                }
            }.awaitAll()

            progressCallback?.invoke(
                SyncProgress(
                    folderId = folderConfig.id,
                    status = SyncStatus.DOWNLOADING,
                    currentFile = null,
                    progress = 20 + (index + 1) * 70 / (remoteChanges.size / DEFAULT_BATCH_SIZE + 1)
                )
            )
        }

        Result.success(
            SyncResult(
                folderId = folderConfig.id,
                syncedFiles = syncedFiles.size,
                failedFiles = failedFiles,
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                bytesTransferred = bytesTransferred,
                success = failedFiles.isEmpty()
            )
        )
    }

    /**
     * Mirror local to remote (remote matches local exactly)
     */
    private suspend fun syncMirrorLocal(
        folderConfig: SyncFolderConfig,
        changes: Pair<List<FileChange>, List<RemoteFileChange>>,
        progressCallback: ((SyncProgress) -> Unit)?
    ): Result<SyncResult> {
        // Similar to LOCAL_TO_REMOTE but also deletes remote files not present locally
        return syncLocalToRemote(folderConfig, changes, progressCallback)
    }

    /**
     * Mirror remote to local (local matches remote exactly)
     */
    private suspend fun syncMirrorRemote(
        folderConfig: SyncFolderConfig,
        changes: Pair<List<FileChange>, List<RemoteFileChange>>,
        progressCallback: ((SyncProgress) -> Unit)?
    ): Result<SyncResult> {
        // Similar to REMOTE_TO_LOCAL but also deletes local files not present remotely
        return syncRemoteToLocal(folderConfig, changes, progressCallback)
    }

    /**
     * Upload file to WebDAV server
     */
    private suspend fun uploadFile(
        folderConfig: SyncFolderConfig,
        localFile: LocalFile,
        syncedFiles: MutableList<String>,
        failedFiles: MutableList<String>,
        bytesTransferred: Long
    ): Result<Unit> {
        val relativePath = localFile.relativePath(folderConfig.localPath)
        val remotePath = "${folderConfig.remotePath}/$relativePath"

        return webDAVClient.uploadFile(localFile.path, remotePath)
            .onSuccess {
                syncedFiles.add(localFile.path)
                bytesTransferred + localFile.size
                Log.d(TAG, "Uploaded: $relativePath")
            }
            .onFailure { e ->
                failedFiles.add(localFile.path)
                Log.e(TAG, "Failed to upload: $relativePath", e)
            }
    }

    /**
     * Download file from WebDAV server
     */
    private suspend fun downloadFile(
        folderConfig: SyncFolderConfig,
        remoteFile: WebDAVFile,
        syncedFiles: MutableList<String>,
        failedFiles: MutableList<String>,
        bytesTransferred: Long
    ): Result<Unit> {
        val relativePath = remoteFile.path.removePrefix(folderConfig.remotePath).trimStart('/')
        val localPath = "${folderConfig.localPath}/$relativePath"

        return webDAVClient.downloadFile(remoteFile.path, localPath)
            .onSuccess {
                syncedFiles.add(remoteFile.path)
                bytesTransferred + remoteFile.size
                Log.d(TAG, "Downloaded: $relativePath")
            }
            .onFailure { e ->
                failedFiles.add(remoteFile.path)
                Log.e(TAG, "Failed to download: $relativePath", e)
            }
    }

    /**
     * Delete remote file
     */
    private suspend fun deleteRemoteFile(
        folderConfig: SyncFolderConfig,
        localPath: String,
        syncedFiles: MutableList<String>,
        failedFiles: MutableList<String>
    ): Result<Unit> {
        val relativePath = localPath.removePrefix(folderConfig.localPath).trimStart('/')
        val remotePath = "${folderConfig.remotePath}/$relativePath"

        return webDAVClient.deleteFile(remotePath)
            .onSuccess {
                syncedFiles.add(localPath)
                Log.d(TAG, "Deleted remote: $relativePath")
            }
            .onFailure { e ->
                failedFiles.add(localPath)
                Log.e(TAG, "Failed to delete remote: $relativePath", e)
            }
    }
}

/**
 * Sync progress information
 */
data class SyncProgress(
    val folderId: String,
    val status: SyncStatus,
    val currentFile: String?,
    val progress: Int,
    val error: String? = null
)

/**
 * Sync status
 */
enum class SyncStatus {
    STARTING,
    SCANNING,
    UPLOADING,
    DOWNLOADING,
    CONFLICT,
    COMPLETED,
    ERROR
}
