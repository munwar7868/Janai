package com.munawar.ai

import android.view.View

/** Everything you said and Munawar answered (text chats and classic-mode calls). Stored only on this phone. */
class HistoryScreen(private val a: MainActivity) : Screen {
    private val list = a.vbox()
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(24)) }
        root.addView(RainbowText(a, "History", 22f))
        root.addView(a.label("Newest at the bottom. Live (Gemini voice) conversations are not saved as text.", 12f, Theme.MUTED))
        root.addView(list)
        refresh()
        return a.scroll(root)
    }

    override fun onShow() = refresh()

    private fun section(title: String, h: ChatHistory) {
        list.addView(a.section(title))
        val turns = h.all().takeLast(25)
        if (turns.isEmpty()) { list.addView(a.label("Nothing yet.", 13f, Theme.MUTED)); return }
        list.addView(a.button("Clear this history", Theme.CARD) { h.clear(); refresh() }, a.lp(WRAP, WRAP, m = 2))
        turns.forEach { t ->
            val c = a.card()
            c.addView(a.label(if (t.role == "user") "You" else "Munawar", 11f, if (t.role == "user") Theme.CYAN else Theme.PINK, true))
            c.addView(a.label(t.text.take(400), 14f).apply { setTextIsSelectable(true) })
            list.addView(c, a.lp(MATCH, WRAP, m = 3))
        }
    }

    private fun refresh() {
        list.removeAllViews()
        section("💬 Chat", a.chatEngine.history)
        section("🎙 Voice (classic mode)", a.callEngine.history)
        section("💻 Code", a.codeEngine.history)
    }
}
