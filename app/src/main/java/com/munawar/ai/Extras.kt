package com.munawar.ai

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.CallLog
import android.provider.Settings
import android.telecom.TelecomManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** Extra assistant powers: SOS, call control, contacts, storage/health, parking, smart modes, OTP, missions, research, GitHub. */
object Extras {
    private fun ok(m: String) = Automation.Result(true, m)
    private fun bad(m: String) = Automation.Result(false, m)

    suspend fun handle(a: Host, t: String, l: String): Automation.Result? {
        val words = l.split(" ").filter { it.isNotEmpty() }

        // ---- safety
        if (l == "sos" || l.startsWith("sos ") || l == "emergency" || l.contains("emergency help") || l.contains("send sos")) return sos(a)

        // ---- calls
        if (Regex("^(?:please )?(?:answer|pick up|receive|accept)(?: the)?(?: phone)?(?: call)?$|^call (?:utha|answer)").containsMatchIn(l)) {
            return callControl(a, true)
        }
        if (l == "end call" || l == "reject call" || l == "decline call" || l.contains("hang up") || l == "cut the call" || l == "end the call") return callControl(a, false)

        Regex("(?i)(?:number of|contact of|lookup contact|find contact|contact details of)\\s+(.+)$|^(.+?)\\s+ka (?:phone )?number$").find(t)?.let { m ->
            val name = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            if (name.isNotEmpty()) return lookup(a, name)
        }

        // ---- device
        Regex("(?i)^copy(?: this| to clipboard)?[: ]+(.+)$").find(t)?.let { m ->
            val cm = a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("munawar", m.groupValues[1].trim()))
            return ok("Copied to the clipboard.")
        }
        if (l.contains("storage") && words.size <= 5 && !l.contains("setting")) return storage(a)
        if (l.contains("phone health") || l.contains("diagnose") || l == "health check") return health(a)

        // ---- my world
        if (l.contains("save parking") || l.contains("park here") || l.contains("i parked") || l.contains("parked here")) return saveParking(a)
        if (l.contains("where did i park") || l.contains("find my car") || l.contains("where is my car") || l.contains("meri gaadi kahan")) return findParking(a)
        Regex("^(driving|sleep|work|game|gaming|normal) mode(?: (on|off))?$").find(l)?.let { m -> return mode(a, m.groupValues[1], m.groupValues[2]) }

        // ---- notifications
        if (l == "clear notifications" || l == "clear all notifications" || l == "dismiss notifications") {
            if (!Perms.notifAccess(a)) return bad("Turn on Notification access first (Control Center).")
            return if (NotifListener.clearAll()) ok("Notifications cleared.") else bad("Notification access isn't connected yet. Open Control Center and re-enable it.")
        }
        if (l.contains("otp") && (l.contains("read") || l.contains("batao") || l.contains("my") || l.contains("latest") || l.contains("show") || l.contains("kya"))) return otp(a)
        if (l.contains("missed call")) return missedCalls(a)
        Regex("(?i)^reply(?: to (\\w+))?(?: saying| with| that)?\\s+(.+)$").find(t)?.let { m ->
            if (!Perms.notifAccess(a)) return null
            val r = NotifListener.reply(a, m.groupValues[1].ifEmpty { null }, m.groupValues[2].trim())
            return if (r != null) ok(r) else bad("No message notification with a reply box is waiting.")
        }

        // ---- missions
        Regex("(?i)^start mission\\s+(.+)$").find(t)?.let { return Missions.start(a, it.groupValues[1].trim()) }
        if (l == "pause mission") return Missions.pause()
        if (l == "resume mission") return Missions.resume()
        if (l == "cancel mission" || l == "stop mission") return Missions.cancel()

        // ---- research / website / github
        Regex("(?i)^(?:deep )?research\\s+(.+)$").find(t)?.let { return research(a, it.groupValues[1].trim()) }
        Regex("(?i)^(?:build|create|make)\\s+(?:a\\s+)?(?:website|web page|webpage|site)\\s*(?:for|about|of)?\\s*(.*)$").find(t)?.let {
            a.openTool("website", it.groupValues[1].trim())
            return ok("Opening the website builder in Tools…")
        }
        if (l.contains("github") && l.contains("repo")) return github(a)
        return null
    }

    // ================= safety =================
    internal fun lastLocation(ctx: Context): Pair<Double, Double>? {
        if (!Perms.hasLocation(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val best = lm.getProviders(true).mapNotNull { try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null } }.maxByOrNull { it.time }
        return if (best == null) null else Pair(best.latitude, best.longitude)
    }

    private fun sos(a: Host): Automation.Result {
        val nums = a.prefs.emergencyNumbers.split(",", ";", "\n").map { s -> s.filter { it.isDigit() || it == '+' } }.filter { it.length >= 6 }
        if (nums.isEmpty()) return bad("Add emergency contacts first: ☰ → Settings → Safety.")
        val loc = lastLocation(a)
        val link = if (loc != null) "https://maps.google.com/?q=${"%.5f".format(loc.first)},${"%.5f".format(loc.second)}" else "(location not available)"
        val msg = "SOS! I need help. My location: $link"
        a.countdown("SOS to ${nums.size} contact(s)", msg) { nums.forEach { Automation.sendSms(a, it, msg) } }
        return ok("SOS prepared for ${nums.size} contact(s): sending ${Automation.secsNote(a)}.")
    }

    // ================= calls & contacts =================
    private fun callControl(a: Host, answer: Boolean): Automation.Result {
        a.requestPerm(Manifest.permission.ANSWER_PHONE_CALLS) { granted ->
            if (!granted) { a.toast("Allow 'Phone calls' control for Munawar in Control Center first."); return@requestPerm }
            try {
                val tm = a.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                if (answer) { tm.acceptRingingCall(); a.toast("Answering the call…") }
                else a.toast(if (tm.endCall()) "Call ended." else "No active call to end.")
            } catch (e: SecurityException) { a.toast("Android blocked call control (permission missing).") }
        }
        return ok(if (answer) "Trying to answer the ringing call…" else "Trying to end the call…")
    }

    private fun lookup(a: Host, name: String): Automation.Result {
        if (!Perms.has(a, Manifest.permission.READ_CONTACTS)) {
            a.requestPerm(Manifest.permission.READ_CONTACTS) {}
            return bad("I need Contacts access. Allow it, then ask again.")
        }
        val n = Automation.findContact(a, name) ?: return bad("No contact named \"$name\".")
        return ok("$name: $n")
    }

    // ================= device =================
    private fun storage(a: Host): Automation.Result {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        fun gb(x: Long) = "%.1f GB".format(x / 1e9)
        val cache = a.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return ok("Storage: ${gb(total - free)} used of ${gb(total)} • ${gb(free)} free (${if (total > 0) free * 100 / total else 0}%%). Munawar cache: %.1f MB. To clean up, use Settings → Storage.".format(cache / 1e6))
    }

    private fun health(a: Host): Automation.Result = ok(healthReport(a))

    internal fun healthReport(a: Host): String {
        val sb = StringBuilder()
        val bi = a.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (bi != null) {
            val level = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / maxOf(1, bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
            val temp = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f
            val health = when (bi.getIntExtra(BatteryManager.EXTRA_HEALTH, 1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "good"; BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEATING"
                BatteryManager.BATTERY_HEALTH_DEAD -> "dead"; BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over-voltage"
                BatteryManager.BATTERY_HEALTH_COLD -> "cold"; else -> "unknown"
            }
            val charging = bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            sb.append("🔋 Battery $level% (${if (charging) "charging" else "not charging"}), $temp °C, health $health\n")
        }
        val mi = ActivityManager.MemoryInfo()
        (a.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        sb.append("🧠 RAM free %.1f of %.1f GB%s\n".format(mi.availMem / 1e9, mi.totalMem / 1e9, if (mi.lowMemory) " (LOW)" else ""))
        val st = StatFs(Environment.getDataDirectory().path)
        sb.append("💾 Storage free %.1f of %.1f GB\n".format(st.availableBlocksLong * st.blockSizeLong / 1e9, st.blockCountLong * st.blockSizeLong / 1e9))
        sb.append("⏱ Up for %.1f hours • %s • Android %s".format(SystemClock.elapsedRealtime() / 3.6e6, Build.MODEL, Build.VERSION.RELEASE))
        return sb.toString()
    }

    // ================= my world =================
    private fun saveParking(a: Host): Automation.Result {
        if (!Perms.hasLocation(a)) {
            a.requestAnyPerm(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) {}
            return bad("I need Location permission. Allow it, then say it again.")
        }
        val loc = lastLocation(a) ?: return bad("No location fix yet. Open Maps once and try again.")
        a.prefs.parking = "${loc.first},${loc.second},${System.currentTimeMillis()}"
        return ok("Parking spot saved (%.5f, %.5f). Ask “where did I park” later.".format(loc.first, loc.second))
    }

    private fun findParking(a: Host): Automation.Result {
        val p = a.prefs.parking.split(",")
        if (p.size < 3) return bad("No parking spot saved yet. Say “save parking” when you park.")
        val mins = (System.currentTimeMillis() - (p[2].toLongOrNull() ?: 0L)) / 60000
        return Automation.launch(a, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${p[0]},${p[1]}?q=${p[0]},${p[1]}(Parked car)")), "Showing your parked car (saved $mins min ago).")
    }

    private fun mode(a: Host, name: String, onOff: String): Automation.Result {
        val off = onOff == "off" || name == "normal"
        val nm = a.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            Automation.launch(a, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), "")
            return bad("Allow “Do Not Disturb access” for Munawar AI, then say it again.")
        }
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (off) { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL); return ok("Back to normal mode (Do Not Disturb off).") }
        when (name) {
            "sleep" -> { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE); am.setStreamVolume(AudioManager.STREAM_MUSIC, max / 5, 0) }
            "work" -> nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            else -> { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY); am.setStreamVolume(AudioManager.STREAM_MUSIC, max * 8 / 10, 0) }
        }
        if (name == "driving") Automation.tryHandle(a, "open maps")
        return ok("${name.replaceFirstChar { it.uppercase() }} mode on. Say “normal mode” to switch it off.")
    }

    // ================= notifications =================
    private fun otp(a: Host): Automation.Result {
        if (!Perms.notifAccess(a)) return bad("Turn on Notification access first (Control Center). I read OTPs only when you ask and never store them.")
        val re = Regex("\\b\\d{4,8}\\b")
        for (n in NotifListener.recent(30)) {
            if (!Regex("(?i)otp|verification|one.time|code|pin|password").containsMatchIn(n)) continue
            val m = re.find(n) ?: continue
            return ok("Latest code: ${m.value}  (from ${n.substringBefore(":")})")
        }
        return bad("No OTP notification found in the recent ones.")
    }

    private fun missedCalls(a: Host): Automation.Result {
        if (!Perms.has(a, Manifest.permission.READ_CALL_LOG)) {
            a.requestPerm(Manifest.permission.READ_CALL_LOG) {}
            return bad("I need Call log access to read missed calls. Allow it, then ask again.")
        }
        val out = ArrayList<String>()
        try {
            a.contentResolver.query(
                CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ?", arrayOf(CallLog.Calls.MISSED_TYPE.toString()), "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && out.size < 5) {
                    val who = c.getString(1).takeUnless { it.isNullOrBlank() } ?: c.getString(0) ?: "Unknown"
                    out.add("• $who — " + java.text.SimpleDateFormat("d MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(c.getLong(2))))
                }
            }
        } catch (e: Exception) { return bad("Couldn't read the call log.") }
        return if (out.isEmpty()) ok("No missed calls.") else ok("Missed calls:\n" + out.joinToString("\n"))
    }

    // ================= research / GitHub =================
    private suspend fun research(a: Host, topic: String): Automation.Result {
        val key = a.prefs.apiKey() ?: return bad("Add your Gemini API key first.")
        return try {
            val r = Gemini.generate(
                key, a.prefs.model, 0.4f,
                "You are a careful research assistant. Search the web and write a clear report: a 3-line summary, key findings as bullets, and the main sources. Be honest about uncertainty.",
                listOf(Turn("user", "Research: $topic")), true
            )
            ok(r)
        } catch (e: Exception) { bad(e.message ?: "Research failed") }
    }

    private suspend fun github(a: Host): Automation.Result {
        val user = a.prefs.githubUser
        if (user.isEmpty()) return bad("Set your GitHub username in Settings → Connectors.")
        return withContext(Dispatchers.IO) {
            try {
                val c = URL("https://api.github.com/users/$user/repos?sort=updated&per_page=8").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 15000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode != 200) return@withContext bad("GitHub replied ${c.responseCode} for user \"$user\".")
                val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
                if (arr.length() == 0) return@withContext ok("$user has no public repositories.")
                val sb = StringBuilder("Latest repositories of $user:\n")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    sb.append("• ${o.optString("name")} ★${o.optInt("stargazers_count")} ${o.optString("language").takeUnless { it == "null" } ?: ""}\n")
                }
                ok(sb.toString().trim())
            } catch (e: Exception) { bad("Couldn't reach GitHub.") }
        }
    }
}

/** Multi-step missions: Gemini plans the steps, you can cancel in the countdown, then they run one by one (pause/resume/cancel anytime). */
object Missions {
    @Volatile var running = false
    @Volatile var paused = false
    @Volatile var cancelled = false
    @Volatile var status = "No mission running"
    var listener: (() -> Unit)? = null

    private fun changed(s: String) { status = s; listener?.invoke() }
    private fun ok(m: String) = Automation.Result(true, m)

    suspend fun start(a: Host, goal: String): Automation.Result {
        if (running) return Automation.Result(false, "A mission is already running. Say “cancel mission” first.")
        val steps = try { Commands.plan(a, goal) } catch (e: Exception) { return Automation.Result(false, e.message ?: "Couldn't plan that.") }
        if (steps.isEmpty()) return Automation.Result(false, "I couldn't plan that. Try describing it differently.")
        val list = steps.mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n")
        a.countdown("Start mission: $goal", list) { run(a, steps) }
        return ok("Mission planned (${steps.size} steps), starting ${Automation.secsNote(a)}:\n$list")
    }

    private fun run(a: Host, steps: List<String>) {
        running = true; paused = false; cancelled = false
        Core.scope.launch {
            var failed = false
            for ((i, s) in steps.withIndex()) {
                while (paused && !cancelled) delay(500)
                if (cancelled) break
                changed("Step ${i + 1}/${steps.size}: $s")
                a.toast("Mission ${i + 1}/${steps.size}: $s")
                val r = try { Commands.run(Hosts.get(), s, false) } catch (e: Exception) { null }
                if (r == null || !r.ok) { changed("Stopped at step ${i + 1}: ${r?.message ?: "not understood"}"); failed = true; break }
                delay(2500)
            }
            running = false
            if (cancelled) changed("Mission cancelled") else if (!failed) changed("Mission finished ✔")
        }
    }

    fun pause(): Automation.Result { if (!running) return Automation.Result(false, "No mission is running."); paused = true; changed("Mission paused"); return ok("Mission paused.") }
    fun resume(): Automation.Result { if (!running) return Automation.Result(false, "No mission is running."); paused = false; changed("Mission resumed"); return ok("Mission resumed.") }
    fun cancel(): Automation.Result { if (!running) return Automation.Result(false, "No mission is running."); cancelled = true; paused = false; return ok("Cancelling the mission…") }
}
