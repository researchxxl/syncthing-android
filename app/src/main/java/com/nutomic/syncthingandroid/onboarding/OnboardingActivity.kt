package com.nutomic.syncthingandroid.onboarding

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.activities.ThemedAppCompatActivity
import com.nutomic.syncthingandroid.activities.WebGuiActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.util.ConfigXml
import com.nutomic.syncthingandroid.util.ConfigXml.OpenConfigException
import com.nutomic.syncthingandroid.util.LocalActivityScope
import com.nutomic.syncthingandroid.util.PermissionUtil
import javax.inject.Inject

class OnboardingActivity : ThemedAppCompatActivity() {

    companion object {
        private const val TAG = "OnboardingActivity"
    }

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: move to ThemedAppCompatActivity when every activity is compose
        enableEdgeToEdge()
        (application as SyncthingApp).component().inject(this)

        val activityScope = this.lifecycleScope

        val haveStoragePermission = PermissionUtil.haveStoragePermission(this)
        val haveIgnoreDozePermission = haveIgnoreDozePermission()
        val haveLocationPermission = haveLocationPermission()
        val haveNotificationPermission = haveNotificationPermission()
        val haveConfig = checkForParseableConfig()

        val shouldSkipToMain = haveStoragePermission and haveNotificationPermission and haveConfig
        if (shouldSkipToMain) {
            // minimum requirements met, go to main
            return startApp()
        }

        val onboardingPages = listOfNotNull(
            OnboardingPage.WELCOME,
            OnboardingPage.STORAGE_PERMISSION.takeUnless { haveStoragePermission },
            OnboardingPage.BATTERY_OPTIMIZATION.takeUnless { haveIgnoreDozePermission },
            OnboardingPage.LOCATION_PERMISSION.takeUnless { haveLocationPermission },
            OnboardingPage.NOTIFICATION_PERMISSION.takeUnless { haveNotificationPermission },
            OnboardingPage.KEY_GENERATION.takeUnless { haveConfig },
        )

        setContent {
            ApplicationTheme {
                CompositionLocalProvider(
                    LocalActivityScope provides activityScope,
                ) {
                    OnboardingScreen(
                        pages = onboardingPages
                    )
                }
            }
        }
    }

    fun startApp() {
        val startIntoWebGui = prefs.getBoolean(Constants.PREF_START_INTO_WEB_GUI, false)
        val mainIntent = Intent(this, MainActivity::class.java)

        /*
         * In case start_into_web_gui option is enabled, start both activities
         * so that back navigation works as expected.
         */
        if (startIntoWebGui) {
            startActivities(arrayOf(
                mainIntent,
                Intent(this, WebGuiActivity::class.java)
            ))
        } else {
            startActivity(mainIntent)
        }
        finish()
    }

    private fun haveIgnoreDozePermission(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun haveLocationPermission(): Boolean {
        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else { true }

        return coarseLocationGranted && backgroundLocationGranted
    }


    private fun haveNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkForParseableConfig(): Boolean {
        val configExists = Constants.getConfigFile(this).exists()
        if (!configExists) {
            return false
        }

        try {
            val configParseTest = ConfigXml(this)
            configParseTest.loadConfig()
            return true
        } catch (_: OpenConfigException) {
            Log.d(TAG, "Failed to parse existing config. Will show key generation slide ...")
        }

        return false
    }
}
