package com.munawar.ai

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.launch

interface Screen {
    val view: View
    fun onShow() {}
    fun onHide() {}
}

class MainActivity : Activity() {
    val prefs: Prefs get() = Core.prefs
    val memory: MemoryStore get() = Core.memory
    val notes: NotesStore get() = Core.notes
    val speaker: Speaker get() = Core.speaker
    val chatEngine: Engine get() = Core.chatEngine
    val codeEngine: Engine get() = Core.codeEngine
    val callEngine: Engine get() = Core.callEngine
    val scope get() = Core.scope
    val orbs = ArrayList<OrbView>()
    val host: ActivityHost by lazy { ActivityHost(this) }

    private lateinit var aurora: AuroraView
    private lateinit var container: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var subtitle: TextView
    private lateinit var pill: TextView
    private lateinit var bottomNav: LinearLayout
    private lateinit var centerBtn: TextView
    private lateinit var edgeView: EdgeLightView
    private lateinit var scrim: View
    private lateinit var drawer: LinearLayout
    private var drawerW = 0
    private var setup: SetupScreen? = null
    private val screens = LinkedHashMap<String, Screen>()
    private val navLabels = LinkedHashMap<String, TextView>()
    private var current: String? = null
    private var nextCode = 1000
    private var navAnim: ValueAnimator? = null
    private val centerBg = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val permCbs = HashMap<Int, (Boolean) -> Unit>()
    private val resultCbs = HashMap<Int, (Int, Intent?) -> Unit>()

    private val menu = listOf(
        "Home" to "🏠  Home (talk)", "Chat" to "💬  Chat", "Phone" to "⌨  Commands & missions", "Tools" to "🛠  Tools & images",
        "Notes" to "📝  Notes & tasks", "History" to "🕘  History", "Triggers" to "⚡  Triggers & habits", "Code" to "💻  Code assistant",
        "Memory" to "🧠  Memory", "Control" to "🚀  Control center", "Settings" to "⚙  Settings", "About" to "🔒  About & safety"
    )

    private fun glassCircle(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Theme.CARD); setStroke(dp(2), Theme.PURPLE) }
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.BG
        window.navigationBarColor = Theme.BG
        Core.init(this)

        val rootFrame = FrameLayout(this)
        aurora = AuroraView(this).also { it.lite = prefs.liteMode }
        rootFrame.addView(aurora, FrameLayout.LayoutParams(MATCH, MATCH))

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- top bar: menu • title • status pill • settings
        topBar = hbox().apply { setPadding(dp(10), dp(6), dp(10), dp(4)) }
        topBar.addView(glassCircle("☰") { openMenu() }, lp(dp(44), dp(44), m = 2))
        val titles = vbox()
        titles.addView(RainbowText(this, "Munawar AI", 21f))
        subtitle = label("Your Smart AI Assistant", 10.5f, Theme.MUTED)
        titles.addView(subtitle)
        topBar.addView(titles, lp(0, WRAP, 1f, 4))
        pill = TextView(this).apply {
            textSize = 11.5f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { setColor(Theme.CARD); cornerRadius = dp(22).toFloat(); setStroke(dp(2), Theme.CYAN) }
            setOnClickListener { setWake(!prefs.wakeEnabled) }
        }
        topBar.addView(pill, lp(WRAP, WRAP, m = 2))
        topBar.addView(glassCircle("⚙") { show("Settings") }, lp(dp(44), dp(44), m = 2))
        column.addView(topBar)

        container = FrameLayout(this)
        column.addView(container, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // ---- bottom bar: Home • Chat • (crown = talk) • History • Settings
        bottomNav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(6))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#00000000"), Color.parseColor("#F0090618")))
        }
        bottomNav.addView(navItem("Home", "🏠", "Home"), lp(0, WRAP, 1f))
        bottomNav.addView(navItem("Chat", "💬", "Chat"), lp(0, WRAP, 1f))
        centerBtn = TextView(this).apply {
            text = "👑"; textSize = 26f; gravity = Gravity.CENTER; background = centerBg
            setOnClickListener { if (AssistantBus.sessionOn) AssistantService.send(this@MainActivity, AssistantService.A_STOP_SESSION) else startCall() }
        }
        centerBg.setColor(Theme.CARD); centerBg.setStroke(dp(3), Theme.CYAN)
        bottomNav.addView(centerBtn, lp(dp(62), dp(62), m = 4))
        bottomNav.addView(navItem("History", "🕘", "History"), lp(0, WRAP, 1f))
        bottomNav.addView(navItem("Settings", "⚙", "Settings"), lp(0, WRAP, 1f))
        column.addView(bottomNav)
        rootFrame.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))

        // ---- slide-in menu (☰)
        scrim = View(this).apply { setBackgroundColor(0x99000000.toInt()); visibility = View.GONE; setOnClickListener { closeDrawer() } }
        rootFrame.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        drawerW = dp(290)
        drawer = vbox().apply {
            setBackgroundColor(Theme.BG); visibility = View.GONE; setPadding(dp(14), dp(24), dp(14), dp(14)); isClickable = true
        }
        drawer.addView(ImageView(this).apply { setImageResource(R.drawable.orb_logo) }, lp(dp(90), dp(90)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        drawer.addView(RainbowText(this, "MUNAWAR AI", 20f).apply { gravity = Gravity.CENTER })
        drawer.addView(label("Created by ${Persona.CREATOR}", 11f, Theme.MUTED).apply { gravity = Gravity.CENTER })
        val menuScroll = vbox()
        menu.forEach { (key, text) ->
            menuScroll.addView(button(text, Theme.CARD) { closeDrawer(); show(key) }.apply { gravity = Gravity.START or Gravity.CENTER_VERTICAL }, lp(MATCH, WRAP, m = 3))
        }
        drawer.addView(scroll(menuScroll), LinearLayout.LayoutParams(MATCH, 0, 1f))
        rootFrame.addView(drawer, FrameLayout.LayoutParams(drawerW, MATCH))

        edgeView = EdgeLightView(this).apply { visibility = View.GONE; isClickable = false; lightOn = prefs.edgeLight; thicknessDp = prefs.edgeThickness; speed = prefs.edgeSpeed }
        rootFrame.addView(edgeView, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(rootFrame)

        refreshWakeUi()
        if (!prefs.onboarded) showSetup() else show("Home")
        handleIntent(intent)
    }

    private fun navItem(key: String, icon: String, name: String): View {
        val box = vbox().apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(2)) }
        box.addView(TextView(this).apply { text = icon; textSize = 21f; gravity = Gravity.CENTER })
        val l = label(name, 10.5f, Theme.MUTED).apply { gravity = Gravity.CENTER }
        navLabels[key] = l
        box.addView(l)
        box.setOnClickListener { show(key) }
        return box
    }

    // ---------- menu ----------
    fun openMenu() {
        scrim.visibility = View.VISIBLE; scrim.alpha = 0f; scrim.animate().alpha(1f).setDuration(160).start()
        drawer.visibility = View.VISIBLE; drawer.translationX = -drawerW.toFloat()
        drawer.animate().translationX(0f).setDuration(200).start()
    }

    private fun closeDrawer() {
        if (drawer.visibility != View.VISIBLE) return
        drawer.animate().translationX(-drawerW.toFloat()).setDuration(160).withEndAction { drawer.visibility = View.GONE }.start()
        scrim.animate().alpha(0f).setDuration(160).withEndAction { scrim.visibility = View.GONE }.start()
    }

    // ---------- first-run setup ----------
    private fun showSetup() {
        topBar.visibility = View.GONE; bottomNav.visibility = View.GONE
        val s = SetupScreen(this)
        setup = s
        container.removeAllViews()
        container.addView(s.view, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    fun finishSetup() {
        prefs.onboarded = true
        setup = null
        topBar.visibility = View.VISIBLE; bottomNav.visibility = View.VISIBLE
        show("Home")
    }

    // ---------- conversation / orb ----------
    /** Start the background Gemini conversation (keeps running when the app is minimised or closed). */
    fun startCall() {
        show("Home")
        requestPerm(Manifest.permission.RECORD_AUDIO) { ok ->
            if (!ok) { toast("Microphone permission is needed to talk to Munawar."); return@requestPerm }
            val go: () -> Unit = {
                AssistantService.send(this, AssistantService.A_SESSION)
                if (!Perms.overlay(this) && !prefs.overlayAsked) { prefs.overlayAsked = true; overlayDialog() }
            }
            if (Build.VERSION.SDK_INT >= 33 && !Perms.has(this, Manifest.permission.POST_NOTIFICATIONS)) requestPerm(Manifest.permission.POST_NOTIFICATIONS) { go() } else go()
        }
    }

    private fun overlayDialog() {
        AlertDialog.Builder(this).setTitle("Allow “Display over other apps”?")
            .setMessage("With it, the colourful screen-edge light shows over every app, the cancel-card for calls/messages appears over other apps, and Munawar can open apps (YouTube, WhatsApp…) while you are in another app.")
            .setPositiveButton("Allow") { _, _ -> try { startActivity(Perms.overlayIntent(this)) } catch (e: Exception) {} }
            .setNegativeButton("Not now", null).show()
    }

    fun orbState(s: OrbView.S) { orbs.forEach { it.state = s } }
    fun orbLevel(l: Float) { orbs.forEach { it.level = l } }
    private fun orbStandby(on: Boolean) { orbs.forEach { it.standby = on } }

    /** Speak a chat reply with the orb in SPEAKING state (not while a background conversation is running). */
    fun say(text: String) {
        if (!prefs.speak || AssistantBus.sessionOn) return
        orbState(OrbView.S.SPEAKING)
        speaker.speak(text) { if (!AssistantBus.sessionOn) orbState(OrbView.S.IDLE) }
    }

    fun askChat(text: String) { show("Chat"); (screens["Chat"] as? ChatScreen)?.submit(text) }

    /** Runs a phone command from a suggestion chip; falls back to chat if it isn't a command. */
    fun runCommand(cmd: String) {
        scope.launch {
            val r = try { Commands.run(host, cmd) } catch (e: Exception) { null }
            if (r == null) askChat(cmd) else toast(r.message.take(240))
        }
    }

    fun openTool(id: String, prompt: String) { show("Tools"); (screens["Tools"] as? ToolsScreen)?.selectAndRun(id, prompt) }
    fun generateImage(prompt: String) = openTool("image", prompt)

    // ---------- wake word ("Hey Munawar", runs in the background service) ----------
    fun refreshWakeUi() {
        orbStandby(prefs.wakeEnabled)
        updatePill()
        (screens["Control"] as? ControlScreen)?.onShow()
    }

    private fun updatePill() {
        val (t, c) = when {
            AssistantBus.sessionOn -> Pair("● In conversation", Theme.PINK)
            prefs.wakeEnabled -> Pair("● Always Listening 🎙", Color.parseColor("#4ADE80"))
            else -> Pair("○ Wake word off", Theme.MUTED)
        }
        if (pill.text != t) pill.text = t
        pill.setTextColor(c)
    }

    fun setWake(on: Boolean) {
        if (!on) {
            prefs.wakeEnabled = false
            AssistantService.send(this, AssistantService.A_WAKE_OFF); refreshWakeUi(); return
        }
        requestPerm(Manifest.permission.RECORD_AUDIO) { ok ->
            if (!ok) { toast("Microphone permission is needed for “Hey Munawar”."); return@requestPerm }
            val go: () -> Unit = {
                prefs.wakeEnabled = true
                AssistantService.send(this, AssistantService.A_WAKE_ON); refreshWakeUi()
                if (!Perms.batteryIgnored(this)) toast("Tip: ☰ → Control → Battery → Unrestricted, so Android doesn't stop background listening.")
            }
            if (Build.VERSION.SDK_INT >= 33) requestPerm(Manifest.permission.POST_NOTIFICATIONS) { go() } else go()
        }
    }

    // ---------- live state from the background service ----------
    private val busListener: () -> Unit = {
        orbState(AssistantBus.state); orbLevel(AssistantBus.level); orbStandby(AssistantBus.standby || prefs.wakeEnabled)
        edgeView.state = AssistantBus.state; edgeView.level = AssistantBus.level
        val showEdge = AssistantBus.sessionOn && prefs.edgeLight && !EdgeLight.systemShown
        if ((edgeView.visibility == View.VISIBLE) != showEdge) edgeView.visibility = if (showEdge) View.VISIBLE else View.GONE
        updatePill()
        val c = if (AssistantBus.sessionOn) "■" else "👑"
        if (centerBtn.text != c) centerBtn.text = c
    }

    // ---------- navigation ----------
    private fun create(name: String): Screen = when (name) {
        "Home" -> HomeScreen(this)
        "Chat" -> ChatScreen(this, false)
        "Code" -> ChatScreen(this, true)
        "Tools" -> ToolsScreen(this)
        "Phone" -> PhoneScreen(this)
        "Notes" -> NotesScreen(this)
        "History" -> HistoryScreen(this)
        "Triggers" -> TriggersScreen(this)
        "Memory" -> MemoryScreen(this)
        "Control" -> ControlScreen(this)
        "Settings" -> SettingsScreen(this)
        else -> AboutScreen(this)
    }

    fun show(name: String) {
        closeDrawer()
        current?.let { screens[it]?.onHide() }
        val s = screens.getOrPut(name) { create(name) }
        container.removeAllViews()
        val v = s.view
        container.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        v.alpha = 0f; v.translationX = dp(24).toFloat()
        v.animate().alpha(1f).translationX(0f).setDuration(170).start()
        current = name
        subtitle.text = if (name == "Home") "Your Smart AI Assistant" else (menu.firstOrNull { it.first == name }?.second?.substring(3)?.trim() ?: name)
        navLabels.forEach { (k, l) -> l.setTextColor(if (k == name) Theme.CYAN else Theme.MUTED) }
        s.onShow()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null || !prefs.onboarded) return
        val cmd = i.getStringExtra("cmd")
        val auto = i.getBooleanExtra("autocall", false)
        val tool = i.getStringExtra("tool")
        val toolPrompt = i.getStringExtra("toolPrompt") ?: ""
        i.removeExtra("cmd"); i.removeExtra("autocall"); i.removeExtra("tool"); i.removeExtra("toolPrompt")
        if (!tool.isNullOrBlank()) openTool(tool, toolPrompt)
        else if (auto) startCall()
        else if (!cmd.isNullOrBlank()) askChat(cmd)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    fun requestPerm(perm: String, cb: (Boolean) -> Unit) = requestAnyPerm(arrayOf(perm), cb)

    /** Asks for the permissions together; cb(true) if at least one is granted (e.g. fine OR approximate location). */
    fun requestAnyPerm(perms: Array<String>, cb: (Boolean) -> Unit) {
        if (perms.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) { cb(true); return }
        val code = nextCode++
        permCbs[code] = { ok ->
            if (!ok && perms.none { shouldShowRequestPermissionRationale(it) }) blockedDialog()
            cb(ok)
        }
        requestPermissions(perms, code)
    }

    private fun blockedDialog() {
        AlertDialog.Builder(this).setTitle("Permission blocked")
            .setMessage("Android won't show this permission prompt again. Open the app's settings → Permissions and allow it there.")
            .setPositiveButton("Open settings") { _, _ -> try { startActivity(Perms.appInfoIntent(this)) } catch (e: Exception) {} }
            .setNegativeButton("Not now", null).show()
    }

    @Suppress("DEPRECATION")
    fun startForResult(i: Intent, cb: (Int, Intent?) -> Unit) {
        val code = nextCode++
        resultCbs[code] = cb
        try { startActivityForResult(i, code) } catch (e: Exception) { resultCbs.remove(code); toast("Not available on this phone.") }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permCbs.remove(requestCode)?.invoke(grantResults.any { it == PackageManager.PERMISSION_GRANTED })
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        resultCbs.remove(requestCode)?.invoke(resultCode, data)
    }

    private fun startNavAnim() {
        if (navAnim != null || prefs.liteMode) return
        val hsv = floatArrayOf(0f, 0.85f, 1f)
        var f = 0
        navAnim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (++f % 3 != 0) return@addUpdateListener
                hsv[0] = it.animatedValue as Float
                centerBg.setStroke(dp(3), Color.HSVToColor(hsv))
            }
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        visibleHost = host
        aurora.paused = false; aurora.invalidate()
        Parallax.start(this)
        startNavAnim()
        if (!AssistantBus.listeners.contains(busListener)) AssistantBus.listeners.add(busListener)
        busListener()
        if (prefs.wakeEnabled && !AssistantService.running && Perms.has(this, Manifest.permission.RECORD_AUDIO)) AssistantService.send(this, AssistantService.A_WAKE_ON)
        setup?.onShow()
        current?.let { screens[it]?.onShow() }
    }

    override fun onPause() {
        super.onPause()
        visibleHost = null
        aurora.paused = true
        Parallax.stop()
        navAnim?.cancel(); navAnim = null
        AssistantBus.listeners.remove(busListener)
        (screens["Home"] as? HomeScreen)?.pauseAnim()
        if (!AssistantBus.sessionOn) speaker.stop()   // chat replies only; the background conversation keeps running
        orbState(OrbView.S.IDLE); orbLevel(0f)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            drawer.visibility == View.VISIBLE -> closeDrawer()
            prefs.onboarded && current != "Home" -> show("Home")
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        screens.values.forEach { it.onHide() }
        super.onDestroy()
    }

    companion object { @Volatile var visibleHost: ActivityHost? = null }
}
