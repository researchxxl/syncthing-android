package com.nutomic.syncthingandroid.webdav

import android.content.Context
import com.nutomic.syncthingandroid.webdav.model.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ConflictResolver
 */
class ConflictResolverTest {

    private lateinit var mockContext: Context
    private lateinit var conflictResolver: ConflictResolver

    @Before
    fun setup() {
        mockContext = mockk()
        conflictResolver = ConflictResolver(mockContext)
    }

    @Test
    fun testDetectConflict_bothModified() = runTest {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            lastSyncTime = System.currentTimeMillis() - 3600000 // 1 hour ago
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L, // Different size
            lastModified = System.currentTimeMillis() - 1800000, // 30 min ago
            etag = "different_etag"
        )

        val conflict = conflictResolver.detectConflict(localFile, remoteFile)

        assertNotNull(conflict)
        assertEquals(ConflictType.BOTH_MODIFIED, conflict)
    }

    @Test
    fun testDetectConflict_noConflict() = runTest {
        val lastSyncTime = System.currentTimeMillis() - 3600000
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = lastSyncTime - 1000, // Modified before last sync
            lastSyncTime = lastSyncTime
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = lastSyncTime, // Same as last sync
            etag = "same_etag"
        )

        val conflict = conflictResolver.detectConflict(localFile, remoteFile)

        assertNull(conflict)
    }

    @Test
    fun testResolveConflict_localWins() = runTest {
        val fileConflict = createTestConflict()

        val result = conflictResolver.resolveConflict(
            fileConflict,
            ConflictStrategy.LOCAL_WINS
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() is ConflictResolution.LocalWins)
    }

    @Test
    fun testResolveConflict_remoteWins() = runTest {
        val fileConflict = createTestConflict()

        val result = conflictResolver.resolveConflict(
            fileConflict,
            ConflictStrategy.REMOTE_WINS
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() is ConflictResolution.RemoteWins)
    }

    @Test
    fun testResolveConflict_newestWins_localIsNewer() = runTest {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            lastSyncTime = System.currentTimeMillis() - 3600000
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis() - 1800000,
            etag = "etag123"
        )

        val conflict = FileConflict(
            folderId = "test_folder",
            relativePath = "test.txt",
            localFile = localFile,
            remoteFile = remoteFile,
            conflictType = ConflictType.BOTH_MODIFIED
        )

        val result = conflictResolver.resolveConflict(
            conflict,
            ConflictStrategy.NEWEST_WINS
        )

        assertTrue(result.isSuccess)
        val resolution = result.getOrNull()
        assertTrue(resolution is ConflictResolution.NewestWins)
        assertTrue((resolution as ConflictResolution.NewestWins).isLocal)
    }

    @Test
    fun testResolveConflict_newestWins_remoteIsNewer() = runTest {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis() - 1800000,
            lastSyncTime = System.currentTimeMillis() - 3600000
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis(),
            etag = "etag123"
        )

        val conflict = FileConflict(
            folderId = "test_folder",
            relativePath = "test.txt",
            localFile = localFile,
            remoteFile = remoteFile,
            conflictType = ConflictType.BOTH_MODIFIED
        )

        val result = conflictResolver.resolveConflict(
            conflict,
            ConflictStrategy.NEWEST_WINS
        )

        assertTrue(result.isSuccess)
        val resolution = result.getOrNull()
        assertTrue(resolution is ConflictResolution.NewestWins)
        assertFalse((resolution as ConflictResolution.NewestWins).isLocal)
    }

    @Test
    fun testResolveConflict_keepBoth() = runTest {
        val fileConflict = createTestConflict()

        val result = conflictResolver.resolveConflict(
            fileConflict,
            ConflictStrategy.KEEP_BOTH
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() is ConflictResolution.KeepBoth)

        val resolution = result.getOrNull() as ConflictResolution.KeepBoth
        assertTrue(resolution.localCopyPath.contains("_local_"))
        assertTrue(resolution.remoteCopyPath.contains("_remote_"))
    }

    @Test
    fun testResolveConflict_manualResolve() = runTest {
        val fileConflict = createTestConflict()

        val result = conflictResolver.resolveConflict(
            fileConflict,
            ConflictStrategy.MANUAL_RESOLVE
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() is ConflictResolution.ManualResolve)
    }

    @Test
    fun testIsConflictCopy_local() {
        assertTrue(conflictResolver.isConflictCopy("conflict_test_local_20240118_120000.txt"))
        assertTrue(conflictResolver.isConflictCopy("conflict_document_local_20240118_120000.pdf"))
    }

    @Test
    fun testIsConflictCopy_remote() {
        assertTrue(conflictResolver.isConflictCopy("conflict_test_remote_20240118_120000.txt"))
        assertTrue(conflictResolver.isConflictCopy("conflict_document_remote_20240118_120000.pdf"))
    }

    @Test
    fun testIsConflictCopy_notConflictCopy() {
        assertFalse(conflictResolver.isConflictCopy("test.txt"))
        assertFalse(conflictResolver.isConflictCopy("document.pdf"))
        assertFalse(conflictResolver.isConflictCopy("conflict.txt"))
    }

    @Test
    fun testExtractOriginalFileName_local() {
        val conflictFileName = "conflict_test_local_20240118_120000.txt"
        val originalName = conflictResolver.extractOriginalFileName(conflictFileName)

        assertEquals("test.txt", originalName)
    }

    @Test
    fun testExtractOriginalFileName_remote() {
        val conflictFileName = "conflict_document_remote_20240118_120000.pdf"
        val originalName = conflictResolver.extractOriginalFileName(conflictFileName)

        assertEquals("document.pdf", originalName)
    }

    @Test
    fun testExtractOriginalFileName_noExtension() {
        val conflictFileName = "conflict_readme_local_20240118_120000"
        val originalName = conflictResolver.extractOriginalFileName(conflictFileName)

        assertEquals("readme", originalName)
    }

    @Test
    fun testExtractOriginalFileName_notConflictCopy() {
        val fileName = "test.txt"
        val originalName = conflictResolver.extractOriginalFileName(fileName)

        assertNull(originalName)
    }

    @Test
    fun testGetConflictDescription() {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis() - 1800000,
            lastSyncTime = System.currentTimeMillis() - 3600000
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis() - 900000,
            etag = "etag123"
        )

        val conflict = FileConflict(
            folderId = "test_folder",
            relativePath = "test.txt",
            localFile = localFile,
            remoteFile = remoteFile,
            conflictType = ConflictType.BOTH_MODIFIED
        )

        val description = conflictResolver.getConflictDescription(conflict)

        assertTrue(description.contains("test.txt"))
        assertTrue(description.contains("Local:"))
        assertTrue(description.contains("Remote:"))
        assertTrue(description.contains("Type:"))
        assertTrue(description.contains("BOTH_MODIFIED"))
    }

    @Test
    fun testAreFilesEqual_sameSizeAndTime() = runTest {
        val timestamp = System.currentTimeMillis()
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = timestamp
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = timestamp,
            etag = "etag123"
        )

        val areEqual = conflictResolver.areFilesEqual(localFile, remoteFile)

        assertTrue(areEqual)
    }

    @Test
    fun testAreFilesEqual_differentSize() = runTest {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis()
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis(),
            etag = "etag123"
        )

        val areEqual = conflictResolver.areFilesEqual(localFile, remoteFile)

        assertFalse(areEqual)
    }

    @Test
    fun testGenerateConflictCopyPath() {
        val originalPath = "/local/folder/test.txt"
        val timestamp = "20240118_120000"

        val localCopyPath = conflictResolver.generateConflictCopyPath(
            originalPath,
            timestamp,
            "local"
        )

        assertTrue(localCopyPath.contains("_local_"))
        assertTrue(localCopyPath.endsWith(".txt"))
    }

    private fun createTestConflict(): FileConflict {
        val localFile = LocalFile(
            path = "/local/test.txt",
            name = "test.txt",
            isDirectory = false,
            size = 1024L,
            lastModified = System.currentTimeMillis(),
            lastSyncTime = System.currentTimeMillis() - 3600000
        )

        val remoteFile = WebDAVFile(
            name = "test.txt",
            path = "/remote/test.txt",
            isDirectory = false,
            size = 2048L,
            lastModified = System.currentTimeMillis() - 1800000,
            etag = "different_etag"
        )

        return FileConflict(
            folderId = "test_folder",
            relativePath = "test.txt",
            localFile = localFile,
            remoteFile = remoteFile,
            conflictType = ConflictType.BOTH_MODIFIED
        )
    }
}
