package com.nutomic.syncthingandroid.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.isTelevision

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(
    title: String,
    description: String? = null,
    content: @Composable (ColumnScope.() -> Unit) = {},
) {
    val configuration = LocalConfiguration.current
    val navigator = LocalSettingsNavigator.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec = rememberSplineBasedDecay()
    )
    val collapsedFraction = scrollBehavior.state.collapsedFraction

    // Alpha: 1.0 -> 0.0 (over 0.0 to 0.25 collapsedFraction)
    val subtitleAlpha = (1f - (collapsedFraction / 0.25f)).coerceIn(0f, 1f)

    // Height: 1.0 -> 0.0 (over 0.25 to 1 collapsedFraction)
    // This starts shrinking only after the text is fully transparent for smooth transition
    val heightScale = (1f - ((collapsedFraction - 0.25f) / 0.75f)).coerceIn(0f, 1f)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (configuration.isTelevision) {
                // Use normal top app bar because of low vertical space
                TopAppBar(
                    title = { Text(title) }
                    // TVs remotes have dedicated back buttons,
                    // so material guidelines suggest to not show the back button
                )
            } else {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(title)
                            if (!description.isNullOrBlank() && heightScale > 0f) {
                                Column(
                                    modifier = Modifier.alpha(subtitleAlpha)
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints)
                                            val currentHeight = (placeable.height * heightScale).toInt()
                                            layout(placeable.width, currentHeight) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                ) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { navigator.navigateBack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .padding(paddingValues),
                content = content,
            )
        },
    )
}
