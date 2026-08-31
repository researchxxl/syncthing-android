package com.nutomic.syncthingandroid.recentchanges

import android.text.format.DateUtils
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Rule
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.util.isTelevision
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecentChangesScreen(
    changes: List<RecentChange>,
    isRefreshing: Boolean,
    showExactTimes: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleExactTimes: () -> Unit,
    onItemClick: (RecentChange) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        flingAnimationSpec = rememberSplineBasedDecay(),
    )

    // Kept alongside pull-to-refresh: on Android TV the list is driven by a D-pad, where a pull
    // gesture is not reachable.
    val refreshAction: @Composable () -> Unit = {
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.refresh),
            )
        }
    }

    Scaffold(
        modifier = if (configuration.isTelevision) {
            Modifier
        } else {
            Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
        },
        topBar = {
            if (configuration.isTelevision) {
                // Plain bar because of the limited vertical space, and no back button: TV remotes
                // have a dedicated one. Mirrors SettingsScaffold.
                TopAppBar(
                    title = { Text(stringResource(R.string.recent_changes_title)) },
                    actions = {
                        refreshAction()
                        OverflowMenu(
                            showExactTimes = showExactTimes,
                            onToggleExactTimes = onToggleExactTimes,
                        )
                    },
                )
            } else {
                LargeTopAppBar(
                    title = { Text(stringResource(R.string.recent_changes_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    },
                    actions = {
                        refreshAction()
                        OverflowMenu(
                            showExactTimes = showExactTimes,
                            onToggleExactTimes = onToggleExactTimes,
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { paddingValues ->
        val containerModifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        val listContent: @Composable BoxScope.() -> Unit = {
            if (changes.isEmpty()) {
                EmptyState(Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(changes) { change ->
                        RecentChangeItem(
                            change = change,
                            showExactTimes = showExactTimes,
                            onClick = { onItemClick(change) },
                        )
                    }
                }
            }
        }

        if (configuration.isTelevision) {
            // No pull-to-refresh on TV. The gesture is unreachable with a D-pad anyway, and the
            // indicator's nested-scroll hook also reacts to the programmatic scrolling that focus
            // movement causes: moving focus back up to the first row starts a "pull" that no gesture
            // ever releases, leaving the spinner stuck on screen. The top bar's refresh action is the
            // TV entry point.
            Box(modifier = containerModifier, content = listContent)
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = containerModifier,
                content = listContent,
            )
        }
    }
}

/** Internal rather than private so it can be previewed on its own; see RecentChangesPreviews.kt. */
@Composable
internal fun RecentChangeItem(
    change: RecentChange,
    showExactTimes: Boolean,
    onClick: () -> Unit = {},
) {
    // Deleted entries no longer exist on disk, so they are not actionable — but they must still be
    // focusable. `clickable(enabled = false)` also drops focusability, which made D-pad navigation
    // skip them: focus jumped over a run of deleted rows, and because focus movement is what scrolls
    // a LazyColumn on TV, the rows in the middle of a long run could never be brought into view.
    // Focusable-without-clickable keeps them reachable while leaving them inert, and needs an
    // explicit indication so the focus highlight is still drawn.
    val interactionSource = remember { MutableInteractionSource() }
    val rowModifier = if (change.canOpen) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
            .indication(interactionSource, LocalIndication.current)
            .focusable(interactionSource = interactionSource)
    }

    ListItem(
        overlineContent = { Text(change.folderPath) },
        headlineContent = { Text(change.filename) },
        supportingContent = { ChangeMetadata(change, showExactTimes) },
        leadingContent = {
            Icon(
                imageVector = change.icon,
                contentDescription = null,
                tint = change.iconTint,
            )
        },
        modifier = rowModifier,
    )
}

/**
 * Time and modifying device on a single line, labelled by icons rather than "Time:" / "Device:"
 * prefixes. The device name takes the remaining width and ellipsizes; the timestamp keeps its
 * intrinsic width so it never truncates first.
 */
@Composable
private fun ChangeMetadata(change: RecentChange, showExactTimes: Boolean) {
    val timeText = change.timeText(showExactTimes)
    val timeLabel = stringResource(R.string.modification_time, timeText)
    val deviceLabel = stringResource(R.string.modified_by_device, change.modifiedByName)
    val hasDevice = change.modifiedByName.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        // The icons carry the labels visually; keep the fully labelled translated strings for
        // screen readers so dropping the prefixes costs no accessibility.
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = if (hasDevice) "$timeLabel, $deviceLabel" else timeLabel
        },
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(METADATA_ICON_SIZE),
        )
        Spacer(Modifier.width(4.dp))
        Text(text = timeText, maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (hasDevice) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Outlined.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(METADATA_ICON_SIZE),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = change.modifiedByName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val METADATA_ICON_SIZE = 14.dp

/**
 * Relative ("2 minutes ago") by default, absolute when the user opts in. Relative wins for a recent-
 * activity feed because the useful question is "how fresh is this", and
 * [DateUtils.getRelativeTimeSpanString] switches to a date on its own once an entry is old enough, so
 * long-past events do not read as "3,000,000 minutes ago".
 *
 * Falls back to the timestamp exactly as Syncthing reported it if it could not be parsed.
 */
@Composable
private fun RecentChange.timeText(showExactTimes: Boolean): String {
    val millis = timeMillis ?: return rawTime
    return if (showExactTimes) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(millis))
    } else {
        // Recomputed on every recomposition, which the foreground poll triggers every few seconds,
        // so the label stays current without a dedicated ticker.
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.SECOND_IN_MILLIS,
        ).toString()
    }
}

@Composable
private fun OverflowMenu(
    showExactTimes: Boolean,
    onToggleExactTimes: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.show_exact_times)) },
                onClick = {
                    onToggleExactTimes()
                    expanded = false
                },
                leadingIcon = {
                    if (showExactTimes) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    } else {
                        // Keeps the label aligned whether or not the check is drawn.
                        Spacer(Modifier.size(CHECK_ICON_SIZE))
                    }
                },
            )
        }
    }
}

private val CHECK_ICON_SIZE = 24.dp

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Rule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.no_recent_changes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val RecentChange.icon: ImageVector
    get() = when (type) {
        ChangeType.DIR -> when (action) {
            ChangeAction.ADDED -> Icons.Outlined.CreateNewFolder
            ChangeAction.DELETED -> Icons.Outlined.FolderDelete
            ChangeAction.MODIFIED -> Icons.Outlined.Folder
            ChangeAction.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }
        ChangeType.FILE -> when (action) {
            ChangeAction.ADDED -> Icons.AutoMirrored.Outlined.NoteAdd
            ChangeAction.DELETED -> Icons.Outlined.Delete
            ChangeAction.MODIFIED -> Icons.Outlined.EditNote
            ChangeAction.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
        }
        ChangeType.UNKNOWN -> Icons.AutoMirrored.Outlined.HelpOutline
    }

private val RecentChange.iconTint: Color
    @Composable get() = when (action) {
        ChangeAction.ADDED -> MaterialTheme.colorScheme.primary
        ChangeAction.DELETED -> MaterialTheme.colorScheme.error
        ChangeAction.MODIFIED -> MaterialTheme.colorScheme.tertiary
        ChangeAction.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
