package com.nutomic.syncthingandroid.settings

import android.content.ComponentName
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.SyncthingActivity
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals

val LocalSyncthingService = staticCompositionLocalOf<SyncthingService?> { null }
val LocalServiceUpdateTick = staticCompositionLocalOf { 0 }

class SettingsActivity : SyncthingActivity() {

    @Inject
    lateinit var prefFlow: MutableStateFlow<Preferences>

    private var syncthingServiceState by mutableStateOf<SyncthingService?>(service)

    // The ticker will help update the ui whenever the state of syncthing service updates
    private var serviceUpdateTick by mutableIntStateOf(0)
    val stateChangeListener = SyncthingService.OnServiceStateChangeListener {
        serviceUpdateTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SyncthingApp).component().inject(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val routeStr = intent.getStringExtra(EXTRA_START_DESTINATION)
        val startDestination: SettingsRoute = SettingsRoute.fromString(routeStr)

        setContent {
            val backStack = rememberSettingsNavBackStack(startDestination)
            val navigator = remember(backStack) {
                object : Navigator<SettingsRoute> {
                    override fun navigateTo(route: SettingsRoute) {
                        backStack.add(route)
                    }
                    override fun navigateBack() {
                        if (backStack.size == 1) {
                            finish()
                        } else {
                            backStack.removeLastOrNull()
                        }
                    }
                }
            }

            // If we are on syncthing options and service stop, pop out to settings root
            val shouldExitStOptions by remember { derivedStateOf {
                val isNotActive = syncthingServiceState?.currentState != SyncthingService.State.ACTIVE
                val isOnStOptions = backStack.last() == SettingsRoute.SyncthingOptions
                isNotActive && isOnStOptions
            } }

            LaunchedEffect(shouldExitStOptions) {
                if (shouldExitStOptions) {
                    navigator.navigateBack()
                }
            }

            ApplicationTheme {
                CompositionLocalProvider(
                    LocalSettingsNavigator provides navigator,
                    LocalSyncthingService provides syncthingServiceState,
                    LocalServiceUpdateTick provides serviceUpdateTick,
                ) {
                    ProvidePreferenceLocals(flow = prefFlow) {
                        SettingsNavDisplay(
                            backStack = backStack,
                        )
                    }
                }
            }
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        super.onServiceConnected(name, service)
        syncthingServiceState = this.service
        syncthingServiceState?.registerOnServiceStateChangeListener(stateChangeListener)
        serviceUpdateTick++
    }

    override fun onServiceDisconnected(name: ComponentName) {
        syncthingServiceState?.unregisterOnServiceStateChangeListener(stateChangeListener)
        super.onServiceDisconnected(name)
        syncthingServiceState = null
        serviceUpdateTick++
    }


    companion object {
        const val EXTRA_START_DESTINATION = "com.nutomic.syncthingandroid.settings.EXTRA_START_DESTINATION"
    }
}
