package com.munawar.ai

import android.app.AlertDialog
import android.view.View
import android.widget.Switch

/** "When X happens → run this command" rules, plus the habits Munawar has learned. */
class TriggersScreen(private val a: MainActivity) : Screen {
    private val list = a.vbox()
    private val habits = a.vbox()
    override val view: View = build()

    private fun build(): View {
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(10), a.dp(14), a.dp(24)) }
        root.addView(RainbowText(a, "Triggers & habits", 22f))
        root.addView(a.label("Example: when the charger connects → “flashlight off”; when a notification contains “OTP” → “read otp”; every day at 07:00 → “daily briefing”. Charger/headphone/screen triggers run while “Hey Munawar” (always listening) is ON; notification triggers need Notification access; time triggers use the alarm clock.", 12f, Theme.MUTED))
        root.addView(a.button("＋ New rule") { newRule() }, a.lp(MATCH, WRAP, m = 4))
        root.addView(list)
        root.addView(a.section("Learned habits"))
        root.addView(a.label("Munawar remembers (on this phone only) which commands you use at which hour and suggests them on Home.", 12f, Theme.MUTED))
        root.addView(habits)
        refresh()
        return a.scroll(root)
    }

    override fun onShow() = refresh()

    private fun refresh() {
        list.removeAllViews()
        val rules = Triggers.all()
        if (rules.isEmpty()) list.addView(a.label("No rules yet.", 13f, Theme.MUTED))
        rules.forEach { r ->
            val c = a.card()
            val name = Triggers.types.firstOrNull { it.first == r.type }?.second ?: r.type
            c.addView(a.label("$name${if (r.arg.isNotBlank()) ": ${r.arg}" else ""}", 14f, Theme.CYAN, true))
            c.addView(a.label("→ ${r.command}", 14f))
            val row = a.hbox()
            row.addView(Switch(a).apply {
                text = "On"; setTextColor(Theme.TEXT); isChecked = r.enabled
                setOnCheckedChangeListener { _, _ -> Triggers.toggle(r.id) }
            }, a.lp(0, WRAP, 1f))
            row.addView(a.button("Delete", Theme.CARD) { Triggers.delete(r.id); refresh() }, a.lp(WRAP, WRAP, m = 2))
            c.addView(row)
            list.addView(c, a.lp(MATCH, WRAP, m = 3))
        }
        habits.removeAllViews()
        val s = Habits.suggest(5)
        habits.addView(a.label(if (s.isEmpty()) "Nothing learned yet. Use commands for a few days." else "Around now you usually use:\n" + s.joinToString("\n") { "• $it" }, 13f))
        habits.addView(a.button("Forget habits", Theme.CARD) { Habits.clear(); refresh() }, a.lp(WRAP, WRAP, m = 3))
    }

    private fun newRule() {
        val labels = Triggers.types.map { it.second }.toTypedArray()
        AlertDialog.Builder(a).setTitle("When…").setItems(labels) { _, i ->
            val type = Triggers.types[i].first
            val needsArg = type == "notification" || type == "time"
            val box = a.vbox().apply { setPadding(a.dp(16), a.dp(8), a.dp(16), a.dp(8)) }
            val argF = a.field(if (type == "time") "Time, e.g. 07:30" else "App name or text, e.g. whatsapp")
            val cmdF = a.field("Then run, e.g. flashlight on")
            if (needsArg) box.addView(argF, a.lp(MATCH, WRAP, m = 3))
            box.addView(cmdF, a.lp(MATCH, WRAP, m = 3))
            AlertDialog.Builder(a).setTitle(labels[i]).setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val cmd = cmdF.text.toString().trim()
                    val arg = argF.text.toString().trim()
                    if (cmd.isEmpty() || (needsArg && arg.isEmpty())) a.toast("Fill in everything first.")
                    else { Triggers.add(type, if (needsArg) arg else "", cmd); refresh() }
                }.setNegativeButton("Cancel", null).show()
        }.show()
    }
}
