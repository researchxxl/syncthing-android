package com.nutomic.syncthingandroid.recentchanges

import android.content.ComponentName
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.R
import com.nutomic.syncthingandroid.activities.SyncthingActivity
import com.nutomic.syncthingandroid.model.Device
import com.nutomic.syncthingandroid.model.DiskEvent
import com.nutomic.syncthingandroid.service.Constants.PREF_SHOW_EXACT_TIMES
import com.nutomic.syncthingandroid.service.RestApi
import com.nutomic.syncthingandroid.service.SyncthingService
import com.nutomic.syncthingandroid.service.SyncthingServiceBinder
import com.nutomic.syncthingandroid.theme.ApplicationTheme
import com.nutomic.syncthingandroid.util.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shows the list of recent changes to files and folders reported by the local Syncthing instance.
 */
class RecentChangesActivity : SyncthingActivity(), SyncthingService.OnServiceStateChangeListener {

    private var changes by mutableStateOf<List<RecentChange>>(emptyList())

    /** Drives the pull-to-refresh indicator; only set for refreshes the user asked for. */
    private var isRefreshing by mutableStateOf(false)

    /** Re-entrancy guard, separate from [isRefreshing] so background polls stay invisible. */
    private var refreshInFlight = false

    private var showExactTimes by mutableStateOf(false)
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Same store the Dagger-provided SharedPreferences wraps, read directly so this screen does
        // not need to be added to the injector for one boolean.
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        showExactTimes = preferences.getBoolean(PREF_SHOW_EXACT_TIMES, false)

        setContent {
            ApplicationTheme {
                RecentChangesScreen(
                    changes = changes,
                    isRefreshing = isRefreshing,
                    showExactTimes = showExactTimes,
                    onNavigateBack = { finish() },
                    onRefresh = { refresh() },
                    onToggleExactTimes = ::toggleExactTimes,
                    onItemClick = ::openChange,
                )
            }
        }

        startBackgroundPolling()
    }

    /**
     * Keeps the list current while the screen is in the foreground, without ever showing the refresh
     * indicator — new changes just appear.
     *
     * This polls on an interval rather than holding a long-poll open (which is how the web GUI stays
     * live). Two things make a subscription impractical here: `RestApi.getDiskEvents` has no `since`
     * parameter, so a loop would re-fetch the same buffered events and spin as fast as the server
     * could answer; and Volley's `DefaultRetryPolicy` uses a 5s socket timeout, so it abandons and
     * retries a blocked long-poll several times per call — the server explicitly notes such requests
     * "should not be retried". Short `timeout=1` queries on a timer are far more predictable.
     *
     * `repeatOnLifecycle(STARTED)` stops polling when the screen is not visible.
     */
    private fun startBackgroundPolling() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(POLL_INTERVAL)
                    refresh(showProgress = false)
                }
            }
        }
    }

    override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
        super.onServiceConnected(componentName, binder)
        (binder as SyncthingServiceBinder).service.registerOnServiceStateChangeListener(this)
    }

    override fun onServiceStateChange(newState: SyncthingService.State) {
        Log.v(TAG, "onServiceStateChange($newState)")
        if (newState == SyncthingService.State.ACTIVE) {
            refresh()
        }
    }

    override fun onDestroy() {
        service?.unregisterOnServiceStateChangeListener(this)
        super.onDestroy()
    }

    /**
     * @param showProgress whether to drive the refresh indicator. False for background polls, so
     *   they update the list silently.
     */
    private fun refresh(showProgress: Boolean = true) {
        if (refreshInFlight) return
        val api = activeApi ?: return

        refreshInFlight = true
        if (showProgress) isRefreshing = true
        // Scoped to the lifecycle, so a pending request is cancelled if the screen goes away.
        lifecycleScope.launch {
            try {
                // Both of these are cached config reads, not network calls.
                val devices = api.getDevices(true)
                val localDeviceId = api.localDevice.deviceID
                Log.v(TAG, "Querying disk events")
                api.awaitDiskEvents(DISK_EVENT_LIMIT)?.let { diskEvents ->
                    changes = diskEvents
                        .toRecentChanges(devices, localDeviceId, getString(R.string.this_device))
                }
            } finally {
                refreshInFlight = false
                isRefreshing = false
            }
        }
    }

    private fun openChange(change: RecentChange) {
        if (!change.canOpen) return
        Log.v(TAG, "User clicked item with title '${change.rawPath}'")
        val folder = activeApi?.getFolderByID(change.folderId)
        if (folder == null) {
            Log.e(TAG, "openChange: folder unavailable for id=[${change.folderId}]")
            return
        }
        val fullPath = folder.path + File.separator + change.rawPath
        when (change.type) {
            ChangeType.DIR -> FileUtils.openFolder(this, fullPath)
            ChangeType.FILE -> FileUtils.openFile(this, fullPath)
            ChangeType.UNKNOWN -> Log.e(TAG, "openChange: unknown type for path=[${change.rawPath}]")
        }
    }

    private fun toggleExactTimes() {
        showExactTimes = !showExactTimes
        preferences.edit { putBoolean(PREF_SHOW_EXACT_TIMES, showExactTimes) }
    }

    /** The [RestApi], but only once the service is actually running. */
    private val activeApi: RestApi?
        get() = api?.takeIf { service?.currentState == SyncthingService.State.ACTIVE }

    companion object {
        private const val TAG = "RecentChangesActivity"
        private const val DISK_EVENT_LIMIT = 100

        /** How often the foreground screen re-queries. Tune freely; each query is a local call. */
        private val POLL_INTERVAL = 5.seconds
    }
}

private val REQUEST_TIMEOUT = 30.seconds

/**
 * Suspending wrapper around [RestApi.getDiskEvents].
 *
 * Returns null if the request does not finish within [REQUEST_TIMEOUT]. The timeout is not optional:
 * `ApiRequest.connect` passes an error listener that swallows failures (`error -> {}`), so a failed
 * request never invokes the success callback and would otherwise leave this coroutine suspended
 * forever.
 *
 * ### Why the continuation is held behind an [AtomicReference]
 *
 * Cancelling this coroutine (e.g. the user presses back, destroying the Activity and its
 * `lifecycleScope`) does **not** cancel the underlying HTTP request. `ApiRequest.connect` adds the
 * request to a `static` Volley queue and keeps no handle to it, and `getDiskEvents` returns nothing
 * we could cancel with. The request therefore runs to completion and its result is discarded.
 *
 * That matters because Volley keeps the success listener alive until the request finishes, and a
 * listener capturing the continuation directly would retain this chain:
 *
 * ```
 * static sVolleyQueue -> StringRequest -> listener -> continuation -> coroutine
 *     -> lifecycleScope -> Activity (and its whole Compose tree)
 * ```
 *
 * With `DefaultRetryPolicy(5000, 5, DEFAULT_BACKOFF_MULT)` that is up to ~30s (5s × 6 attempts) of a
 * destroyed Activity being unreachable-but-retained. Clearing the reference on cancellation breaks
 * the chain immediately, so the Activity is collectable as soon as it is destroyed. The `getAndSet`
 * also guarantees the continuation is resumed at most once, whichever thread delivers first.
 *
 * **This only fixes the retention, not the wasted work.** Genuinely aborting the request would mean
 * plumbing a Volley request tag through `ApiRequest`/`GetRequest`/`RestApi` so the queue could
 * `cancelAll(tag)` — shared code used by every screen — and even then Volley's cancel only suppresses
 * *delivery*, it does not abort an in-flight socket. Since this is a loopback call to the local
 * Syncthing process it normally returns in milliseconds, so that was judged not worth the blast
 * radius. Revisit if these requests ever go off-device.
 */
private suspend fun RestApi.awaitDiskEvents(limit: Int): List<DiskEvent>? =
    withTimeoutOrNull(REQUEST_TIMEOUT) {
        suspendCancellableCoroutine { continuation ->
            val pending = AtomicReference(continuation)
            continuation.invokeOnCancellation { pending.set(null) }
            getDiskEvents(limit) { diskEvents -> pending.getAndSet(null)?.resume(diskEvents) }
        }
    }

enum class ChangeType {
    FILE, DIR, UNKNOWN;

    companion object {
        fun of(raw: String?) = when (raw) {
            "file" -> FILE
            "dir" -> DIR
            else -> UNKNOWN
        }
    }
}

enum class ChangeAction {
    ADDED, DELETED, MODIFIED, UNKNOWN;

    companion object {
        fun of(raw: String?) = when (raw) {
            "added" -> ADDED
            "deleted" -> DELETED
            "modified" -> MODIFIED
            else -> UNKNOWN
        }
    }
}

/** Immutable UI model for a single row of [RecentChangesScreen]. */
@Immutable
data class RecentChange(
    val filename: String,
    val folderPath: String,
    val modifiedByName: String,
    /** Epoch millis of the change, or null if the reported timestamp could not be parsed. */
    val timeMillis: Long?,
    /** The timestamp exactly as Syncthing reported it; shown when [timeMillis] is null. */
    val rawTime: String,
    val type: ChangeType,
    val action: ChangeAction,
    /** Needed to resolve the on-disk location when the row is opened. */
    val folderId: String,
    val rawPath: String,
) {
    /** Deleted entries are gone from disk, and an unknown type has no viewer to launch. */
    val canOpen: Boolean get() = action != ChangeAction.DELETED && type != ChangeType.UNKNOWN
}

private fun List<DiskEvent>.toRecentChanges(
    devices: List<Device>,
    localDeviceId: String,
    thisDeviceLabel: String,
): List<RecentChange> {
    // Reused across the whole list rather than rebuilt per row. SimpleDateFormat is not thread-safe,
    // which is fine: this only ever runs on the main dispatcher.
    val timestampParser = SimpleDateFormat(RFC3339_PATTERN, Locale.US)
    return withoutUselessEvents().map { event ->
        RecentChange(
            filename = fileNameOf(event.data.path),
            folderPath = "[${event.data.label}]${File.separator}${parentPathOf(event.data.path)}",
            modifiedByName = resolveDeviceName(event.data.modifiedBy, devices, localDeviceId, thisDeviceLabel),
            timeMillis = timestampParser.parseEpochMillisOrNull(event.time),
            rawTime = event.time,
            type = ChangeType.of(event.data.type),
            action = ChangeAction.of(event.data.action),
            folderId = event.data.folderID,
            rawPath = event.data.path,
        )
    }
}

private const val RFC3339_PATTERN = "yyyy-MM-dd'T'HH:mm:ssZ"

/**
 * Parses a Syncthing timestamp (RFC 3339, e.g. `2018-10-29T15:18:52.6183215+01:00`) to epoch millis,
 * or null if it does not parse.
 *
 * `SimpleDateFormat` is used rather than `java.time` because minSdk is 23 and core library desugaring
 * is not enabled, so `OffsetDateTime` would be unavailable on API 23–25. It needs the input normalised
 * first: the fractional seconds dropped (it cannot handle 7–9 digits), and the offset reduced from
 * `+01:00` to the RFC 822 `+0100` that the `Z` pattern expects.
 */
private fun SimpleDateFormat.parseEpochMillisOrNull(raw: String): Long? {
    if (raw.isEmpty()) return null
    val normalised = raw
        .replace(FRACTIONAL_SECONDS, "")
        .let { if (it.endsWith("Z")) it.dropLast(1) + "+0000" else it }
        .replace(OFFSET_WITH_COLON, "$1$2")
    return runCatching { parse(normalised)?.time }.getOrNull()
}

private val FRACTIONAL_SECONDS = Regex("\\.\\d+")
private val OFFSET_WITH_COLON = Regex("([+-]\\d{2}):(\\d{2})$")

/**
 * Drops disk events that are not useful to display:
 *  - events carrying no data,
 *  - an "added" entry whose exact path is deleted by a later event,
 *  - any entry inside a directory that a later event deletes.
 *
 * Later means "has a higher event id". Deletions are indexed up front so this stays linear in the
 * number of events (times path depth) instead of comparing every event against every other.
 *
 * This replaced a three-pass O(n²) implementation in the old view-based screen. Equivalence to that
 * implementation was established with a differential fuzz — 400k random cases over two corpora, the
 * second one adversarial (double slashes, leading slashes, and sibling-prefix traps such as `ab` vs `a`
 * and `a/bc` vs `a/b`), with zero mismatches. That harness was a throwaway script and is not in the
 * repo, so the guarantee rests on this note.
 *
 * `internal` and pure on purpose: **this is the first thing worth a real unit test** if a test source
 * set is ever added to the project. It is the only non-obvious logic on this screen.
 */
internal fun List<DiskEvent>.withoutUselessEvents(): List<DiskEvent> {
    val valid = filter { it.data != null }

    val lastDeletedAt = HashMap<String, Long>()
    val lastDirDeletedAt = HashMap<String, Long>()
    for (event in valid) {
        if (ChangeAction.of(event.data.action) != ChangeAction.DELETED) continue
        lastDeletedAt.keepHighest(event.data.path, event.id)
        if (ChangeType.of(event.data.type) == ChangeType.DIR) {
            lastDirDeletedAt.keepHighest(event.data.path, event.id)
        }
    }
    if (lastDeletedAt.isEmpty()) return valid

    fun deletedAfter(map: Map<String, Long>, path: String, id: Long) = (map[path] ?: -1L) > id

    return valid.filterNot { event ->
        val path = event.data.path
        val supersededByDelete = ChangeAction.of(event.data.action) == ChangeAction.ADDED &&
            deletedAfter(lastDeletedAt, path, event.id)

        supersededByDelete ||
            path.ancestorPaths().any { deletedAfter(lastDirDeletedAt, it, event.id) }
    }
}

/**
 * Stores [value] under [key] unless a higher value is already recorded.
 *
 * Hand-rolled because the obvious alternatives — `merge`, `compute`, `putIfAbsent`, `getOrDefault` —
 * are all Java 8 default methods that require API 24, and minSdk is 23.
 */
private fun MutableMap<String, Long>.keepHighest(key: String, value: Long) {
    val current = this[key]
    if (current == null || value > current) {
        this[key] = value
    }
}

/** Ancestor directory paths, longest first: "a/b/c.txt" -> "a/b", "a". */
private fun String.ancestorPaths(): Sequence<String> = sequence {
    var slash = lastIndexOf('/')
    while (slash > 0) {
        yield(substring(0, slash))
        slash = lastIndexOf('/', slash - 1)
    }
}

/** Last path segment, e.g. "a/b/c.txt" -> "c.txt". */
private fun fileNameOf(path: String): String {
    // Encode "#" so Uri.parse handles it. See syncthing-android/651.
    val uriParseInput = path.replace("#", Uri.encode("#"))
    return uriParseInput.toUri().lastPathSegment.orEmpty()
}

/** Parent directory of a path, e.g. "a/b/c.txt" -> "a/b", "c.txt" -> "". */
private fun parentPathOf(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")

/**
 * Resolves the partial device ID reported by Syncthing to a readable device name, or
 * [thisDeviceLabel] for the local device. Returns the input unchanged if it is blank or matches no
 * known device.
 */
private fun resolveDeviceName(
    modifiedBy: String,
    devices: List<Device>,
    localDeviceId: String,
    thisDeviceLabel: String,
): String {
    if (modifiedBy.isEmpty()) return modifiedBy
    val device = devices.firstOrNull { it.deviceID.startsWith(modifiedBy) } ?: return modifiedBy
    return if (device.deviceID == localDeviceId) thisDeviceLabel else device.displayName
}
