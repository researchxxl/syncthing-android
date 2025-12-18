package com.nutomic.syncthingandroid.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.theme.ApplicationTheme

class DeviceIdDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val deviceName = args.getString(ARG_DEVICE_NAME)!!
        val isCurrentDevice = args.getBoolean(ARG_IS_CURRENT_DEVICE)
        val deviceId = args.getString(ARG_DEVICE_ID)!!
        val qrCode = BundleCompat.getParcelable(args, ARG_QR_CODE, Bitmap::class.java)!!

        return Dialog(requireContext()).apply {
            setContentView(
                ComposeView(context).apply {
                    setContent {
                        DeviceIdDialog(
                            onDismiss = { dismiss() },
                            deviceName,
                            isCurrentDevice,
                        ) {
                            if (isLandscape()) {
                                LandscapeDialogContent(
                                    deviceId,
                                    qrCode,
                                    onCopy = { copyDeviceId(deviceId) },
                                    onShare = { shareDeviceId(deviceId) },
                                )
                            } else {
                                PortraitDialogContent(
                                    deviceId,
                                    qrCode,
                                    onCopy = { copyDeviceId(deviceId) },
                                    onShare = { shareDeviceId(deviceId) },
                                )
                            }
                        }
                    }
                }
            )
        }
    }

    private fun copyDeviceId(id: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        val clip = ClipData.newPlainText(getString(R.string.device_id), id)
        clipboard.setPrimaryClip(clip)

        // Android 13+ shows a system confirmation automatically
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                requireContext(),
                R.string.device_id_copied_to_clipboard,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun shareDeviceId(deviceId: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, deviceId)
        }

        startActivity(
            Intent.createChooser(shareIntent, getString(R.string.share_device_id_chooser))
        )
    }


    companion object {
        private const val ARG_DEVICE_NAME = "device_name"
        private const val ARG_DEVICE_ID = "device_id"
        private const val ARG_QR_CODE = "qr_code"
        private const val ARG_IS_CURRENT_DEVICE = "is_current_device"

        fun show(
            fm: FragmentManager,
            deviceName: String,
            deviceId: String,
            qrCode: Bitmap,
            isCurrentDevice: Boolean = false,
        ) {
            DeviceIdDialogFragment().apply {
                arguments = bundleOf(
                    ARG_DEVICE_NAME to deviceName,
                    ARG_DEVICE_ID to deviceId,
                    ARG_QR_CODE to qrCode,
                    ARG_IS_CURRENT_DEVICE to isCurrentDevice,
                )
            }.show(fm, "DeviceIdDialog")
        }
    }
}


@Composable
fun DeviceIdDialog(
    onDismiss: () -> Unit,
    deviceName: String,
    isCurrentDevice: Boolean = false,
    content: @Composable () -> Unit,
) {
    ApplicationTheme {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.let { if (!isLandscape()) it.widthIn(max = 460.dp).fillMaxWidth(0.9f) else it },
            onDismissRequest = { onDismiss() },
            title = {
                if (isLandscape()) {
                    Row(Modifier.fillMaxWidth()) {
                        DialogTitle(deviceName, isCurrentDevice, Modifier.weight(1f))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.close_device_id))
                        }
                    }
                } else {
                    DialogTitle(deviceName, isCurrentDevice)
                }
            },
            text = content,
            confirmButton = {
                if (!isLandscape()) {
                    TextButton(onClick = { onDismiss() }) {
                        Text(stringResource(R.string.finish))
                    }
                }
            },
        )
    }
}

@Composable
fun isLandscape(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
fun DialogTitle(
    deviceName: String,
    isCurrentDevice: Boolean = false,
    modifier: Modifier = Modifier
) {
    val thisDeviceText = stringResource(R.string.this_device)

    Column(modifier) {
        Text(stringResource(R.string.device_id))
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = deviceName
                    if (isCurrentDevice) {
                        stateDescription = thisDeviceText
                    }
                }
        ) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isCurrentDevice) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleMedium,
                    softWrap = false,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = thisDeviceText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun PortraitDialogContent(
    deviceId: String,
    qrCode: Bitmap,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val qrMaxHeight = minOf(screenHeightDp * 0.35f, 280.dp)

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.heightIn(min = 160.dp, max = qrMaxHeight).fillMaxWidth()
        ) {
            Image(
                bitmap = qrCode.asImageBitmap(),
                contentDescription = stringResource(R.string.device_id),
                Modifier.fillMaxHeight()
            )
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(deviceId, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onCopy, Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.copy)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.copy))
            }
            FilledTonalButton(onShare, Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share_title)
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.share_title))
            }
        }
    }
}

@Composable
fun LandscapeDialogContent(
    deviceId: String,
    qrCode: Bitmap,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(max = 250.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxHeight().weight(1f)
        ) {
            Image(
                bitmap = qrCode.asImageBitmap(),
                contentDescription = stringResource(R.string.device_id),
                Modifier.fillMaxHeight()
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Text(deviceId, modifier = Modifier.padding(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onCopy, Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.copy))
                }
                FilledTonalButton(onShare, Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = stringResource(R.string.share_title)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share_title))
                }
            }
        }
    }
}
