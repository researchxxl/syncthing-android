package com.nutomic.syncthingandroid.settings

import android.util.Log
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.ui.NavDisplay
import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsRoute : NavKey {

    @Serializable
    data object Root : SettingsRoute

    @Serializable
    data object RunConditions : SettingsRoute
    @Serializable
    data object UserInterface : SettingsRoute
    @Serializable
    data object Behavior : SettingsRoute
    @Serializable
    data object SyncthingOptions : SettingsRoute
    @Serializable
    data object ImportExport : SettingsRoute
    @Serializable
    data object Troubleshooting : SettingsRoute
    @Serializable
    data object Experimental : SettingsRoute
    @Serializable
    data object About : SettingsRoute
    @Serializable
    data object Licenses : SettingsRoute


    companion object {
        const val TAG = "SettingsRoute"

        // Use these strings to open particular screen directly
        fun fromString(route: String?): SettingsRoute = when (route) {
            "RunConditions" -> RunConditions
            "UserInterface" -> UserInterface
            "Behavior" -> Behavior
            "SyncthingOptions" -> SyncthingOptions
            "ImportExport" -> ImportExport
            "Troubleshooting" -> Troubleshooting
            "Experimental" -> Experimental
            "About" -> About
            "Licenses" -> Licenses
            "Root" -> Root
            else -> {
                Log.d(TAG, "Unknown settings path provided: $route. Defaulting to Root.")
                Root
            }
        }
    }
}

interface Navigator<T: NavKey> {
    fun navigateTo(route: T)
    fun navigateBack()
}

val LocalSettingsNavigator = staticCompositionLocalOf<Navigator<SettingsRoute>> {
    error("Navigator not provided")
}

@Composable
fun rememberSettingsNavBackStack(startDestination: SettingsRoute): NavBackStack<SettingsRoute> {
    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) {
        val initialRouts = listOfNotNull(
            SettingsRoute.Root,
            SettingsRoute.About.takeIf { startDestination == SettingsRoute.Licenses },
            startDestination.takeIf { it != SettingsRoute.Root }
        ).toMutableStateList()
        NavBackStack(initialRouts)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavDisplay(
    backStack: NavBackStack<SettingsRoute>
) {
    val navigator = LocalSettingsNavigator.current

    NavDisplay(
        backStack = backStack,
        onBack = { navigator.navigateBack() },
        entryProvider = entryProvider {
            settingsRootEntry()
            settingsRunConditionsEntry()
            settingsUserInterfaceEntry()
            settingsBehaviorEntry()
            settingsSyncthingOptionsEntry()
            settingsImportExportEntry()
            settingsTroubleshootingEntry()
            settingsExperimentalEntry()
            settingsAboutEntry()
            licensesEntry()
        },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            // Slide in from left when navigating back
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
    )
}
