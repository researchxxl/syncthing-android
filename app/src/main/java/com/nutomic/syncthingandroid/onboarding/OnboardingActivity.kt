package com.nutomic.syncthingandroid.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.SyncthingApp
import com.nutomic.syncthingandroid.activities.LogActivity
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
    val userSkippedIgnoreDozePermission: Boolean = false,
    val keyGenerationRunning: Boolean = false,
    val keyGenerationFailed: Boolean = false,
    val keyGenerationStatus: String = "",
)

class OnboardingActivity : ThemedAppCompatActivity() {

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val REQUEST_WRITE_STORAGE = 143
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

        val shouldSkipToMain = haveStoragePermission && haveNotificationPermission && haveConfig
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

        uiState = OnboardingUiState(
            pages = onboardingPages,
            hasStoragePermission = haveStoragePermission,
            hasIgnoreDozePermission = haveIgnoreDozePermission,
            hasLocationPermission = haveLocationPermission,
            hasNotificationPermission = haveNotificationPermission,
            hasConfig = haveConfig,
            isRunningOnTv = isRunningOnTv,
            keyGenerationStatus = getString(R.string.web_gui_creating_key),
        )

        setContent {
            ApplicationTheme {
                CompositionLocalProvider(
                    LocalActivityScope provides activityScope,
                ) {
                    OnboardingScreen(
                        uiState = uiState,
                        onBack = ::handleBack,
                        onContinue = ::handleContinue,
                        onGrantStoragePermission = ::requestStoragePermission,
                        onGrantIgnoreDozePermission = ::requestIgnoreDozePermission,
                        onGrantLocationPermission = ::requestLocationPermission,
                        onGrantNotificationPermission = ::requestNotificationPermission,
                    )
                }
            }
        }
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

    private fun handleContinue(page: OnboardingPage) {
        when (page) {
            OnboardingPage.WELCOME,
            OnboardingPage.LOCATION_PERMISSION -> advance()
            OnboardingPage.STORAGE_PERMISSION -> {
                refreshPermissionState()
                if (uiState.hasStoragePermission) {
                    advance()
                } else {
                    Toast.makeText(this, R.string.toast_write_storage_permission_required, Toast.LENGTH_LONG).show()
                }
            }
            OnboardingPage.BATTERY_OPTIMIZATION -> {
                refreshPermissionState()
                if (uiState.hasIgnoreDozePermission ||
                    uiState.userSkippedIgnoreDozePermission ||
                    uiState.isRunningOnTv
                ) {
                    advance()
                } else {
                    showSkipIgnoreDozeConfirmation()
                }
            }
            OnboardingPage.NOTIFICATION_PERMISSION -> {
                refreshPermissionState()
                if (uiState.hasNotificationPermission) {
                    advance()
                } else {
                    Toast.makeText(this, R.string.toast_notification_permission_required, Toast.LENGTH_LONG).show()
                }
            }
            OnboardingPage.KEY_GENERATION -> {
                refreshPermissionState()
                if (uiState.keyGenerationFailed) {
                    openLogAndFinishOnboarding()
                } else if (uiState.hasConfig) {
                    startApp()
                }
            }
        }
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

    private fun requestStoragePermission() {
        PermissionUtil.requestStoragePermission(this, REQUEST_WRITE_STORAGE)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreDozePermission() {
        var intentFailed = false
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        try {
            val componentName: ComponentName? = intent.resolveActivity(packageManager)
            if (componentName != null) {
                val className = componentName.className
                if (!className.equals("com.android.tv.settings.EmptyStubActivity", ignoreCase = true)) {
                    startActivity(intent)
                    return
                }
                intentFailed = true
            } else {
                Log.w(TAG, "Request ignore battery optimizations not supported")
                intentFailed = true
            }
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Request ignore battery optimizations not supported", e)
            intentFailed = true
        }
        if (intentFailed) {
            Toast.makeText(this, R.string.dialog_disable_battery_optimizations_not_supported, Toast.LENGTH_LONG).show()
        }
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

    private fun showSkipIgnoreDozeConfirmation() {
        AlertDialog.Builder(this)
            .setMessage(R.string.dialog_confirm_skip_ignore_doze_permission)
            .setPositiveButton(R.string.yes) { _, _ ->
                uiState = uiState.copy(userSkippedIgnoreDozePermission = true)
                advanceIfCurrentPage(OnboardingPage.BATTERY_OPTIMIZATION)
            }
            .setNegativeButton(R.string.no) { _, _ ->
                uiState = uiState.copy(userSkippedIgnoreDozePermission = false)
            }
            .show()
    }

    private fun openLogAndFinishOnboarding() {
        val intent = Intent(this, LogActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
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
