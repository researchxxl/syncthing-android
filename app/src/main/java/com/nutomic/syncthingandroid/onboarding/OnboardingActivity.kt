package com.nutomic.syncthingandroid.onboarding

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import java.io.Serializable
import androidx.lifecycle.lifecycleScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.MainActivity
import com.nutomic.syncthingandroid.activities.ThemedAppCompatActivity
import com.nutomic.syncthingandroid.activities.WebGuiActivity
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingRunnable.ExecutableNotFoundException
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.util.ConfigXml
import com.nutomic.syncthingandroid.util.ConfigXml.OpenConfigException
import com.nutomic.syncthingandroid.util.LocalActivityScope
import com.nutomic.syncthingandroid.util.PermissionUtil
import com.nutomic.syncthingandroid.util.Util
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OnboardingUiState(
    val pages: List<OnboardingPage> = emptyList(),
    val currentPage: Int = 0,
    val hasStoragePermission: Boolean = false,
    val hasIgnoreDozePermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasConfig: Boolean = false,
    val isRunningOnTv: Boolean = false,
    val keyGenerationRunning: Boolean = false,
    val keyGenerationFailed: Boolean = false,
    val keyGenerationStatus: String = "",
) : Serializable

class OnboardingActivity : ThemedAppCompatActivity() {

    companion object {
        private const val TAG = "OnboardingActivity"
        const val REQUEST_WRITE_STORAGE = 143

        // Key used to persist the whole UI state across configuration changes (e.g. rotation).
        private const val STATE_UI_STATE = "onboarding_ui_state"
    }

    @Inject
    lateinit var prefs: SharedPreferences

    private var uiState by mutableStateOf(OnboardingUiState())

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionState()
        if (isGranted) {
            Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "User granted POST_NOTIFICATIONS permission.")
            advanceIfCurrentPage(OnboardingPage.NOTIFICATION_PERMISSION)
        } else {
            Log.i(TAG, "User denied POST_NOTIFICATIONS permission.")
        }
    }

    private val coarseLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionState()
        if (isGranted) {
            Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "User granted ACCESS_COARSE_LOCATION permission.")
            advanceIfCurrentPage(OnboardingPage.LOCATION_PERMISSION)
        } else {
            Log.i(TAG, "User denied ACCESS_COARSE_LOCATION permission.")
        }
    }

    private val fineLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionState()
        if (!isGranted) {
            Log.i(TAG, "User denied ACCESS_FINE_LOCATION permission.")
            return@registerForActivityResult
        }
        Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
        Log.i(TAG, "User granted ACCESS_FINE_LOCATION permission.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            advanceIfCurrentPage(OnboardingPage.LOCATION_PERMISSION)
        }
    }

    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        refreshPermissionState()
        if (isGranted) {
            Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "User granted ACCESS_BACKGROUND_LOCATION permission.")
            advanceIfCurrentPage(OnboardingPage.LOCATION_PERMISSION)
        } else {
            Log.i(TAG, "User denied ACCESS_BACKGROUND_LOCATION permission.")
        }
    }

    private val androidQLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPermissionState()
        if (results[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            Log.i(TAG, "User granted ACCESS_FINE_LOCATION permission.")
            advanceIfCurrentPage(OnboardingPage.LOCATION_PERMISSION)
        } else {
            Log.i(TAG, "User denied ACCESS_FINE_LOCATION permission.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TODO: move to ThemedAppCompatActivity when every activity is compose
        enableEdgeToEdge()
        (application as SyncthingApp).component().inject(this)

        val activityScope = this.lifecycleScope

        val isRunningOnTv = Util.isRunningOnTV(this)
        Log.d(TAG, if (isRunningOnTv) "Running on a TV Device" else "Running on a non-TV Device")

        val haveStoragePermission = haveStoragePermission()
        val haveIgnoreDozePermission = haveIgnoreDozePermission()
        val haveLocationPermission = haveLocationPermission()
        val haveNotificationPermission = haveNotificationPermission()
        val haveConfig = checkForParseableConfig()

        // On recreation (e.g. rotation) restore the entire previous UI state so nothing resets,
        // including the page set, which is decided once and must stay stable for the whole flow.
        val savedState = restoreUiState(savedInstanceState)

        if (savedState == null) {
            val shouldSkipToMain = haveStoragePermission && haveNotificationPermission && haveConfig
            if (shouldSkipToMain) {
                // minimum requirements met, go to main
                return startApp()
            }
        }

        uiState = savedState?.copy(
            // Permissions/config may have changed while we were gone; re-derive these.
            hasStoragePermission = haveStoragePermission,
            hasIgnoreDozePermission = haveIgnoreDozePermission,
            hasLocationPermission = haveLocationPermission,
            hasNotificationPermission = haveNotificationPermission,
            hasConfig = haveConfig,
            isRunningOnTv = isRunningOnTv,
            // A key-generation coroutine cannot survive recreation, so never restore it as running.
            keyGenerationRunning = false,
        ) ?: OnboardingUiState(
            pages = listOfNotNull(
                OnboardingPage.WELCOME,
                OnboardingPage.STORAGE_PERMISSION.takeUnless { haveStoragePermission },
                OnboardingPage.BATTERY_OPTIMIZATION.takeUnless { haveIgnoreDozePermission },
                OnboardingPage.LOCATION_PERMISSION.takeUnless { haveLocationPermission },
                OnboardingPage.NOTIFICATION_PERMISSION.takeUnless { haveNotificationPermission },
                OnboardingPage.KEY_GENERATION.takeUnless { haveConfig },
            ),
            hasStoragePermission = haveStoragePermission,
            hasIgnoreDozePermission = haveIgnoreDozePermission,
            hasLocationPermission = haveLocationPermission,
            hasNotificationPermission = haveNotificationPermission,
            hasConfig = haveConfig,
            isRunningOnTv = isRunningOnTv,
            keyGenerationStatus = getString(R.string.web_gui_creating_key),
        )

        // If we landed on the key generation page without a finished config (e.g. its coroutine was
        // interrupted by recreation), (re)start generation as moveToPage would.
        if (uiState.pages.getOrNull(uiState.currentPage) == OnboardingPage.KEY_GENERATION &&
            !uiState.keyGenerationFailed &&
            !uiState.hasConfig
        ) {
            startKeyGeneration()
        }

        setContent {
            ApplicationTheme {
                CompositionLocalProvider(
                    LocalActivityScope provides activityScope,
                ) {
                    OnboardingScreen(
                        uiState = uiState,
                        onBack = ::handleBack,
                        onContinue = ::advance,
                        onFinishOnboarding = ::startApp,
                        onGrantLocationPermission = ::requestLocationPermission,
                        onGrantNotificationPermission = ::requestNotificationPermission,
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Persist the whole UI state so it survives configuration changes.
        outState.putSerializable(STATE_UI_STATE, uiState)
    }

    /**
     * Restores the previously saved UI state, or null on the initial launch (no saved state yet).
     */
    private fun restoreUiState(savedInstanceState: Bundle?): OnboardingUiState? {
        savedInstanceState ?: return null
        return BundleCompat.getSerializable(
            savedInstanceState,
            STATE_UI_STATE,
            OnboardingUiState::class.java,
        )
    }

    override fun onResume() {
        super.onResume()
        val oldState = uiState
        refreshPermissionState()
        val currentPage = uiState.pages.getOrNull(uiState.currentPage)
        if (currentPage == OnboardingPage.STORAGE_PERMISSION &&
            !oldState.hasStoragePermission &&
            uiState.hasStoragePermission
        ) {
            advance()
        } else if (currentPage == OnboardingPage.BATTERY_OPTIMIZATION &&
            !oldState.hasIgnoreDozePermission &&
            uiState.hasIgnoreDozePermission
        ) {
            advance()
        } else if (currentPage == OnboardingPage.NOTIFICATION_PERMISSION &&
            !oldState.hasNotificationPermission &&
            uiState.hasNotificationPermission
        ) {
            advance()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (requestCode == REQUEST_WRITE_STORAGE) {
            refreshPermissionState()
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "User denied WRITE_EXTERNAL_STORAGE permission.")
            } else {
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
                Log.i(TAG, "User granted WRITE_EXTERNAL_STORAGE permission.")
                advanceIfCurrentPage(OnboardingPage.STORAGE_PERMISSION)
            }
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
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

    private fun handleBack() {
        if (uiState.keyGenerationRunning) {
            return
        }
        if (uiState.currentPage == 0) {
            finish()
            return
        }
        moveToPage(uiState.currentPage - 1)
    }

    private fun advanceIfCurrentPage(page: OnboardingPage) {
        if (uiState.pages.getOrNull(uiState.currentPage) == page) {
            advance()
        }
    }

    private fun advance() {
        val nextPage = uiState.currentPage + 1
        if (nextPage < uiState.pages.size) {
            moveToPage(nextPage)
        } else {
            startApp()
        }
    }

    private fun moveToPage(page: Int) {
        val boundedPage = page.coerceIn(0, uiState.pages.lastIndex)
        uiState = uiState.copy(currentPage = boundedPage)
        if (uiState.pages.getOrNull(boundedPage) == OnboardingPage.KEY_GENERATION) {
            startKeyGeneration()
        }
    }

    private fun refreshPermissionState() {
        uiState = uiState.copy(
            hasStoragePermission = haveStoragePermission(),
            hasIgnoreDozePermission = haveIgnoreDozePermission(),
            hasLocationPermission = haveLocationPermission(),
            hasNotificationPermission = haveNotificationPermission(),
            hasConfig = checkForParseableConfig(),
        )
    }

    private fun haveStoragePermission(): Boolean {
        return PermissionUtil.haveStoragePermission(this)
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

    private fun requestLocationPermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                fineLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                androidQLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    )
                )
            }
            else -> {
                coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun startKeyGeneration() {
        if (uiState.keyGenerationRunning || uiState.hasConfig) {
            return
        }
        uiState = uiState.copy(
            keyGenerationRunning = true,
            keyGenerationFailed = false,
            keyGenerationStatus = getString(R.string.web_gui_creating_key),
        )
        lifecycleScope.launch {
            val errorMessage = withContext(Dispatchers.IO) {
                try {
                    ConfigXml(this@OnboardingActivity).generateConfig()
                    null
                } catch (e: ExecutableNotFoundException) {
                    getString(R.string.executable_not_found, e.message)
                } catch (_: OpenConfigException) {
                    getString(R.string.config_create_failed)
                }
            }

            if (errorMessage != null) {
                uiState = uiState.copy(
                    keyGenerationRunning = false,
                    keyGenerationFailed = true,
                    keyGenerationStatus = errorMessage,
                )
                return@launch
            }

            if (!checkForParseableConfig()) {
                uiState = uiState.copy(
                    keyGenerationRunning = false,
                    keyGenerationFailed = true,
                    hasConfig = false,
                    keyGenerationStatus = getString(R.string.config_read_failed),
                )
                return@launch
            }

            uiState = uiState.copy(
                keyGenerationRunning = false,
                keyGenerationFailed = false,
                hasConfig = true,
                keyGenerationStatus = getString(R.string.key_generation_success),
            )
        }
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
