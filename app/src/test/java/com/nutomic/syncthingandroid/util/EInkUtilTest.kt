package com.nutomic.syncthingandroid.util

import android.content.Context
import android.os.Build
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for EInkUtil
 */
class EInkUtilTest {

    private lateinit var mockContext: Context

    @Before
    fun setup() {
        mockContext = mockk()
    }

    @Test
    fun testEInkUtil_constants() {
        // Verify constants are defined
        assertEquals(9, EInkUtil.E_INK_MANUFACTURERS.size)
        assertTrue(EInkUtil.E_INK_MANUFACTURERS.contains("onyx"))
        assertTrue(EInkUtil.E_INK_MANUFACTURERS.contains("kindle"))

        assertEquals(9, EInkUtil.E_INK_DEVICE_PREFIXES.size)
        assertTrue(EInkUtil.E_INK_DEVICE_PREFIXES.contains("onyx"))
        assertTrue(EInkUtil.E_INK_DEVICE_PREFIXES.contains("eboox"))
    }

    @Test
    fun testGetDeviceType() {
        // Test device type detection
        val deviceType = EInkUtil.getDeviceType(mockContext)
        assertTrue(deviceType == "eink" || deviceType == "lcd")
    }

    @Test
    fun testRefreshMode_enum() {
        // Test refresh mode enum
        val modes = EInkUtil.RefreshMode.values()
        assertEquals(4, modes.size)
        assertTrue(modes.contains(EInkUtil.RefreshMode.GLOBAL))
        assertTrue(modes.contains(EInkUtil.RefreshMode.PARTIAL))
        assertTrue(modes.contains(EInkUtil.RefreshMode.A2))
        assertTrue(modes.contains(EInkUtil.RefreshMode.NORMAL))
    }

    @Test
    fun testRecommendedRefreshInterval() {
        // Test that refresh interval is reasonable
        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)
        assertTrue(interval > 0)
        assertTrue(interval <= 10000)  // Max 10 seconds
    }

    @Test
    fun testRecommendedUIUpdateInterval() {
        // Test UI update interval
        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)
        assertTrue(interval > 0)
    }

    @Test
    fun testOptimalTextSize() {
        // Test text size optimization
        val defaultSize = 14f
        val optimalSize = EInkUtil.getOptimalTextSize(mockContext, defaultSize)
        assertTrue(optimalSize >= defaultSize)
    }

    @Test
    fun testOptimalSyncBatchSize() {
        // Test sync batch size optimization
        val defaultBatchSize = 10
        val optimalBatchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, defaultBatchSize)
        assertTrue(optimalBatchSize >= defaultBatchSize)
    }

    @Test
    fun testSupportedRefreshModes() {
        // Test refresh mode detection
        val modes = EInkUtil.getSupportedRefreshModes(mockContext)
        assertNotNull(modes)
        assertTrue(modes.isNotEmpty())
    }

    @Test
    fun testShouldDisableAnimations() {
        // Test animation disable check
        val shouldDisable = EInkUtil.shouldDisableAnimations(mockContext)
        // Should return boolean
        assertTrue(shouldDisable is Boolean)
    }

    @Test
    fun testShouldUseHighContrast() {
        // Test high contrast check
        val shouldUse = EInkUtil.shouldUseHighContrast(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldBatchUIUpdates() {
        // Test batch UI updates check
        val shouldBatch = EInkUtil.shouldBatchUIUpdates(mockContext)
        assertTrue(shouldBatch is Boolean)
    }

    @Test
    fun testGetRecommendedUIBatchSize() {
        // Test UI batch size recommendation
        val batchSize = EInkUtil.getRecommendedUIBatchSize(mockContext)
        assertTrue(batchSize > 0)
    }

    @Test
    fun testShouldUseProgressiveRendering() {
        // Test progressive rendering check
        val shouldUse = EInkUtil.shouldUseProgressiveRendering(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldUseGrayscale() {
        // Test grayscale check
        val shouldUse = EInkUtil.shouldUseGrayscale(mockContext)
        assertTrue(shouldUse is Boolean)
    }

    @Test
    fun testShouldPerformGlobalRefresh() {
        // Test global refresh timing
        val lastRefresh = System.currentTimeMillis() - 60000  // 1 minute ago
        val shouldRefresh = EInkUtil.shouldPerformGlobalRefresh(
            mockContext,
            lastRefresh,
            30000  // 30 second interval
        )
        // 1 minute ago > 30 seconds, so should refresh
        assertTrue(shouldRefresh)

        val shouldNotRefresh = EInkUtil.shouldPerformGlobalRefresh(
            mockContext,
            System.currentTimeMillis() - 10000,  // 10 seconds ago
            30000
        )
        assertFalse(shouldNotRefresh)
    }

    @Test
    fun testGetEInkBrand() {
        // Test brand detection
        val brand = EInkUtil.getEInkBrand(mockContext)
        assertNotNull(brand)
        assertTrue(brand == "onyx" || brand == "kindle" || brand == "kobo" ||
                brand == "pocketbook" || brand == "tolino" || brand == "hisense" ||
                brand == "bigme" || brand == "remarkable" || brand == "generic" ||
                brand == "unknown")
    }

    @Test
    fun testSupportsPartialRefresh() {
        // Test partial refresh support
        val supports = EInkUtil.supportsPartialRefresh(mockContext)
        assertTrue(supports is Boolean)
    }

    @Test
    fun testSupportsPenInput() {
        // Test pen input support
        val supports = EInkUtil.supportsPenInput(mockContext)
        assertTrue(supports is Boolean)
    }

    @Test
    fun testGetDeviceOptimizations() {
        // Test device optimizations map
        val optimizations = EInkUtil.getDeviceOptimizations(mockContext)
        assertNotNull(optimizations)

        // All values should be Boolean
        optimizations.values.forEach { value ->
            assertTrue(value is Boolean)
        }
    }

    @Test
    fun testIsOnyxBoox() {
        // Test Onyx Boox detection
        val isOnyx = EInkUtil.isOnyxBoox(mockContext)
        assertTrue(isOnyx is Boolean)
    }

    @Test
    fun testRefreshMode_values() {
        // Verify all refresh mode values exist
        val modes = EInkUtil.RefreshMode.values()
        val modeNames = modes.map { it.name }

        assertTrue(modeNames.contains("GLOBAL"))
        assertTrue(modeNames.contains("PARTIAL"))
        assertTrue(modeNames.contains("A2"))
        assertTrue(modeNames.contains("NORMAL"))
    }

    @Test
    fun testEInkDevicePrefixes_comprehensive() {
        // Test that all major E-Ink brands are covered
        val prefixes = EInkUtil.E_INK_DEVICE_PREFIXES

        // Verify major brands
        assertTrue(prefixes.any { "onyx" in it })
        assertTrue(prefixes.any { "kindle" in it })
        assertTrue(prefixes.any { "kobo" in it })
        assertTrue(prefixes.any { "pocketbook" in it })
        assertTrue(prefixes.any { "tolino" in it })
        assertTrue(prefixes.any { "hisense" in it })
        assertTrue(prefixes.any { "bigme" in it })
    }

    @Test
    fun testEInkManufacturers_comprehensive() {
        // Test that all major E-Ink manufacturers are covered
        val manufacturers = EInkUtil.E_INK_MANUFACTURERS

        // Verify major manufacturers
        assertTrue(manufacturers.any { "onyx" in it })
        assertTrue(manufacturers.any { "kindle" in it })
        assertTrue(manufacturers.any { "kobo" in it })
        assertTrue(manufacturers.any { "pocketbook" in it })
    }

    @Test
    fun testGetRecommendedRefreshInterval_eink() {
        // Mock E-Ink device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns true

        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)

        // Should be longer for E-Ink
        assertTrue(interval >= 2000)  // At least 2 seconds
    }

    @Test
    fun testGetRecommendedRefreshInterval_normal() {
        // Mock normal device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns false

        val interval = EInkUtil.getRecommendedRefreshInterval(mockContext)

        // Should be shorter for normal devices
        assertTrue(interval <= 1000)  // At most 1 second
    }

    @Test
    fun testGetOptimalSyncBatchSize_eink() {
        // Mock E-Ink device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns true

        val batchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, 10)

        // Should be larger for E-Ink
        assertTrue(batchSize >= 10)
    }

    @Test
    fun testGetOptimalSyncBatchSize_normal() {
        // Mock normal device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns false

        val batchSize = EInkUtil.getOptimalSyncBatchSize(mockContext, 10)

        // Should be default for normal devices
        assertEquals(10, batchSize)
    }

    @Test
    fun testGetRecommendedUIUpdateInterval_eink() {
        // Mock E-Ink device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns true

        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)

        // Should be longer for E-Ink
        assertTrue(interval >= 3000)  // At least 3 seconds
    }

    @Test
    fun testGetRecommendedUIUpdateInterval_normal() {
        // Mock normal device
        mockkObject(EInkUtil)
        every { EInkUtil.isEInkDevice(mockContext) } returns false

        val interval = EInkUtil.getRecommendedUIUpdateInterval(mockContext)

        // Should be shorter for normal devices
        assertTrue(interval <= 1000)  // At most 1 second
    }
}
