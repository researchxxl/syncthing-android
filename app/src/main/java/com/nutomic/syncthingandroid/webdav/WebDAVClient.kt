package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.util.Log
import com.github.sardine.Sardine
import com.github.sardine.SardineFactory
import com.nutomic.syncthingandroid.webdav.model.WebDAVFile
import com.nutomic.syncthingandroid.webdav.model.WebDAVFileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WebDAV client wrapper around Sardine-Android
 *
 * Provides high-level WebDAV operations with coroutine support
 * and proper error handling.
 */
class WebDAVClient(private val context: Context) {

    companion object {
        private const val TAG = "WebDAVClient"

        // Timeout settings
        private const val CONNECT_TIMEOUT_MS = 30000 // 30 seconds
        private const val READ_TIMEOUT_MS = 60000 // 60 seconds for large files

        // Buffer size for file transfers
        private const val BUFFER_SIZE = 8192 // 8KB
    }

    /**
     * WebDAV connection configuration
     */
    data class ConnectionConfig(
        val serverUrl: String,
        val username: String,
        val password: String,
        val authType: AuthType = AuthType.BASIC,
        val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        val readTimeoutMs: Int = READ_TIMEOUT_MS
    ) {
        /**
         * Validate and normalize server URL
         */
        fun getNormalizedUrl(): String {
            var url = serverUrl.trim()

            // Ensure URL has a scheme
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://$url"
            }

            // Remove trailing slash
            url = url.trimEnd('/')

            return url
        }

        /**
         * Validate configuration
         */
        fun isValid(): Boolean {
            return getNormalizedUrl().isNotEmpty() &&
                   username.isNotEmpty() &&
                   password.isNotEmpty()
        }
    }

    /**
     * Authentication type
     */
    enum class AuthType {
        BASIC,
        DIGEST,
        NONE
    }

    private var sardine: Sardine? = null
    private var currentConfig: ConnectionConfig? = null

    /**
     * Initialize WebDAV connection
     *
     * @param config Connection configuration
     * @return Result indicating success or failure
     */
    suspend fun connect(config: ConnectionConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Connecting to WebDAV server: ${config.getNormalizedUrl()}")

            if (!config.isValid()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid WebDAV configuration")
                )
            }

            // Create Sardine instance with authentication
            sardine = when (config.authType) {
                AuthType.BASIC -> SardineFactory.begin(
                    config.username,
                    config.password
                )
                AuthType.DIGEST -> SardineFactory.begin(
                    config.username,
                    config.password.toCharArray()
                )
                AuthType.NONE -> SardineFactory.begin()
            }

            // Enable compression
            sardine?.enableCompression()

            // Test connection by listing root directory
            val testUrl = "${config.getNormalizedUrl()}/"
            val resources = sardine?.list(testUrl)

            if (resources != null) {
                currentConfig = config
                Log.i(TAG, "Successfully connected to WebDAV server")
                Result.success(Unit)
            } else {
                Result.failure(
                    IOException("Failed to connect to WebDAV server: no response")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to WebDAV server", e)
            Result.failure(e)
        }
    }

    /**
     * Upload file to WebDAV server
     *
     * @param localPath Local file path
     * @param remotePath Remote path (relative to server root)
     * @param progressCallback Optional callback for upload progress (0-100)
     * @return Result indicating success or failure
     */
    suspend fun uploadFile(
        localPath: String,
        remotePath: String,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Uploading file: $localPath -> $remotePath")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val localFile = File(localPath)
            if (!localFile.exists()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Local file does not exist: $localPath")
                )
            }

            if (!localFile.isFile) {
                return@withContext Result.failure(
                    IllegalArgumentException("Path is not a file: $localPath")
                )
            }

            // Create parent directories if needed
            val remoteDir = remotePath.substringBeforeLast('/', "")
            if (remoteDir.isNotEmpty()) {
                createDirectoryIfNeeded(remoteDir)
            }

            val remoteUrl = "${config.getNormalizedUrl()}/$remotePath"

            // Upload file with progress tracking
            FileInputStream(localFile).use { inputStream ->
                suspendCancellableCoroutine { continuation ->
                    try {
                        sardine?.put(
                            remoteUrl,
                            inputStream,
                            localFile.length()
                        ) { bytesWritten ->
                            // Calculate progress percentage
                            if (localFile.length() > 0) {
                                val progress = (bytesWritten * 100 / localFile.length()).toInt()
                                progressCallback?.invoke(progress)
                            }
                        }

                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            Log.i(TAG, "File uploaded successfully: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload file: $localPath", e)
            Result.failure(e)
        }
    }

    /**
     * Download file from WebDAV server
     *
     * @param remotePath Remote path (relative to server root)
     * @param localPath Local file path
     * @param progressCallback Optional callback for download progress (0-100)
     * @return Result indicating success or failure
     */
    suspend fun downloadFile(
        remotePath: String,
        localPath: String,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Downloading file: $remotePath -> $localPath")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val localFile = File(localPath)

            // Create parent directories if needed
            localFile.parentFile?.mkdirs()

            val remoteUrl = "${config.getNormalizedUrl()}/$remotePath"

            // Get file info for size
            val fileInfo = getFileInfo(remotePath)
                .getOrElse { e ->
                    return@withContext Result.failure(
                        IOException("Failed to get remote file info: ${e.message}")
                    )
                }

            // Download file
            sardine?.get(remoteUrl)?.use { inputStream ->
                FileOutputStream(localFile).use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        // Calculate progress percentage
                        if (fileInfo.size > 0) {
                            val progress = (totalBytesRead * 100 / fileInfo.size).toInt()
                            progressCallback?.invoke(progress)
                        }
                    }
                }
            }

            Log.i(TAG, "File downloaded successfully: $remotePath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download file: $remotePath", e)
            Result.failure(e)
        }
    }

    /**
     * List directory contents
     *
     * @param path Directory path (relative to server root)
     * @return Result containing list of files or error
     */
    suspend fun listDirectory(path: String): Result<List<WebDAVFile>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Listing directory: $path")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val url = "${config.getNormalizedUrl()}/$path"
            val resources = sardine?.list(url)

            if (resources == null) {
                return@withContext Result.failure(
                    IOException("Failed to list directory: no response")
                )
            }

            // Convert Sardine resources to WebDAVFile objects
            val files = resources
                .filter { it.path != path } // Exclude parent directory entry
                .map { resource ->
                    val fileName = resource.path?.trimEnd('/')?.split('/')?.lastOrNull() ?: "Unknown"
                    WebDAVFile(
                        name = fileName,
                        path = resource.path?.trimEnd('/') ?: "",
                        isDirectory = resource.isDirectory ?: false,
                        size = resource.contentLength ?: 0,
                        lastModified = resource.modified?.time ?: 0,
                        etag = resource.etag,
                        contentType = resource.contentType
                    )
                }

            Log.d(TAG, "Listed ${files.size} items in directory: $path")
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list directory: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Delete file or directory
     *
     * @param path Path to delete (relative to server root)
     * @return Result indicating success or failure
     */
    suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Deleting file: $path")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val url = "${config.getNormalizedUrl()}/$path"

            // Check if path exists
            val exists = sardine?.exists(url)
            if (exists != true) {
                return@withContext Result.failure(
                    IllegalArgumentException("Path does not exist: $path")
                )
            }

            // Delete file or directory
            sardine?.delete(url)

            Log.i(TAG, "File deleted successfully: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Move or rename file
     *
     * @param fromPath Source path (relative to server root)
     * @param toPath Destination path (relative to server root)
     * @return Result indicating success or failure
     */
    suspend fun moveFile(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Moving file: $fromPath -> $toPath")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val fromUrl = "${config.getNormalizedUrl()}/$fromPath"
            val toUrl = "${config.getNormalizedUrl()}/$toPath"

            // Move file
            sardine?.move(fromUrl, toUrl)

            Log.i(TAG, "File moved successfully: $fromPath -> $toPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to move file: $fromPath", e)
            Result.failure(e)
        }
    }

    /**
     * Copy file
     *
     * @param fromPath Source path (relative to server root)
     * @param toPath Destination path (relative to server root)
     * @return Result indicating success or failure
     */
    suspend fun copyFile(fromPath: String, toPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Copying file: $fromPath -> $toPath")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val fromUrl = "${config.getNormalizedUrl()}/$fromPath"
            val toUrl = "${config.getNormalizedUrl()}/$toPath"

            // Copy file
            sardine?.copy(fromUrl, toUrl)

            Log.i(TAG, "File copied successfully: $fromPath -> $toPath")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy file: $fromPath", e)
            Result.failure(e)
        }
    }

    /**
     * Create directory
     *
     * @param path Directory path to create (relative to server root)
     * @return Result indicating success or failure
     */
    suspend fun createDirectory(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating directory: $path")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val url = "${config.getNormalizedUrl()}/$path"

            // Check if directory already exists
            if (sardine?.exists(url) == true) {
                Log.d(TAG, "Directory already exists: $path")
                return@withContext Result.success(Unit)
            }

            // Create directory
            sardine?.createDirectory(url)

            Log.i(TAG, "Directory created successfully: $path")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create directory: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Get file metadata
     *
     * @param path File path (relative to server root)
     * @return Result containing file info or error
     */
    suspend fun getFileInfo(path: String): Result<WebDAVFileInfo> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Getting file info: $path")

            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val url = "${config.getNormalizedUrl()}/$path"

            // Get resource info
            val resources = sardine?.list(url)

            if (resources.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IOException("File not found: $path")
                )
            }

            // First resource should be the file itself
            val resource = resources.firstOrNull()
                ?: return@withContext Result.failure(
                    IOException("Failed to get file info")
                )

            val fileInfo = WebDAVFileInfo(
                path = path,
                etag = resource.etag ?: "",
                lastModified = resource.modified?.time ?: 0,
                contentType = resource.contentType,
                size = resource.contentLength ?: 0
            )

            Result.success(fileInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info: $path", e)
            Result.failure(e)
        }
    }

    /**
     * Check if connection is active
     *
     * @return true if connected and authenticated
     */
    fun isConnected(): Boolean {
        return sardine != null && currentConfig != null
    }

    /**
     * Disconnect from WebDAV server
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from WebDAV server")
        sardine = null
        currentConfig = null
    }

    /**
     * Test connection by attempting to list root directory
     *
     * @return Result indicating success or failure with error message
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val config = currentConfig
                ?: return@withContext Result.failure(
                    IllegalStateException("Not connected to WebDAV server")
                )

            val url = "${config.getNormalizedUrl()}/"
            val resources = sardine?.list(url)

            Result.success(resources != null)
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            Result.failure(e)
        }
    }

    /**
     * Create directory if it doesn't exist
     *
     * @param path Directory path to create
     */
    private suspend fun createDirectoryIfNeeded(path: String) {
        if (path.isEmpty()) return

        val config = currentConfig ?: return
        val url = "${config.getNormalizedUrl()}/$path"

        if (sardine?.exists(url) != true) {
            createDirectory(path)
        }
    }
}
