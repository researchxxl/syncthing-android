package com.nutomic.syncthingandroid.webdav

import android.content.Context
import com.nutomic.syncthingandroid.webdav.model.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SyncEngine
 */
class SyncEngineTest {

    private lateinit var mockContext: Context
    private lateinit var mockWebDAVClient: WebDAVClient
    private lateinit var mockConflictResolver: ConflictResolver
    private lateinit var syncEngine: SyncEngine

    @Before
    fun setup() {
        mockContext = mockk()
        mockWebDAVClient = mockk()
        mockConflictResolver = mockk()

        syncEngine = SyncEngine(
            context = mockContext,
            webDAVClient = mockWebDAVClient,
            conflictResolver = mockConflictResolver
        )
    }

    @Test
    fun testSyncFolder_disabledFolder() = runTest {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            enabled = false
        )

        val result = syncEngine.syncFolder(config)

        assertTrue(result.isSuccess)
        val syncResult = result.getOrNull()
        assertNotNull(syncResult)
        assertFalse(syncResult?.syncedFiles!! > 0)
    }

    @Test
    fun testSyncFolder_invalidLocalPath() = runTest {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/nonexistent/folder",
            remotePath = "/remote/folder",
            enabled = true
        )

        val result = syncEngine.syncFolder(config)

        assertTrue(result.isFailure)
    }

    @Test
    fun testSyncProgress_values() {
        val progress1 = SyncProgress(
            folderId = "test_folder",
            status = SyncStatus.STARTING,
            currentFile = null,
            progress = 0
        )

        assertEquals("test_folder", progress1.folderId)
        assertEquals(SyncStatus.STARTING, progress1.status)
        assertEquals(0, progress1.progress)
        assertNull(progress1.currentFile)
        assertNull(progress1.error)

        val progress2 = SyncProgress(
            folderId = "test_folder",
            status = SyncStatus.ERROR,
            currentFile = "test.txt",
            progress = 50,
            error = "Connection failed"
        )

        assertEquals("Connection failed", progress2.error)
        assertEquals("test.txt", progress2.currentFile)
    }

    @Test
    fun testSyncStatus_values() {
        assertEquals(7, SyncStatus.values().size)
        assertTrue(SyncStatus.values().contains(SyncStatus.STARTING))
        assertTrue(SyncStatus.values().contains(SyncStatus.SCANNING))
        assertTrue(SyncStatus.values().contains(SyncStatus.UPLOADING))
        assertTrue(SyncStatus.values().contains(SyncStatus.DOWNLOADING))
        assertTrue(SyncStatus.values().contains(SyncStatus.CONFLICT))
        assertTrue(SyncStatus.values().contains(SyncStatus.COMPLETED))
        assertTrue(SyncStatus.values().contains(SyncStatus.ERROR))
    }

    @Test
    fun testSyncMode_bidirectional() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            syncMode = SyncMode.BIDIRECTIONAL
        )

        assertEquals(SyncMode.BIDIRECTIONAL, config.syncMode)
    }

    @Test
    fun testSyncMode_localToRemote() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            syncMode = SyncMode.LOCAL_TO_REMOTE
        )

        assertEquals(SyncMode.LOCAL_TO_REMOTE, config.syncMode)
    }

    @Test
    fun testSyncMode_remoteToLocal() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            syncMode = SyncMode.REMOTE_TO_LOCAL
        )

        assertEquals(SyncMode.REMOTE_TO_LOCAL, config.syncMode)
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_noFilters() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            fileFilters = emptyList(),
            excludePatterns = emptyList()
        )

        assertTrue(config.shouldSyncFile("test.txt"))
        assertTrue(config.shouldSyncFile("document.pdf"))
        assertTrue(config.shouldSyncFile("image.jpg"))
        assertTrue(config.shouldSyncFile("any.file"))
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_withIncludeFilters() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            fileFilters = listOf(".txt", ".pdf"),
            excludePatterns = emptyList()
        )

        assertTrue(config.shouldSyncFile("test.txt"))
        assertTrue(config.shouldSyncFile("document.pdf"))
        assertFalse(config.shouldSyncFile("image.jpg"))
        assertFalse(config.shouldSyncFile("video.mp4"))
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_withExcludePatterns() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            fileFilters = emptyList(),
            excludePatterns = listOf(".*\\.tmp", ".*\\.bak", "~.*")
        )

        assertFalse(config.shouldSyncFile("test.tmp"))
        assertFalse(config.shouldSyncFile("document.bak"))
        assertFalse(config.shouldSyncFile("~temp.txt"))
        assertTrue(config.shouldSyncFile("test.txt"))
        assertTrue(config.shouldSyncFile("document.pdf"))
    }

    @Test
    fun testSyncFolderConfig_combinedFilters() {
        val config = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            fileFilters = listOf(".txt", ".pdf"),
            excludePatterns = listOf(".*\\.tmp")
        )

        // Include filters take priority for matching files
        assertTrue(config.shouldSyncFile("test.txt"))
        assertTrue(config.shouldSyncFile("document.pdf"))

        // Excluded even if matches include filter
        assertFalse(config.shouldSyncFile("test.tmp"))

        // Not matching any include filter
        assertFalse(config.shouldSyncFile("image.jpg"))
    }

    @Test
    fun testSyncResult_success() {
        val result = SyncResult(
            folderId = "test_folder",
            syncedFiles = 10,
            failedFiles = emptyList(),
            conflicts = emptyList(),
            startTime = System.currentTimeMillis() - 5000,
            endTime = System.currentTimeMillis(),
            bytesTransferred = 1024000L,
            success = true
        )

        assertEquals("test_folder", result.folderId)
        assertEquals(10, result.syncedFiles)
        assertTrue(result.failedFiles.isEmpty())
        assertTrue(result.conflicts.isEmpty())
        assertEquals(1024000L, result.bytesTransferred)
        assertTrue(result.success)
        assertTrue(result.duration() > 0)
    }

    @Test
    fun testSyncResult_withFailures() {
        val result = SyncResult(
            folderId = "test_folder",
            syncedFiles = 5,
            failedFiles = listOf("/local/file1.txt", "/local/file2.txt"),
            conflicts = emptyList(),
            startTime = System.currentTimeMillis() - 3000,
            endTime = System.currentTimeMillis(),
            bytesTransferred = 512000L,
            success = false
        )

        assertEquals(5, result.syncedFiles)
        assertEquals(2, result.failedFiles.size)
        assertFalse(result.success)
    }

    @Test
    fun testSyncResult_withConflicts() {
        val conflict = FileConflict(
            folderId = "test_folder",
            relativePath = "conflict.txt",
            localFile = LocalFile(
                path = "/local/conflict.txt",
                name = "conflict.txt",
                isDirectory = false,
                size = 1024L,
                lastModified = System.currentTimeMillis()
            ),
            remoteFile = com.nutomic.syncthingandroid.webdav.model.WebDAVFile(
                name = "conflict.txt",
                path = "/remote/conflict.txt",
                isDirectory = false,
                size = 2048L,
                lastModified = System.currentTimeMillis(),
                etag = "different"
            ),
            conflictType = ConflictType.BOTH_MODIFIED
        )

        val result = SyncResult(
            folderId = "test_folder",
            syncedFiles = 8,
            failedFiles = emptyList(),
            conflicts = listOf(conflict),
            startTime = System.currentTimeMillis() - 4000,
            endTime = System.currentTimeMillis(),
            bytesTransferred = 800000L
        )

        assertEquals(1, result.conflicts.size)
        assertEquals("conflict.txt", result.conflicts[0].fileName())
    }

    @Test
    fun testSyncFolderConfig_localFolderName() {
        val config1 = SyncFolderConfig(
            id = "test_folder",
            localPath = "/storage/emulated/0/Books",
            remotePath = "/remote/Books"
        )

        assertEquals("Books", config1.localFolderName())

        val config2 = config1.copy(localPath = "/storage/emulated/0/Books/")
        assertEquals("Books", config2.localFolderName())
    }

    @Test
    fun testSyncFolderConfig_remoteFolderName() {
        val config1 = SyncFolderConfig(
            id = "test_folder",
            localPath = "/local/Books",
            remotePath = "/webdav/Books"
        )

        assertEquals("Books", config1.remoteFolderName())

        val config2 = config1.copy(remotePath = "/webdav/Books/")
        assertEquals("Books", config2.remoteFolderName())
    }
}
