package com.hybrid87sg.bindertracker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * View choices that belong to this phone only, never sent online.
 *
 * A shared binder is the same for everyone (name, colour, cards), but how
 * each person *looks* at it is their own business. Today that's the
 * Missing cards "Main set / Master set" choice, kept per binder.
 *
 * Saved in a small settings file on the phone ("SharedPreferences"), with
 * keys like "master_<binder id>". No database change needed.
 */
class ViewPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("view", Context.MODE_PRIVATE)

    private val _masterSets = MutableStateFlow(load())

    /** Binder ID -> true for "Master set", false for "Main set". Binders not listed haven't been chosen yet. */
    val masterSets: StateFlow<Map<String, Boolean>> = _masterSets.asStateFlow()

    fun setMasterSet(binderId: String, master: Boolean) {
        prefs.edit().putBoolean(KEY + binderId, master).apply()
        _masterSets.value = _masterSets.value + (binderId to master)
    }

    /**
     * Gives [binder] this phone's own Main/Master choice. The first time a
     * binder is seen, whatever it had before this update is kept as this
     * phone's choice, so nobody's view jumps.
     */
    fun personal(binder: Binder, chosen: Map<String, Boolean> = _masterSets.value): Binder {
        val mine = chosen[binder.id]
        if (mine == null) {
            setMasterSet(binder.id, binder.countSecretRares)
            return binder
        }
        return if (mine == binder.countSecretRares) binder else binder.copy(countSecretRares = mine)
    }

    private fun load(): Map<String, Boolean> = prefs.all.mapNotNull { (k, v) ->
        if (k.startsWith(KEY) && v is Boolean) k.removePrefix(KEY) to v else null
    }.toMap()

    private companion object {
        const val KEY = "master_"
    }
}
