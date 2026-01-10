package com.nutomic.syncthingandroid.settings

import android.content.SharedPreferences
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.MapPreferences
import me.zhanghai.compose.preference.Preferences
import java.lang.IllegalArgumentException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator

/**
 * NOTE: The implementation in this version of the library doesn't support long values
 * So i have copied the implementation here and added the line with `putLong`.
 * We may remove this file entirely and use the createPreferenceFlow from the library
 * once they add support for the long values
 */
fun createPreferenceFlow(
    sharedPreferences: SharedPreferences
): MutableStateFlow<Preferences> =
    MutableStateFlow(sharedPreferences.preferences).also {
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.Main.immediate) {
            it.drop(1).collect { sharedPreferences.preferences = it }
        }
    }

private var SharedPreferences.preferences: Preferences
    get() = @Suppress("UNCHECKED_CAST") MapPreferences(all as Map<String, Any>)
    set(value) {
        edit {
            clear()
            for ((key, mapValue) in value.asMap()) {
                when (mapValue) {
                    is Boolean -> putBoolean(key, mapValue)
                    is Int -> putInt(key, mapValue)
                    is Float -> putFloat(key, mapValue)
                    is Long -> putLong(key, mapValue)
                    is String -> putString(key, mapValue)
                    is Set<*> ->
                        @Suppress("UNCHECKED_CAST") putStringSet(key, mapValue as Set<String>)
                    else -> throw IllegalArgumentException("Unsupported type for value $mapValue")
                }
            }
        }
    }

private inline fun SharedPreferences.edit(action: SharedPreferences.Editor.() -> Unit) {
    edit().apply {
        action()
        apply()
    }
}
