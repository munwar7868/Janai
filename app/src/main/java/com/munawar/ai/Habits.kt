package com.munawar.ai

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Learns which commands you use at which hour (stored only on this phone) and suggests them again around that time. */
object Habits {
    fun record(cmd: String) {
        val c = cmd.trim().lowercase()
        if (c.length < 3 || c.length > 60) return
        try {
            val o = JSONObject(Core.prefs.habits)
            val arr = o.optJSONArray(c) ?: JSONArray().also { a -> for (i in 0 until 24) a.put(0); o.put(c, a) }
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            arr.put(h, arr.optInt(h, 0) + 1)
            // keep the file small: drop the rarest entries beyond 60
            if (o.length() > 60) {
                val keys = o.keys().asSequence().toList()
                val weakest = keys.minByOrNull { k -> (0 until 24).sumOf { i -> o.getJSONArray(k).optInt(i, 0) } }
                if (weakest != null) o.remove(weakest)
            }
            Core.prefs.habits = o.toString()
        } catch (e: Exception) { /* ignore */ }
    }

    /** Commands you used most around the current hour (±1), most frequent first. */
    fun suggest(max: Int = 3): List<String> {
        return try {
            val o = JSONObject(Core.prefs.habits)
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val scored = ArrayList<Pair<String, Int>>()
            for (k in o.keys()) {
                val a = o.getJSONArray(k)
                val s = a.optInt(h, 0) * 2 + a.optInt((h + 23) % 24, 0) + a.optInt((h + 1) % 24, 0)
                if (s >= 2) scored.add(Pair(k, s))
            }
            scored.sortedByDescending { it.second }.take(max).map { it.first }
        } catch (e: Exception) { emptyList() }
    }

    fun clear() { Core.prefs.habits = "{}" }
}
