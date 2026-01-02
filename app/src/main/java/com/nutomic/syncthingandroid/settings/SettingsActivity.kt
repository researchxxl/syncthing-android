package com.nutomic.syncthingandroid.settings

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.SyncthingActivity
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.Preferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals


class SettingsActivity : SyncthingActivity() {

    @Inject
    lateinit var prefFlow: MutableStateFlow<Preferences>

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

            ApplicationTheme {
                CompositionLocalProvider(LocalSettingsNavigator provides navigator) {
                    ProvidePreferenceLocals(flow = prefFlow) {
                        SettingsNavDisplay(
                            backStack = backStack,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_START_DESTINATION = "com.nutomic.syncthingandroid.settings.EXTRA_START_DESTINATION"
    }
}
