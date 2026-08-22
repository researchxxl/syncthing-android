package com.nutomic.syncthingandroid.webgui

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nutomic.syncthingandroid.R

/**
 * What the Web GUI screen shows on top of the WebView. The WebView itself is always present; these
 * states only control the overlay, so a failed load can never leave the user staring at a spinner.
 */
internal sealed interface WebGuiLoadState {
    data object Loading : WebGuiLoadState

    data object Loaded : WebGuiLoadState

    /** [detail] is a short technical cause shown under the generic message, if one is known. */
    data class Failed(val detail: String?) : WebGuiLoadState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WebGuiScreen(
    state: WebGuiLoadState,
    onNavigateBack: () -> Unit,
    onRetry: () -> Unit,
    /**
     * Changing this discards the current WebView and asks [webViewFactory] for a new one. Needed
     * after the render process dies, because that WebView can never be used again.
     */
    webViewKey: Any,
    webViewFactory: (Context) -> WebView,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.web_gui_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(Modifier.fillMaxSize()) {
                key(webViewKey) {
                    AndroidView(
                        factory = webViewFactory,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                when (state) {
                    WebGuiLoadState.Loaded -> Unit
                    WebGuiLoadState.Loading -> LoadingOverlay()
                    is WebGuiLoadState.Failed -> FailedOverlay(state.detail, onRetry)
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay() {
    Overlay {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.web_gui_loading),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedOverlay(detail: String?, onRetry: () -> Unit) {
    Overlay {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.web_gui_load_failed),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                text = detail,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onRetry,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

/**
 * Opaque full-size cover so the WebView underneath (blank, or showing its own error page) never
 * shows through.
 */
@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            content()
        }
    }
}
