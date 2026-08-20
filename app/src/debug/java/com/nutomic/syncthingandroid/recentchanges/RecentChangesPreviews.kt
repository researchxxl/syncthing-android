package com.nutomic.syncthingandroid.recentchanges

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Compose previews for the Recent changes screen, living in the **debug** source set so neither they
 * nor their sample data can reach a release build (release does not run R8, so anything in `main`
 * ships). `internal` members of `main` are visible here because the debug source set is compiled into
 * the same module.
 *
 * These exist so the screen can be reviewed without a running Syncthing instance. They replaced an
 * earlier approach that injected fake `DiskEvent`s behind the global `Constants.ENABLE_TEST_DATA` flag,
 * which had to be remembered and switched back off before every build. Previews build [RecentChange]
 * directly instead, so no fake events, timestamp parsing or device-name resolution are involved.
 *
 * Note: relative timestamps are computed from the real clock at render time, so re-rendering a preview
 * keeps the labels in the band each row is meant to demonstrate.
 *
 * **Do not add `showSystemUi = true`.** It draws a status and navigation bar but the preview renderer
 * does not supply the matching [androidx.compose.foundation.layout.WindowInsets], so `Scaffold` gets
 * zero insets and the content renders *underneath* the painted bars — the clock overlaps the back
 * button and the gesture bar sits on top of the last row. That is purely a preview artifact: at runtime
 * `enableEdgeToEdge()` plus real insets pads it correctly. `device = ...` gives the same realistic
 * dimensions without the misleading decoration.
 */
private fun change(
    filename: String,
    type: ChangeType = ChangeType.FILE,
    action: ChangeAction = ChangeAction.MODIFIED,
    folderPath: String = "[Photos]/Camera",
    device: String = "Living room NAS",
    age: Duration = 20.minutes,
) = RecentChange(
    filename = filename,
    folderPath = folderPath,
    modifiedByName = device,
    timeMillis = System.currentTimeMillis() - age.inWholeMilliseconds,
    rawTime = "",
    type = type,
    action = action,
    folderId = "preview-folder",
    rawPath = filename,
)

/** Every icon and tint combination, including the unknown-action and unknown-type fallbacks. */
private val variantRows = listOf(
    change("Trip to the coast", ChangeType.DIR, ChangeAction.ADDED),
    change("Old screenshots", ChangeType.DIR, ChangeAction.DELETED),
    change("Camera", ChangeType.DIR, ChangeAction.MODIFIED),
    change("IMG_20260726_101500.jpg", ChangeType.FILE, ChangeAction.ADDED),
    change("notes-draft.txt", ChangeType.FILE, ChangeAction.DELETED),
    change("budget.ods", ChangeType.FILE, ChangeAction.MODIFIED),
    change("renamed-file.txt", ChangeType.FILE, ChangeAction.UNKNOWN),
    change("renamed-dir", ChangeType.DIR, ChangeAction.UNKNOWN),
    change("link-to-elsewhere", ChangeType.UNKNOWN, ChangeAction.MODIFIED),
)

/** Layout and text edge cases: truncation, wrapping, and the missing-device branch. */
private val edgeCaseRows = listOf(
    change("readme.md", folderPath = "[Documents]/"),
    change(
        "quarterly-report-final-v3-actually-final-this-time.ods",
        folderPath = "[Documents]/Work/Finance/Quarterly/2026/Q3/Drafts",
    ),
    change("no-device.txt", device = ""),
    change("from-here.txt", device = "This device"),
    change("shared.txt", device = "Workshop laptop in the upstairs office that nobody renamed"),
    change("日本語のファイル-🎉.txt"),
)

/** One row per relative-time band, from seconds through to where the label becomes a date. */
private val timeBandRows = listOf(
    change("just-now.txt", age = 3.seconds),
    change("seconds.txt", age = 45.seconds),
    change("one-minute.txt", age = 90.seconds),
    change("minutes.txt", age = 20.minutes),
    change("one-hour.txt", age = 1.hours),
    change("hours.txt", age = 5.hours),
    change("a-day.txt", age = 26.hours),
    change("days.txt", age = 3.days),
    change("over-a-week.txt", age = 10.days),
    change("months.txt", age = 60.days),
    // Negative age: a peer with a skewed clock can report a change ahead of local time.
    change("future.txt", age = -(4.minutes)),
)

private val screenRows = variantRows + edgeCaseRows

// ---------------------------------------------------------------------------------------------
// Component previews
// ---------------------------------------------------------------------------------------------

@Composable
private fun RowList(rows: List<RecentChange>, showExactTimes: Boolean = false) {
    ApplicationTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { RecentChangeItem(change = it, showExactTimes = showExactTimes) }
            }
        }
    }
}

@Preview(name = "Rows · icon + tint variants", heightDp = 900)
@Composable
private fun RowVariantsPreview() = RowList(variantRows)

@Preview(name = "Rows · icon + tint variants (dark)", heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RowVariantsDarkPreview() = RowList(variantRows)

@Preview(name = "Rows · text + layout edge cases", heightDp = 700)
@Composable
private fun RowEdgeCasesPreview() = RowList(edgeCaseRows)

@Preview(name = "Rows · relative time bands", heightDp = 1100)
@Composable
private fun RowTimeBandsPreview() = RowList(timeBandRows)

@Preview(name = "Rows · exact times", heightDp = 1100)
@Composable
private fun RowExactTimesPreview() = RowList(timeBandRows, showExactTimes = true)

/** Narrow screen: checks the metadata row ellipsizes the device name and not the timestamp. */
@Preview(name = "Rows · narrow screen", widthDp = 320, heightDp = 700)
@Composable
private fun RowNarrowPreview() = RowList(edgeCaseRows)

/** Large font scale, the usual source of clipped list rows. */
@Preview(name = "Rows · large font", fontScale = 1.6f, heightDp = 900)
@Composable
private fun RowLargeFontPreview() = RowList(variantRows.take(4))

// ---------------------------------------------------------------------------------------------
// Screen previews
// ---------------------------------------------------------------------------------------------

@Composable
private fun Screen(
    changes: List<RecentChange> = screenRows,
    isRefreshing: Boolean = false,
    showExactTimes: Boolean = false,
) {
    ApplicationTheme {
        RecentChangesScreen(
            changes = changes,
            isRefreshing = isRefreshing,
            showExactTimes = showExactTimes,
            onNavigateBack = {},
            onRefresh = {},
            onToggleExactTimes = {},
            onItemClick = {},
        )
    }
}

@Preview(name = "Screen · populated", device = Devices.PIXEL_5)
@Composable
private fun ScreenPreview() = Screen()

@Preview(name = "Screen · populated (dark)", device = Devices.PIXEL_5, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenDarkPreview() = Screen()

@Preview(name = "Screen · exact times", device = Devices.PIXEL_5)
@Composable
private fun ScreenExactTimesPreview() = Screen(showExactTimes = true)

@Preview(name = "Screen · empty", device = Devices.PIXEL_5)
@Composable
private fun ScreenEmptyPreview() = Screen(changes = emptyList())

@Preview(name = "Screen · refreshing", device = Devices.PIXEL_5)
@Composable
private fun ScreenRefreshingPreview() = Screen(isRefreshing = true)

/**
 * The TV layout: a plain [androidx.compose.material3.TopAppBar] with no back button and no
 * pull-to-refresh. `uiMode` is what `Configuration.isTelevision` keys off, so setting it here exercises
 * the real branch rather than an approximation.
 */
@Preview(
    name = "Screen · Android TV",
    device = Devices.TV_1080p,
    uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ScreenTelevisionPreview() = Screen()

/** Landscape, where the collapsing large app bar has the least room. */
@Preview(name = "Screen · landscape", widthDp = 800, heightDp = 400)
@Composable
private fun ScreenLandscapePreview() = Screen()

// ---------------------------------------------------------------------------------------------
// Locale previews
// ---------------------------------------------------------------------------------------------

/**
 * German: the app ships a `values-de` translation, and German words are longer than their English
 * equivalents, which is where list rows tend to start clipping.
 *
 * Both halves localise here: the app's own strings come from `values-de`, and the relative timestamps
 * render as "Vor 20 Minuten" — so the preview renderer does switch `Locale.getDefault()` along with the
 * resource configuration, not only the latter. Matches the on-device behaviour verified with the system
 * locale set to `de-DE` (see .claude/compose-migration/recentchanges-api-findings.md).
 */
@Preview(name = "Screen · German", locale = "de", device = Devices.PIXEL_5)
@Composable
private fun ScreenGermanPreview() = Screen()

@Preview(name = "Rows · German", locale = "de", heightDp = 700)
@Composable
private fun RowGermanPreview() = RowList(edgeCaseRows)

/**
 * Right-to-left. The app ships no Arabic translation, so the strings stay English — the layout is the
 * point. Worth checking: the back arrow and the "note add"/"help"/"rule" glyphs are
 * `Icons.AutoMirrored.*` and should flip, the `ListItem` leading icon should move to the right edge,
 * and the metadata row should mirror so the clock/time sits on the right.
 */
@Preview(name = "Screen · RTL (ar)", locale = "ar", device = Devices.PIXEL_5)
@Composable
private fun ScreenRtlPreview() = Screen()

@Preview(name = "Rows · RTL (ar)", locale = "ar", heightDp = 700)
@Composable
private fun RowRtlPreview() = RowList(variantRows)
