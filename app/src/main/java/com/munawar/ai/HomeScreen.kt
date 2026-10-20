package com.munawar.ai

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Home = the talking screen. It is only a remote control for AssistantService, which keeps the Gemini conversation alive
 * in the background (minimise or close the app and it keeps listening).
 */
class HomeScreen(private val a: MainActivity) : Screen {
    private val orb = OrbView(a).also { a.orbs.add(it); it.lite = a.prefs.liteMode }
    private val wave = WaveView(a)
    private val greeting = a.label("", 14f, Color.WHITE)
    private val status = a.label(AssistantBus.status, 15f, Theme.CYAN).apply { gravity = Gravity.CENTER }
    private val timer = a.label("", 13f, Theme.MUTED).apply { gravity = Gravity.CENTER }
    private val sub = a.label("", 15f).apply { gravity = Gravity.CENTER }
    private val h = Handler(Looper.getMainLooper())
    private val tiles = ArrayList<TileView>()
    private val stateRows = ArrayList<TextView>()
    private var anim: ValueAnimator? = null
    private var frame = 0
    private val controls = a.hbox()
    private val suggestRow = a.hbox()
    private val muteBtn: Button = a.button("🎙 Mute", Theme.CARD) { AssistantService.send(a, AssistantService.A_MUTE) }
    private val intBtn: Button = a.button("✋ Interrupt", Theme.CARD) { AssistantService.send(a, AssistantService.A_INTERRUPT) }
    private val endBtn: Button = a.button("■ End", Theme.RED) { AssistantService.send(a, AssistantService.A_STOP_SESSION) }
    private val input: EditText = a.field("Type or speak… ask anything…").apply { inputType = InputType.TYPE_CLASS_TEXT; setSingleLine(true) }
    private val listener: () -> Unit = { render() }
    private var ticking = false

    private val tick = object : Runnable {
        override fun run() {
            if (!AssistantBus.sessionOn) { ticking = false; timer.text = ""; return }
            val s = (System.currentTimeMillis() - AssistantBus.startMs) / 1000
            timer.text = "%02d:%02d".format(s / 60, s % 60)
            h.postDelayed(this, 1000)
        }
    }

    override val view: View = build()

    private fun glass(radius: Int, stroke: Int = Theme.PURPLE) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(Color.parseColor("#CC1B1346"), Color.parseColor("#CC0B0A24"))
    ).apply { cornerRadius = a.dp(radius).toFloat(); setStroke(a.dp(2), stroke) }

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(12), a.dp(6), a.dp(12), a.dp(24)) }

        // greeting + live state list (like a heads-up display)
        val top = a.hbox().apply { gravity = Gravity.TOP }
        val gCard = a.vbox().apply { background = glass(18, Theme.CYAN); setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10)) }
        gCard.addView(greeting)
        gCard.addView(wave, a.lp(MATCH, a.dp(26), m = 0))
        top.addView(gCard, a.lp(0, WRAP, 1.25f, 3))
        val sCard = a.vbox().apply { background = glass(18, Theme.PURPLE); setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8)) }
        listOf("🧠  Think", "🎙  Listen", "⚡  Reply", "🖼  Generate", "💜  Always With You").forEach { s ->
            val tv = a.label(s, 12.5f, Theme.MUTED).apply { setPadding(0, a.dp(3), 0, a.dp(3)) }
            stateRows.add(tv); sCard.addView(tv)
        }
        top.addView(sCard, a.lp(0, WRAP, 1f, 3))
        root.addView(top)

        orb.setOnClickListener { if (AssistantBus.sessionOn) AssistantService.send(a, AssistantService.A_INTERRUPT) else a.startCall() }
        root.addView(orb, a.lp(MATCH, a.dp(290)))
        root.addView(status, a.lp(MATCH, WRAP, m = 1))
        root.addView(timer)
        controls.addView(muteBtn, a.lp(0, WRAP, 1f, 3))
        controls.addView(intBtn, a.lp(0, WRAP, 1f, 3))
        controls.addView(endBtn, a.lp(0, WRAP, 1f, 3))
        controls.visibility = View.GONE
        root.addView(controls)
        root.addView(sub, a.lp(MATCH, WRAP, m = 3))

        // type or speak bar
        val bar = a.hbox().apply { background = glass(30, Theme.CYAN); setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(6)) }
        bar.addView(a.button("🎙", Theme.PINK) { a.startCall() }, a.lp(a.dp(56), WRAP, m = 2))
        input.background = null
        input.setOnEditorActionListener { _, _, _ -> send(); true }
        bar.addView(input, a.lp(0, WRAP, 1f, 2))
        bar.addView(a.button("➤", Theme.PURPLE) { send() }, a.lp(a.dp(56), WRAP, m = 2))
        root.addView(bar, a.lp(MATCH, WRAP, m = 4))

        suggestRow.gravity = Gravity.CENTER
        root.addView(suggestRow)

        // big 3D glass tiles
        val g1 = a.hbox(); val g2 = a.hbox()
        fun tile(row: LinearLayout, e: String, t: String, s: String, hue: Float, go: () -> Unit) {
            val tv = TileView(a, e, t, s, hue, go); tv.minimumHeight = a.dp(128); tiles.add(tv); row.addView(tv, a.lp(0, WRAP, 1f, 4))
        }
        tile(g1, "💬", "Chat", "Talk with Munawar AI", 215f) { a.show("Chat") }
        tile(g1, "⌨", "Commands", "Quick & fast actions", 275f) { a.show("Phone") }
        tile(g1, "🛠", "Tools", "Useful AI tools", 38f) { a.show("Tools") }
        tile(g2, "🖼", "Images", "Generate amazing images", 320f) { a.openTool("image", "") }
        tile(g2, "📝", "Notes", "Save your thoughts", 130f) { a.show("Notes") }
        tile(g2, "👂", "Hey Munawar", "Wake word & voice", 185f) { a.setWake(!a.prefs.wakeEnabled) }
        root.addView(g1); root.addView(g2)
        root.addView(a.button("＋  All features: Triggers, Missions, History…", Theme.CARD) { a.openMenu() }, a.lp(MATCH, WRAP, m = 6))
        return a.scroll(root)
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty()) return
        input.setText("")
        a.askChat(t)
    }

    private fun render() {
        if (status.text != AssistantBus.status) status.text = AssistantBus.status
        if (sub.text != AssistantBus.sub) sub.text = AssistantBus.sub
        controls.visibility = if (AssistantBus.sessionOn) View.VISIBLE else View.GONE
        muteBtn.text = if (AssistantBus.muted) "🎙 Unmute" else "🎙 Mute"
        wave.active = AssistantBus.sessionOn && (AssistantBus.state == OrbView.S.LISTENING || AssistantBus.state == OrbView.S.SPEAKING)
        val name = a.prefs.userName.ifBlank { "friend" }
        val g = if (AssistantBus.sessionOn) "Talking with Gemini…" else "Hello $name!\nI'm always here for you. What would you like to do?"
        if (greeting.text != g) greeting.text = g
        val active = when {
            AssistantBus.state == OrbView.S.THINKING -> 0
            AssistantBus.state == OrbView.S.LISTENING && AssistantBus.sessionOn -> 1
            AssistantBus.state == OrbView.S.SPEAKING -> 2
            AssistantBus.standby || a.prefs.wakeEnabled -> 4
            else -> -1
        }
        stateRows.forEachIndexed { i, tv ->
            tv.setTextColor(if (i == active) Color.WHITE else Theme.MUTED)
            tv.setShadowLayer(if (i == active) 14f else 0f, 0f, 0f, Theme.CYAN)
        }
        if (AssistantBus.sessionOn && !ticking) { ticking = true; h.post(tick) }
    }

    private fun refreshSuggestions() {
        suggestRow.removeAllViews()
        for (s in Habits.suggest(3)) {
            suggestRow.addView(a.button("✨ $s", Theme.CARD) { a.runCommand(s) }.apply { textSize = 11f }, a.lp(WRAP, WRAP, m = 2))
        }
    }

    private fun startAnim() {
        if (anim != null || a.prefs.liteMode) return
        anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                if (++frame % 2 != 0) return@addUpdateListener
                val v = it.animatedValue as Float
                val tx = Parallax.tx; val ty = Parallax.ty
                tiles.forEachIndexed { i, t ->
                    t.glow((v + i * 50f) % 360f - 180f)
                    t.translationX = tx * a.dp(5) * (1 + (i % 3) * 0.4f)
                    t.translationY = ty * a.dp(4) * (1 + (i / 3) * 0.4f)
                }
            }
            start()
        }
        tiles.forEachIndexed { i, t -> t.alpha = 0f; t.translationY = 40f; t.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(300).start() }
    }

    fun pauseAnim() { anim?.cancel(); anim = null }

    override fun onShow() {
        if (!AssistantBus.listeners.contains(listener)) AssistantBus.listeners.add(listener)
        render(); refreshSuggestions(); startAnim()
    }

    override fun onHide() { AssistantBus.listeners.remove(listener); pauseAnim() }
}
