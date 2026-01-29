package com.nutomic.syncthingandroid.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope

import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.theme.ApplicationTheme

fun EntryProviderScope<SettingsRoute>.licensesEntry() {
    entry<SettingsRoute.Licenses> {
        LicensesScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen() {
    val navigator = LocalSettingsNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    ApplicationTheme {
        val resources = LocalResources.current

        // Read the libraries content outside of produceLibraries to avoid the lint warning
        val librariesContent = remember {
            resources.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() }
        }
        val libraries by produceLibraries(librariesContent)

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = { Text(stringResource(id = R.string.open_source_licenses_title)) },
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
        ) { paddingValues ->
            LibrariesContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                libraries = libraries
            )
        }
    }
}
