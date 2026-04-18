package com.nutomic.syncthingandroid.webdav.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WebDAV data models
 */
class WebDAVModelsTest {

    private lateinit var webDAVFile: WebDAVFile
    private lateinit var localFile: LocalFile
    private lateinit var syncFolderConfig: SyncFolderConfig

    @Before
    fun setup() {
        webDAVFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            etag = "abc123",
            contentType = "text/plain"
        )

        localFile = LocalFile(
            path = "/local/folder/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            lastSyncTime = System.currentTimeMillis() - 3600000
        )

        syncFolderConfig = SyncFolderConfig(
            id = "folder_test",
            localPath = "/local/folder",
            remotePath = "/remote",
            syncMode = SyncMode.BIDIRECTIONAL,
            conflictStrategy = ConflictStrategy.NEWEST_WINS,
            enabled = true
        )
    }

    @Test
    fun testWebDAVFile_parentPath() {
        val parentPath = webDAVFile.parentPath()
        assertEquals("/remote", parentPath)

        val nestedFile = WebDAVFile(
            name = "nested.txt",
            path = "/remote/subfolder/nested.txt",
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            etag = null
        )
        assertEquals("/remote/subfolder", nestedFile.parentPath())
    }

    @Test
    fun testWebDAVFile_extension() {
        assertEquals("txt", webDAVFile.extension())

        val noExtensionFile = WebDAVFile(
            name = "README",
            path = "/remote/README",
            isDirectory = false,
            size = 0L,
            lastModified = 0L,
            etag = null
        )
        assertEquals("", noExtensionFile.extension())

        val directoryFile = WebDAVFile(
            name = "folder",
            path = "/remote/folder",
            isDirectory = true,
            size = 0L,
            lastModified = 0L,
            etag = null
        )
        assertEquals("", directoryFile.extension())
    }

    @Test
    fun testLocalFile_relativePath() {
        val relativePath = localFile.relativePath("/local/folder")
        assertEquals("test.txt", relativePath)

        val nestedFile = LocalFile(
            path = "/local/folder/subfolder/nested.txt",
            name = "nested.txt",
            isDirectory = false,
            size = 0L,
            lastModified = 0L
        )
        val nestedRelativePath = nestedFile.relativePath("/local/folder")
        assertEquals("subfolder/nested.txt", nestedRelativePath)
    }

    @Test
    fun testLocalFile_hasBeenModified() {
        assertTrue(localFile.hasBeenModified())

        val unmodifiedFile = localFile.copy(
            lastModified = localFile.lastSyncTime - 1000
        )
        assertFalse(unmodifiedFile.hasBeenModified())
    }

    @Test
    fun testSyncFolderConfig_localFolderName() {
        assertEquals("folder", syncFolderConfig.localFolderName())

        val trailingSlashConfig = syncFolderConfig.copy(
            localPath = "/local/folder/"
        )
        assertEquals("folder", trailingSlashConfig.localFolderName())
    }

    @Test
    fun testSyncFolderConfig_remoteFolderName() {
        assertEquals("remote", syncFolderConfig.remoteFolderName())

        val nestedConfig = syncFolderConfig.copy(
            remotePath = "/remote/subfolder"
        )
        assertEquals("subfolder", nestedConfig.remoteFolderName())
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_noFilters() {
        assertTrue(syncFolderConfig.shouldSyncFile("test.txt"))
        assertTrue(syncFolderConfig.shouldSyncFile("document.pdf"))
        assertTrue(syncFolderConfig.shouldSyncFile("image.jpg"))
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_withIncludeFilters() {
        val configWithFilters = syncFolderConfig.copy(
            fileFilters = listOf(".txt", ".pdf")
        )
        assertTrue(configWithFilters.shouldSyncFile("test.txt"))
        assertTrue(configWithFilters.shouldSyncFile("document.pdf"))
        assertFalse(configWithFilters.shouldSyncFile("image.jpg"))
    }

    @Test
    fun testSyncFolderConfig_shouldSyncFile_withExcludePatterns() {
        val configWithExcludes = syncFolderConfig.copy(
            excludePatterns = listOf(".*\\.tmp", ".*\\.bak")
        )
        assertFalse(configWithExcludes.shouldSyncFile("test.tmp"))
        assertFalse(configWithExcludes.shouldSyncFile("test.bak"))
        assertTrue(configWithExcludes.shouldSyncFile("test.txt"))
    }

    @Test
    fun testSyncMode_values() {
        assertEquals(5, SyncMode.values().size)
        assertTrue(SyncMode.values().contains(SyncMode.BIDIRECTIONAL))
        assertTrue(SyncMode.values().contains(SyncMode.LOCAL_TO_REMOTE))
        assertTrue(SyncMode.values().contains(SyncMode.REMOTE_TO_LOCAL))
        assertTrue(SyncMode.values().contains(SyncMode.MIRROR_LOCAL))
        assertTrue(SyncMode.values().contains(SyncMode.MIRROR_REMOTE))
    }

    @Test
    fun testConflictStrategy_values() {
        assertEquals(5, ConflictStrategy.values().size)
        assertTrue(ConflictStrategy.values().contains(ConflictStrategy.LOCAL_WINS))
        assertTrue(ConflictStrategy.values().contains(ConflictStrategy.REMOTE_WINS))
        assertTrue(ConflictStrategy.values().contains(ConflictStrategy.NEWEST_WINS))
        assertTrue(ConflictStrategy.values().contains(ConflictStrategy.KEEP_BOTH))
        assertTrue(ConflictStrategy.values().contains(ConflictStrategy.MANUAL_RESOLVE))
    }

    @Test
    fun testSyncResult_duration() {
        val startTime = System.currentTimeMillis() - 5000
        val endTime = System.currentTimeMillis()

        val result = SyncResult(
            folderId = "test_folder",
            startTime = startTime,
            endTime = endTime
        )

        val duration = result.duration()
        assertTrue(duration >= 4900 && duration <= 5100) // Allow 100ms tolerance
    }

    @Test
    fun testSyncResult_formattedDuration() {
        val result = SyncResult(
            folderId = "test_folder",
            startTime = System.currentTimeMillis() - 30000,
            endTime = System.currentTimeMillis()
        )

        val formatted = result.formattedDuration()
        assertTrue(formatted.endsWith("s"))
    }

    @Test
    fun testSyncResult_formattedBytesTransferred() {
        val result = SyncResult(
            folderId = "test_folder",
            bytesTransferred = 1024L
        )

        val formatted = result.formattedBytesTransferred()
        assertTrue(formatted.contains("KB"))

        val largeResult = result.copy(bytesTransferred = 1024L * 1024 * 5)
        val largeFormatted = largeResult.formattedBytesTransferred()
        assertTrue(largeFormatted.contains("MB"))
    }

    @Test
    fun testFileConflict_fileName() {
        val conflict = FileConflict(
            folderId = "test_folder",
            relativePath = "documents/test.txt",
            localFile = localFile,
            remoteFile = webDAVFile,
            conflictType = ConflictType.BOTH_MODIFIED
        )

        assertEquals("test.txt", conflict.fileName())

        val nestedConflict = conflict.copy(
            relativePath = "subfolder/nested/file.pdf"
        )
        assertEquals("file.pdf", nestedConflict.fileName())
    }

    @Test
    fun testConflictType_values() {
        assertEquals(4, ConflictType.values().size)
        assertTrue(ConflictType.values().contains(ConflictType.BOTH_MODIFIED))
        assertTrue(ConflictType.values().contains(ConflictType.BOTH_DELETED))
        assertTrue(ConflictType.values().contains(ConflictType.LOCAL_DELETED_REMOTE_MODIFIED))
        assertTrue(ConflictType.values().contains(ConflictType.LOCAL_MODIFIED_REMOTE_DELETED))
    }
}
