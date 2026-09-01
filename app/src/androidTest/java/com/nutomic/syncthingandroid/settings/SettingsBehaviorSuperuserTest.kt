package com.nutomic.syncthingandroid.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** Direct Compose coverage for the manually controlled superuser preference row. */
class SettingsBehaviorSuperuserTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleIsPresentAndPendingEnableRemainsOffAndDisabled() {
        composeRule.setContent {
            SuperuserPreferenceRow(
                state = SuperuserPreferenceUiState(
                    checked = false,
                    enabled = false,
                    summary = Summary.REQUESTING,
                ),
                onToggle = {},
            )
        }

        composeRule.onNodeWithText("Run Syncthing as Superuser").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun checkedAndUnavailableStateIsVisibleAndEnabled() {
        composeRule.setContent {
            SuperuserPreferenceRow(
                state = SuperuserPreferenceUiState(
                    checked = true,
                    enabled = true,
                    summary = Summary.UNAVAILABLE,
                ),
                onToggle = {},
            )
        }

        composeRule.onNodeWithText("Run Syncthing as Superuser").assertIsDisplayed()
        composeRule.onNode(isToggleable()).assertIsOn().assertIsEnabled()
    }

    @Test
    fun disableToggleIsForwardedWithoutAConfirmationDialog() {
        var requestedValue: Boolean? = null
        composeRule.setContent {
            SuperuserPreferenceRow(
                state = SuperuserPreferenceUiState(
                    checked = true,
                    enabled = true,
                    summary = Summary.ENABLED,
                ),
                onToggle = { requestedValue = it },
            )
        }

        composeRule.onNode(isToggleable()).performClick()

        assertEquals(false, requestedValue)
    }
}
