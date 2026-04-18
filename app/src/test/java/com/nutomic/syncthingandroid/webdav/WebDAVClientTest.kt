package com.nutomic.syncthingandroid.webdav

import android.content.Context
import com.github.sardine.Sardine
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WebDAVClient
 */
class WebDAVClientTest {

    private lateinit var mockContext: Context
    private lateinit var webDAVClient: WebDAVClient

    @Before
    fun setup() {
        mockContext = mockk()
        webDAVClient = WebDAVClient(mockContext)
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

        val invalidConfig3 = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "user",
            password = ""
        )
        assertFalse(invalidConfig3.isValid())
    }

    @Test
    fun testIsConnected_whenNotConnected() {
        assertFalse(webDAVClient.isConnected())
    }

    @Test
    fun testDisconnect() {
        // After disconnect, client should not be connected
        webDAVClient.disconnect()
        assertFalse(webDAVClient.isConnected())
    }

    @Test
    fun testAuthType_values() {
        assertEquals(3, WebDAVClient.AuthType.values().size)
        assertTrue(WebDAVClient.AuthType.values().contains(WebDAVClient.AuthType.BASIC))
        assertTrue(WebDAVClient.AuthType.values().contains(WebDAVClient.AuthType.DIGEST))
        assertTrue(WebDAVClient.AuthType.values().contains(WebDAVClient.AuthType.NONE))
    }

    @Test
    fun testConnectionConfig_defaults() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "user",
            password = "pass"
        )

        assertEquals(WebDAVClient.AuthType.BASIC, config.authType)
        assertEquals(30000, config.connectTimeoutMs)
        assertEquals(60000, config.readTimeoutMs)
    }

    @Test
    fun testConnectionConfig_customTimeouts() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "user",
            password = "pass",
            authType = WebDAVClient.AuthType.DIGEST,
            connectTimeoutMs = 60000,
            readTimeoutMs = 120000
        )

        assertEquals(WebDAVClient.AuthType.DIGEST, config.authType)
        assertEquals(60000, config.connectTimeoutMs)
        assertEquals(120000, config.readTimeoutMs)
    }

    @Test
    fun testConnectionConfig_normalizeUrl_withSpaces() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "  dav.example.com  ",
            username = "user",
            password = "pass"
        )

        // Spaces should be trimmed but not affect the URL structure
        val normalized = config.getNormalizedUrl()
        assertTrue(normalized.startsWith("https://"))
        assertTrue(normalized.contains("dav.example.com"))
        assertFalse(normalized.endsWith("/"))
    }

    @Test
    fun testConnectionConfig_normalizeUrl_withPort() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "dav.example.com:8080",
            username = "user",
            password = "pass"
        )

        val normalized = config.getNormalizedUrl()
        assertEquals("https://dav.example.com:8080", normalized)
    }

    @Test
    fun testConnectionConfig_normalizeUrl_withPath() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "dav.example.com/webdav",
            username = "user",
            password = "pass"
        )

        val normalized = config.getNormalizedUrl()
        assertEquals("https://dav.example.com/webdav", normalized)
    }

    @Test
    fun testConnectionConfig_normalizeUrl_withHttps() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com/webdav/",
            username = "user",
            password = "pass"
        )

        val normalized = config.getNormalizedUrl()
        assertEquals("https://dav.example.com/webdav", normalized)
    }

    @Test
    fun testConnectionConfig_normalizeUrl_withHttp() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "http://dav.example.com/webdav/",
            username = "user",
            password = "pass"
        )

        val normalized = config.getNormalizedUrl()
        assertEquals("http://dav.example.com/webdav", normalized)
    }

    @Test
    fun testConnectionConfig_authTypeNone() {
        val config = WebDAVClient.ConnectionConfig(
            serverUrl = "https://dav.example.com",
            username = "",
            password = "",
            authType = WebDAVClient.AuthType.NONE
        )

        // No-auth config should still be valid (for public WebDAV servers)
        assertTrue(config.getNormalizedUrl().isNotEmpty())
        assertEquals(WebDAVClient.AuthType.NONE, config.authType)
    }
}
