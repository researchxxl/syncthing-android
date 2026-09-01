package com.nutomic.syncthingandroid.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation3.runtime.EntryProviderScope
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.service.Constants
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.util.CertificateValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory

/**
 * Upper bound on a picked certificate or key file. Generous for PEM — a full chain is a few KiB — while
 * keeping both files small enough to sit in the saved-instance-state Bundle, which travels over a
 * Binder transaction with a hard size limit shared process-wide.
 */
private const val MAX_PEM_BYTES = 64 * 1024

/** Relative to [R.string.wiki_url], matching how the onboarding pages link into the wiki. */
private const val CUSTOM_CERT_WIKI_PATH =
    "/tips-and-tricks/Use-a-custom-HTTPS-certificate-for-the-Web-GUI.md"

fun EntryProviderScope<SettingsRoute>.settingsCustomCertificateEntry() {
    entry<SettingsRoute.CustomCertificate> {
        SettingsCustomCertificateScreen()
    }
}

@Composable
fun SettingsCustomCertificateScreen() {
    val context = LocalContext.current
    val navigator = LocalSettingsNavigator.current
    val stService = LocalSyncthingService.current

    var certBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var keyBytes by rememberSaveable { mutableStateOf<ByteArray?>(null) }
    var certName by rememberSaveable { mutableStateOf<String?>(null) }
    var keyName by rememberSaveable { mutableStateOf<String?>(null) }
    var applyFailure by rememberSaveable(stateSaver = ApplyFailureSaver) { mutableStateOf(null) }
    // Deliberately not saved: both are recomputed by the LaunchedEffects below, from the picked bytes
    // and from the certificate on disk respectively.
    var validation by remember { mutableStateOf<CertificateValidator.ValidationResult?>(null) }
    var currentInfo by remember { mutableStateOf<CertificateValidator.CertInfo?>(null) }
    // Not saved on purpose: an apply in flight cannot survive the composition being torn down, since
    // its result callback writes to it. Restoring "applying" would strand the spinner forever.
    var applying by remember { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentInfo = withContext(Dispatchers.IO) {
            try {
                val file = Constants.getHttpsCertFile(context)
                if (file.exists()) CertificateValidator.describe(file.readBytes()) else null
            } catch (_: Exception) {
                null
            }
        }
    }

    LaunchedEffect(certBytes, keyBytes) {
        val c = certBytes
        val k = keyBytes
        validation = if (c != null && k != null)
            withContext(Dispatchers.IO) { CertificateValidator.validate(c, k) }
        else
            null
    }

    val certPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        readPicked(context, uri)?.let { (name, bytes) ->
            certName = name; certBytes = bytes; applyFailure = null
        }
    }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        readPicked(context, uri)?.let { (name, bytes) ->
            keyName = name; keyBytes = bytes; applyFailure = null
        }
    }

    val canApply = validation?.canApply == true && stService != null && !applying

    SettingsScaffold(title = stringResource(R.string.webui_custom_cert_title)) {
        item {
            PreferenceCategory(
                title = { Text(stringResource(R.string.preference_category_explanation)) }
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.custom_cert_explanation)) }
            )
        }

        item { CurrentCertificateCard(currentInfo) }

        item {
            Preference(
                title = { Text(stringResource(R.string.custom_cert_select_cert)) },
                summary = { Text(certName ?: stringResource(R.string.custom_cert_not_selected)) },
                enabled = !applying,
                onClick = { certPicker.launch(arrayOf("*/*")) },
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.custom_cert_select_key)) },
                summary = { Text(keyName ?: stringResource(R.string.custom_cert_not_selected)) },
                enabled = !applying,
                onClick = { keyPicker.launch(arrayOf("*/*")) },
            )
        }

        validation?.let { result ->
            val parseError = result.parseError
            if (parseError != null) {
                item { CheckRow(CertificateValidator.Status.FAIL, parseError, null) }
            } else {
                result.checks.forEach { check ->
                    item {
                        CheckRow(check.status, stringResource(checkTitle(check.check)), check.detail)
                    }
                }
            }
        }

        applyFailure?.let { failure ->
            item { ApplyFailureCard(applyFailureMessage(failure)) }
        }

        // Offered whenever the user is stuck — a rejected file or a failed apply. Both are cases where
        // the wiki page (root CA installation, full chain, matching key) is the actual answer.
        val blockedByChecks = validation?.let { result ->
            result.parseError != null ||
                    result.checks.any { it.status == CertificateValidator.Status.FAIL }
        } == true
        if (blockedByChecks || applyFailure != null) {
            item { HelpRow() }
        }

        item {
            Preference(
                title = {
                    Button(
                        onClick = {
                            val result = validation
                            val service = stService
                            if (result != null && result.canApply && service != null && !applying) {
                                applying = true
                                applyFailure = null
                                service.replaceHttpsCertificate(result.certPem, result.keyPem) { outcome, detail ->
                                    applying = false
                                    applyFailure = onApplyOutcome(context, navigator, outcome, detail)
                                }
                            }
                        },
                        enabled = canApply,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.custom_cert_apply))
                    }
                },
                summary = {
                    Box(
                        modifier = Modifier.height(24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AnimatedVisibility(
                            visible = applying,
                            enter = fadeIn(animationSpec = tween(durationMillis = 120)),
                            exit = fadeOut(animationSpec = tween(durationMillis = 120)),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.custom_cert_applying),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            )
        }
        item {
            Preference(
                title = { Text(stringResource(R.string.custom_cert_reset_title)) },
                summary = { Text(stringResource(R.string.custom_cert_reset_summary)) },
                enabled = stService != null && !applying,
                onClick = { showResetDialog = true },
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.custom_cert_reset_title)) },
            text = { Text(stringResource(R.string.custom_cert_reset_question)) },
            confirmButton = {
                TextButton(onClick = {
                    showResetDialog = false
                    val service = stService
                    if (service != null && !applying) {
                        applying = true
                        applyFailure = null
                        service.resetHttpsCertificate { outcome, detail ->
                            applying = false
                            applyFailure = if (isSuccess(outcome)) {
                                Toast.makeText(
                                    context.applicationContext,
                                    R.string.custom_cert_reset_done,
                                    Toast.LENGTH_LONG,
                                ).show()
                                navigator.navigateUp()
                                null
                            } else {
                                ApplyFailure(
                                    outcome ?: SyncthingService.HttpsCertReplaceResult.FAILED,
                                    detail,
                                )
                            }
                        }
                    }
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CurrentCertificateCard(info: CertificateValidator.CertInfo?) {
    PreferenceCategory(
        title = { Text(stringResource(R.string.custom_cert_current_title)) },
    )
    Preference(
        title = {
            if (info == null) {
                Text(stringResource(R.string.custom_cert_current_none))
            } else {
                Text(stringResource(R.string.custom_cert_subject, info.subject))
                Text(stringResource(R.string.custom_cert_issuer, info.issuer))
                Text(stringResource(R.string.custom_cert_expires, info.notAfter))
                Text(stringResource(if (info.selfSigned) R.string.custom_cert_self_signed else R.string.custom_cert_ca_signed))
            }
        },
    )
}

@Composable
private fun CheckRow(status: CertificateValidator.Status, title: String, detail: String?) {
    val (icon, tint) = when (status) {
        CertificateValidator.Status.PASS -> Icons.Filled.CheckCircle to Color(0xFF2E7D32)
        CertificateValidator.Status.WARN -> Icons.Filled.Warning to Color(0xFFF9A825)
        CertificateValidator.Status.FAIL -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun checkTitle(check: CertificateValidator.Check): Int = when (check) {
    CertificateValidator.Check.CHAIN -> R.string.cert_check_chain
    CertificateValidator.Check.TRUST -> R.string.cert_check_trust
    CertificateValidator.Check.VALIDITY -> R.string.cert_check_validity
    CertificateValidator.Check.KEY -> R.string.cert_check_key
}

/**
 * A failed apply, kept on screen rather than shown as a toast. The reason matters here — "Syncthing
 * generated its own certificate instead" tells the user something actionable that a disappearing
 * "Could not apply the certificate" does not — and a toast cannot carry the help link.
 */
private data class ApplyFailure(
    val outcome: SyncthingService.HttpsCertReplaceResult,
    val detail: String?,
)

/**
 * Stores an [ApplyFailure] as two plain strings so it survives a configuration change. The enum is
 * saved by name and looked up defensively — a name that no longer exists after an app update restores
 * as "no failure" rather than throwing during state restoration.
 */
private val ApplyFailureSaver = listSaver<ApplyFailure?, String>(
    save = { failure ->
        failure?.let { listOf(it.outcome.name, it.detail.orEmpty()) } ?: emptyList()
    },
    restore = { saved ->
        saved.takeIf { it.size == 2 }?.let { (name, detail) ->
            runCatching { SyncthingService.HttpsCertReplaceResult.valueOf(name) }
                .getOrNull()
                ?.let { ApplyFailure(it, detail.ifEmpty { null }) }
        }
    },
)

@Composable
private fun applyFailureMessage(failure: ApplyFailure): String = when (failure.outcome) {
    SyncthingService.HttpsCertReplaceResult.FAILED_CERT_REJECTED ->
        stringResource(R.string.custom_cert_failed_rejected)
    SyncthingService.HttpsCertReplaceResult.FAILED_NOT_ONLINE ->
        stringResource(R.string.custom_cert_failed_not_online)
    else -> stringResource(R.string.custom_cert_failed, failure.detail ?: "")
}

private fun isSuccess(outcome: SyncthingService.HttpsCertReplaceResult?): Boolean =
    outcome == SyncthingService.HttpsCertReplaceResult.SUCCESS ||
            outcome == SyncthingService.HttpsCertReplaceResult.SUCCESS_PENDING_START

/**
 * Reports the outcome of an apply. Success leaves the screen; failure stays put and is returned so it
 * can be rendered inline, because the reason is worth reading and acting on.
 */
private fun onApplyOutcome(
    context: Context,
    navigator: Navigator<SettingsRoute>,
    outcome: SyncthingService.HttpsCertReplaceResult?,
    detail: String?,
): ApplyFailure? {
    when (outcome) {
        SyncthingService.HttpsCertReplaceResult.SUCCESS ->
            Toast.makeText(context.applicationContext, R.string.custom_cert_applied, Toast.LENGTH_LONG).show()
        SyncthingService.HttpsCertReplaceResult.SUCCESS_PENDING_START ->
            Toast.makeText(context.applicationContext, R.string.custom_cert_applied_pending, Toast.LENGTH_LONG).show()
        else -> return ApplyFailure(outcome ?: SyncthingService.HttpsCertReplaceResult.FAILED, detail)
    }
    navigator.navigateUp()
    return null
}

/**
 * The outcome of an apply, which is a different kind of thing from the validation checks above it —
 * hence a card rather than another [CheckRow]. It also has to carry more text than a check detail, so
 * it needs the room.
 */
@Composable
private fun ApplyFailureCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.custom_cert_apply_failed_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * Links to the wiki page for this feature, following the `wiki_url` + relative path convention.
 *
 * Deliberately an ordinary preference row rather than a button: it is a secondary action sitting just
 * above "Apply certificate", and a second filled button competes with — and when Apply is disabled,
 * visually outranks — the primary one.
 */
@Composable
private fun HelpRow() {
    val context = LocalContext.current
    val url = stringResource(R.string.wiki_url) + CUSTOM_CERT_WIKI_PATH
    Preference(
        title = { Text(stringResource(R.string.custom_cert_get_help)) },
        summary = { Text(stringResource(R.string.custom_cert_get_help_summary)) },
        icon = {
            Icon(
                Icons.AutoMirrored.Filled.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (e: ActivityNotFoundException) {
                toast(context, R.string.custom_cert_help_unavailable)
            }
        },
    )
}

private fun readPicked(context: Context, uri: Uri?): Pair<String, ByteArray>? {
    if (uri == null) {
        return null
    }
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null) {
            toast(context, R.string.custom_cert_read_failed)
            null
        } else if (bytes.size > MAX_PEM_BYTES) {
            toast(context, R.string.custom_cert_file_too_large)
            null
        } else {
            (queryDisplayName(context, uri) ?: "selected file") to bytes
        }
    } catch (e: Exception) {
        toast(context, R.string.custom_cert_read_failed)
        null
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else {
                null
            }
        }
}

private fun toast(context: Context, resId: Int) {
    Toast.makeText(context.applicationContext, resId, Toast.LENGTH_LONG).show()
}
