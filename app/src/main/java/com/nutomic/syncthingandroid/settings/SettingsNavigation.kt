package com.nutomic.syncthingandroid.settings

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import me.zhanghai.compose.preference.ProvidePreferenceLocals

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
            startDestination.takeIf { it != SettingsRoute.Root }
        ).toMutableStateList()
        NavBackStack(initialRouts)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavDisplay(
    startDestination: SettingsRoute = SettingsRoute.Root,
    onFinishActivity: () -> Unit = {}
) {
    val backStack = rememberSettingsNavBackStack(startDestination)
    val navigator = remember(backStack, onFinishActivity) {
        object : Navigator<SettingsRoute> {
            override fun navigateTo(route: SettingsRoute) {
                backStack.add(route)
            }
            override fun navigateBack() {
                if (backStack.size == 1) {
                    onFinishActivity()
                } else {
                    backStack.removeLastOrNull()
                }
            }
        }
    }

    CompositionLocalProvider(LocalSettingsNavigator provides navigator) {
        ProvidePreferenceLocals {
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
    }
}
