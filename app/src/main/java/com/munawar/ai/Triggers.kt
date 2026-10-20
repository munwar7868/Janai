package com.munawar.ai

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Rule(val id: Long, val type: String, val arg: String, val command: String, val enabled: Boolean)

/**
 * "When X happens, run this command" automation. Charger/headphone/screen/battery triggers work while the background
 * assistant is running (Hey Munawar ON); notification triggers need Notification access; time triggers use the alarm clock.
 */
object Triggers {
    val types = listOf(
        "charging_on" to "Charger connected", "charging_off" to "Charger disconnected", "battery_low" to "Battery low",
        "headset_on" to "Headphones plugged in", "screen_on" to "Screen turns on", "screen_off" to "Screen turns off",
        "notification" to "Notification contains…", "time" to "Every day at (HH:MM)"
    )
    private val lastFired = HashMap<Long, Long>()
    private var receiver: TriggerReceiver? = null

    fun all(): List<Rule> = try {
        val a = JSONArray(Core.prefs.rules)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Rule(o.getLong("id"), o.getString("type"), o.optString("arg"), o.getString("cmd"), o.optBoolean("on", true))
        }
    } catch (e: Exception) { emptyList() }

    private fun save(list: List<Rule>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("type", it.type).put("arg", it.arg).put("cmd", it.command).put("on", it.enabled)) }
        Core.prefs.rules = a.toString()
    }

    fun add(type: String, arg: String, command: String) {
        save(all() + Rule(System.currentTimeMillis(), type, arg.trim(), command.trim(), true))
        scheduleTimes(Core.appCtx)
    }

    fun delete(id: Long) { save(all().filter { it.id != id }); scheduleTimes(Core.appCtx) }
    fun toggle(id: Long) { save(all().map { if (it.id == id) it.copy(enabled = !it.enabled) else it }); scheduleTimes(Core.appCtx) }

    fun fire(type: String, arg: String = "") {
        val now = System.currentTimeMillis()
        for (r in all()) {
            if (!r.enabled || r.type != type) continue
            if (type == "notification" && (r.arg.isBlank() || !arg.contains(r.arg, true))) continue
            if (now - (lastFired[r.id] ?: 0L) < 10_000L) continue
            lastFired[r.id] = now
            Core.scope.launch {
                try {
                    if (r.command.trim().equals("start conversation", true)) AssistantService.send(Core.appCtx, AssistantService.A_SESSION)
                    else Commands.run(Hosts.get(), r.command, false)
                } catch (e: Exception) { /* ignore a failing rule */ }
            }
        }
    }

    fun fireById(id: Long) { all().firstOrNull { it.id == id }?.let { fire(it.type, it.arg) } }

    /** Called by the background service so the system broadcasts reach us. */
    fun register(ctx: Context) {
        if (receiver != null) return
        val rx = TriggerReceiver()
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED); addAction(Intent.ACTION_POWER_DISCONNECTED); addAction(Intent.ACTION_BATTERY_LOW)
            addAction("android.intent.action.HEADSET_PLUG"); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) ctx.applicationContext.registerReceiver(rx, f, Context.RECEIVER_NOT_EXPORTED)
        else ctx.applicationContext.registerReceiver(rx, f)
        receiver = rx
    }

    fun unregister(ctx: Context) {
        val rx = receiver ?: return
        try { ctx.applicationContext.unregisterReceiver(rx) } catch (e: Exception) {}
        receiver = null
    }

    /** Schedules the next run of every enabled daily "time" rule. */
    fun scheduleTimes(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (r in all()) {
            val pi = PendingIntent.getBroadcast(
                ctx, (r.id % 1_000_000L).toInt(), Intent(ctx, RuleAlarmReceiver::class.java).putExtra("id", r.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (r.type != "time" || !r.enabled) { am.cancel(pi); continue }
            val m = Regex("(\\d{1,2}):(\\d{2})").find(r.arg) ?: continue
            val h = m.groupValues[1].toInt(); val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) continue
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, min); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
        }
    }
}

class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            Intent.ACTION_POWER_CONNECTED -> Triggers.fire("charging_on")
            Intent.ACTION_POWER_DISCONNECTED -> Triggers.fire("charging_off")
            Intent.ACTION_BATTERY_LOW -> Triggers.fire("battery_low")
            "android.intent.action.HEADSET_PLUG" -> if (i.getIntExtra("state", 0) == 1) Triggers.fire("headset_on")
            Intent.ACTION_SCREEN_ON -> Triggers.fire("screen_on")
            Intent.ACTION_SCREEN_OFF -> Triggers.fire("screen_off")
        }
    }
}

class RuleAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Core.init(c)
        Triggers.fireById(i.getLongExtra("id", -1L))
        Triggers.scheduleTimes(c)
    }
}
