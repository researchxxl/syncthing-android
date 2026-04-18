package com.nutomic.syncthingandroid.webdav

import android.content.Context
import android.content.SharedPreferences
import com.nutomic.syncthingandroid.webdav.model.SyncFolderConfig
import com.nutomic.syncthingandroid.webdav.model.SyncMode
import com.nutomic.syncthingandroid.webdav.model.ConflictStrategy
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WebDAVConfigManager
 */
class WebDAVConfigManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var configManager: WebDAVConfigManager

    @Before
    fun setup() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk()

        every { mockContext.getSharedPreferences("webdav_config", Context.MODE_PRIVATE) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        every { mockEditor.clear() } returns mockEditor
        every { mockEditor.apply() } just Runs

        configManager = WebDAVConfigManager(mockContext)
    }

    @Test
    fun testSaveServerConfig() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "testuser",
            password = "testpass",
            authType = WebDAVClient.AuthType.BASIC
        )

        configManager.saveServerConfig(config)

        verify { mockEditor.putString("server_url", "https://dav.example.com") }
        verify { mockEditor.putString("username", "testuser") }
        verify { mockEditor.putString("password", any()) } // Password should be encrypted
        verify { mockEditor.putString("auth_type", "BASIC") }
        verify { mockEditor.putInt("connect_timeout", 30000) }
        verify { mockEditor.putInt("read_timeout", 60000) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetServerConfig_whenConfigured() {
        every { mockPrefs.getString("server_url", null) } returns "https://dav.example.com"
        every { mockPrefs.getString("username", null) } returns "testuser"
        every { mockPrefs.getString("password", null) } returns "encrypted_password"
        every { mockPrefs.getString("auth_type", "BASIC") } returns "BASIC"
        every { mockPrefs.getInt("connect_timeout", 30000) } returns 30000
        every { mockPrefs.getInt("read_timeout", 60000) } returns 60000

        val config = configManager.getServerConfig()

        assertNotNull(config)
        assertEquals("https://dav.example.com", config?.serverUrl)
        assertEquals("testuser", config?.username)
        assertEquals(WebDAVClient.AuthType.BASIC, config?.authType)
        assertEquals(30000, config?.connectTimeoutMs)
        assertEquals(60000, config?.readTimeoutMs)
    }

    @Test
    fun testGetServerConfig_whenNotConfigured() {
        every { mockPrefs.getString("server_url", null) } returns null
        every { mockPrefs.getString("username", null) } returns null
        every { mockPrefs.getString("password", null) } returns null

        val config = configManager.getServerConfig()

        assertNull(config)
    }

    @Test
    fun testHasServerConfig_whenConfigured() {
        every { mockPrefs.getString("server_url", null) } returns "https://dav.example.com"
        every { mockPrefs.getString("username", null) } returns "testuser"
        every { mockPrefs.getString("password", null) } returns "encrypted_password"

        assertTrue(configManager.hasServerConfig())
    }

    @Test
    fun testHasServerConfig_whenNotConfigured() {
        every { mockPrefs.getString("server_url", null) } returns null

        assertFalse(configManager.hasServerConfig())
    }

    @Test
    fun testClearServerConfig() {
        configManager.clearServerConfig()

        verify { mockEditor.remove("server_url") }
        verify { mockEditor.remove("username") }
        verify { mockEditor.remove("password") }
        verify { mockEditor.remove("auth_type") }
        verify { mockEditor.remove("connect_timeout") }
        verify { mockEditor.remove("read_timeout") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testSaveFolderConfig() {
        val config = SyncFolderConfig(
            id = "folder_test",
            localPath = "/local/folder",
            remotePath = "/remote/folder",
            syncMode = SyncMode.BIDIRECTIONAL,
            conflictStrategy = ConflictStrategy.NEWEST_WINS,
            enabled = true
        )

        every { mockPrefs.getString("folder_configs", null) } returns null

        configManager.saveFolderConfig(config)

        verify { mockEditor.putString("folder_folder_test", any()) }
        verify { mockEditor.putString("folder_configs", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGetFolderConfig_whenExists() {
        val folderJson = """{
            "id":"folder_test",
            "localPath":"/local/folder",
            "remotePath":"/remote/folder",
            "syncMode":"BIDIRECTIONAL",
            "conflictStrategy":"NEWEST_WINS",
            "enabled":true,
            "fileFilters":[],
            "excludePatterns":[]
        }""".trimIndent()

        every { mockPrefs.getString("folder_folder_test", null) } returns folderJson

        val config = configManager.getFolderConfig("folder_test")

        assertNotNull(config)
        assertEquals("folder_test", config?.id)
        assertEquals("/local/folder", config?.localPath)
        assertEquals("/remote/folder", config?.remotePath)
        assertEquals(SyncMode.BIDIRECTIONAL, config?.syncMode)
        assertEquals(ConflictStrategy.NEWEST_WINS, config?.conflictStrategy)
        assertTrue(config?.enabled == true)
    }

    @Test
    fun testGetFolderConfig_whenNotExists() {
        every { mockPrefs.getString("folder_folder_test", null) } returns null

        val config = configManager.getFolderConfig("folder_test")

        assertNull(config)
    }

    @Test
    fun testGetAllFolderConfigs() {
        val folder1Json = """{"id":"folder1","localPath":"/path1","remotePath":"/remote1","syncMode":"BIDIRECTIONAL","conflictStrategy":"NEWEST_WINS","enabled":true,"fileFilters":[],"excludePatterns":[]}""".trimIndent()
        val folder2Json = """{"id":"folder2","localPath":"/path2","remotePath":"/remote2","syncMode":"LOCAL_TO_REMOTE","conflictStrategy":"LOCAL_WINS","enabled":true,"fileFilters":[],"excludePatterns":[]}""".trimIndent()

        every { mockPrefs.getString("folder_configs", null) } returns "[\"folder1\", \"folder2\"]"
        every { mockPrefs.getString("folder_folder1", null) } returns folder1Json
        every { mockPrefs.getString("folder_folder2", null) } returns folder2Json

        val configs = configManager.getAllFolderConfigs()

        assertEquals(2, configs.size)
        assertEquals("folder1", configs[0].id)
        assertEquals("folder2", configs[1].id)
    }

    @Test
    fun testDeleteFolderConfig() {
        every { mockPrefs.getString("folder_configs", null) } returns "[\"folder_test\"]"

        configManager.deleteFolderConfig("folder_test")

        verify { mockEditor.remove("folder_folder_test") }
        verify { mockEditor.putString("folder_configs", "[]") }
        verify { mockEditor.apply() }
    }

    @Test
    fun testSetFolderEnabled() {
        val folderJson = """{"id":"folder_test","localPath":"/local/folder","remotePath":"/remote/folder","syncMode":"BIDIRECTIONAL","conflictStrategy":"NEWEST_WINS","enabled":true,"fileFilters":[],"excludePatterns":[]}""".trimIndent()

        every { mockPrefs.getString("folder_folder_test", null) } returns folderJson

        configManager.setFolderEnabled("folder_test", false)

        verify { mockEditor.putString("folder_folder_test", any()) }
        verify { mockEditor.apply() }
    }

    @Test
    fun testGenerateFolderId() {
        val folderId1 = configManager.generateFolderId()
        val folderId2 = configManager.generateFolderId()

        assertNotEquals(folderId1, folderId2)
        assertTrue(folderId1.startsWith("folder_"))
        assertTrue(folderId2.startsWith("folder_"))
    }

    @Test
    fun testClearAll() {
        configManager.clearAll()

        verify { mockEditor.clear() }
        verify { mockEditor.apply() }
    }

    @Test
    fun testRegisterChangeListener() {
        val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()

        configManager.registerChangeListener(listener)

        verify { mockPrefs.registerOnSharedPreferenceChangeListener(listener) }
    }

    @Test
    fun testUnregisterChangeListener() {
        val listener = mockk<SharedPreferences.OnSharedPreferenceChangeListener>()

        configManager.unregisterChangeListener(listener)

        verify { mockPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    @Test
    fun testConnectionConfig_getNormalizedUrl() {
        val config1 = WebDAVClient.ConnectionConfig(
            serverUrl = "dav.example.com",
            username = "user",
            password = "pass"
        )
        assertEquals("https://dav.example.com", config1.getNormalizedUrl())

        val config2 = WebDAVClient.ConnectionConfig(
            serverUrl = "http://dav.example.com",
            username = "user",
            password = "pass"
        )
        assertEquals("http://dav.example.com", config2.getNormalizedUrl())

        val config3 = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com/",
            username = "user",
            password = "pass"
        )
        assertEquals("https://dav.example.com", config3.getNormalizedUrl())
    }

    @Test
    fun testConnectionConfig_isValid() {
        val validConfig = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "user",
            password = "pass"
        )
        assertTrue(validConfig.isValid())

        val invalidConfig1 = WebDAVClient.ConnectionConfig(
            serverUrl = "",
            username = "user",
            password = "pass"
        )
        assertFalse(invalidConfig1.isValid())

        val invalidConfig2 = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "",
            password = "pass"
        )
        assertFalse(invalidConfig2.isValid())
    }
}
