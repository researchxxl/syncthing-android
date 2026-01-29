package com.nutomic.syncthingandroid.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.compose.preference.MapPreferences
import me.zhanghai.compose.preference.Preferences

/**
 * The library assumes we will have a global preference flow and use it everywhere
 * so they use GlobalScope for the flow collection and writing to disk.
 * But since we are doing scoped migration, we need to scope it to the settings activity for now
 * and also implement flow update on external updates.
 * The libraries flow also calls SharedPreferences.clear before writing everything back.
 * These quirks may pose a problem when the app is in the hybrid phase (i.e using SharedPreferences
 * directly and preference flow).
 * We may use the libraries implementation once we can use the preference flow everywhere (i.e.
 * every ui element is reactive with compose).
 */

fun createPreferenceFlow(
    sharedPreferences: SharedPreferences,
    scope: CoroutineScope,
): MutableStateFlow<Preferences> =
    MutableStateFlow(sharedPreferences.preferences).also {

        // Sync from Flow -> SharedPreferences
        scope.launch(Dispatchers.Main.immediate) {
            it.drop(1).collectLatest { newPrefs ->
                withContext(Dispatchers.IO) {
                    val newMap = newPrefs.asMap()
                    val diskMap = sharedPreferences.all

                    sharedPreferences.edit {
                        newMap.forEach { (key, newValue) ->
                            val diskValue = diskMap[key]
                            if (newValue != diskValue) {
                                putAny(key, newValue)
                            }
                        }
                    }
                }
            }
        }

        // Sync from SharedPreferences -> Flow (External/Background updates)
        scope.launch(Dispatchers.Main.immediate) {
            sharedPreferences.observeChanges().collect { diskMap ->
                val currentFlowMap = it.value.asMap()
                if (diskMap != currentFlowMap) {
                    it.value = MapPreferences(diskMap)
                }
            }
        }
    }

private val SharedPreferences.preferences: Preferences
    get() = @Suppress("UNCHECKED_CAST") MapPreferences(all as Map<String, Any>)

private fun SharedPreferences.observeChanges() = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        @Suppress("UNCHECKED_CAST")
        trySend(prefs.all as Map<String, Any>)
    }
    registerOnSharedPreferenceChangeListener(listener)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
private fun SharedPreferences.Editor.putAny(key: String, value: Any?) {
    when (value) {
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is String -> putString(key, value)
        is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(key, value as Set<String>)
        null -> remove(key)
    }
}
