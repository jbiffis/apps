package dev.jbiffis.caddie.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * User-tunable drawn-map rendering parameters. Backed by SharedPreferences and
 * held in Compose state so changes update the map live.
 *
 * - [maxObjects]  total tuft/tree budget across the whole course view.
 * - [perWood]     approx. max tufts per wood polygon (bigger = denser woods).
 * - [treeSizeStop] 1..10 size steps; the app shipped at step 3 (x1.30).
 */
object MapSettings {
    const val TREE_STOPS = 10
    const val DEFAULT_MAX = 14000
    const val DEFAULT_PER_WOOD = 4500
    const val DEFAULT_TREE_STOP = 3

    var maxObjects by mutableIntStateOf(DEFAULT_MAX)
        private set
    var perWood by mutableIntStateOf(DEFAULT_PER_WOOD)
        private set
    var treeSizeStop by mutableIntStateOf(DEFAULT_TREE_STOP)
        private set

    /** Draw multiplier for the current size step. Step 3 == the 1.30x we shipped. */
    val treeScale: Float get() = treeScaleFor(treeSizeStop)
    fun treeScaleFor(stop: Int): Float = 1.0f + (stop - 1) * 0.15f

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences("map_settings", Context.MODE_PRIVATE)
        prefs = p
        maxObjects = p.getInt("max_objects", DEFAULT_MAX)
        perWood = p.getInt("per_wood", DEFAULT_PER_WOOD)
        treeSizeStop = p.getInt("tree_stop", DEFAULT_TREE_STOP)
    }

    fun updateMaxObjects(v: Int) {
        maxObjects = v.coerceIn(2000, 30000)
        prefs?.edit()?.putInt("max_objects", maxObjects)?.apply()
    }

    fun updatePerWood(v: Int) {
        perWood = v.coerceIn(500, 10000)
        prefs?.edit()?.putInt("per_wood", perWood)?.apply()
    }

    fun updateTreeSize(v: Int) {
        treeSizeStop = v.coerceIn(1, TREE_STOPS)
        prefs?.edit()?.putInt("tree_stop", treeSizeStop)?.apply()
    }
}
