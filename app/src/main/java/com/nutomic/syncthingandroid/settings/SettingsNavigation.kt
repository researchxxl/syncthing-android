package com.nutomic.syncthingandroid.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SettingsNavDisplay(
    startDestination: SettingsRoute = SettingsRoute.Root
) {

    val backStack = rememberSettingsNavBackStack(startDestination)
    val listDetailSceneStrategy = rememberListDetailSceneStrategy<SettingsRoute>()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = settingsNavEntryProvider(backStack),
        sceneStrategy = listDetailSceneStrategy,
    )
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun settingsNavEntryProvider(
    backstack: NavBackStack<SettingsRoute>,
) = entryProvider<SettingsRoute>(
    fallback = { key ->
        NavEntry<SettingsRoute>(key, metadata = ListDetailSceneStrategy.detailPane()) { key ->
            Text("$key")
        }
    }
) {
    entry<SettingsRoute.Root>(
        metadata = ListDetailSceneStrategy.listPane()
    ) {
        SettingsRootScreen(backstack)
    }
    entry<SettingsRoute.RunConditions>(
        metadata = ListDetailSceneStrategy.listPane()
    ) {
        SettingsRunConditionsScreen()
    }
}
