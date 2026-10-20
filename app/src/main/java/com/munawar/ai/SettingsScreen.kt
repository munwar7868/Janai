package com.munawar.ai

import android.app.AlertDialog
import android.view.View
import android.widget.Switch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsScreen(private val a: MainActivity) : Screen {
    private val keyField = a.field("Paste Gemini API key", password = true)
    private val keyStatus = a.label("", 13f, Theme.MUTED)
    private val modelField = a.field("Model, e.g. gemini-2.5-flash")
    private val conn = a.label("Connection: not tested", 13f, Theme.MUTED)
    private val liveField = a.field("Live model")
    private val tempLabel = a.label("", 13f, Theme.MUTED)
    private val pitchLabel = a.label("", 13f, Theme.MUTED)
    private val rateLabel = a.label("", 13f, Theme.MUTED)
    private val elKey = a.field("ElevenLabs API key", password = true)
    private val elVoice = a.field("ElevenLabs Voice ID")
    private val elModel = a.field("ElevenLabs model (eleven_multilingual_v2)")
    private val elStatus = a.label("", 13f, Theme.MUTED)
    private val haUrl = a.field("Home Assistant URL, e.g. http://192.168.1.10:8123")
    private val haToken = a.field("Long-lived access token", password = true)
    private val haDevices = a.field("One per line:  bedroom light = light.bedroom", multi = true)
    private val haStatus = a.label("", 13f, Theme.MUTED)
    private val ccField = a.field("Default country code, e.g. 91")
    private val imgField = a.field("Image model, e.g. gemini-2.5-flash-image")
    private val oaiBase = a.field("Base URL (OpenAI-compatible), e.g. https://api.openai.com/v1")
    private val oaiModel = a.field("Model name")
    private val oaiKey = a.field("API key", password = true)
    private val claudeModel = a.field("Claude model name")
    private val claudeKey = a.field("Claude API key", password = true)
    private val brainStatus = a.label("", 12f, Theme.MUTED)
    private val sosField = a.field("Emergency numbers, comma separated, e.g. +919876543210, +911234567890", multi = true)
    private val ghField = a.field("GitHub username")
    private val repo = a.field("GitHub repo for updates: owner/name")
    override val view: View = build()

    private fun sw(text: String, on: Boolean, f: (Boolean) -> Unit) = Switch(a).apply {
        this.text = text; setTextColor(Theme.TEXT); isChecked = on; minimumHeight = a.dp(48)
        setOnCheckedChangeListener { _, v -> f(v) }
    }

    private fun build(): View {
        val p = a.prefs
        val root = a.vbox().apply { setPadding(a.dp(14), a.dp(12), a.dp(14), a.dp(28)) }
        root.addView(a.label("Settings", 22f, Theme.CYAN, true))

        // ---- Gemini
        root.addView(a.section("Gemini API key"))
        root.addView(a.label("Get a key at aistudio.google.com. Encrypted with the Android Keystore; never logged or hardcoded.", 12f, Theme.MUTED))
        root.addView(keyField, a.lp(MATCH, WRAP, m = 3))
        root.addView(keyStatus)
        val kr = a.hbox()
        kr.addView(a.button("Save / edit") {
            val k = keyField.text.toString().trim()
            if (k.length < 20) a.toast("That doesn't look like a valid key") else { p.saveApiKey(k); keyField.setText(""); refreshKey() }
        }, a.lp(0, WRAP, 1f, 2))
        kr.addView(a.button("Remove", Theme.RED) { p.removeApiKey(); refreshKey() }, a.lp(0, WRAP, 1f, 2))
        root.addView(kr)
        root.addView(a.button("Test connection", Theme.CYAN) { test() }, a.lp(MATCH, WRAP, m = 2))
        root.addView(conn)

        root.addView(a.section("Chat model (default: Gemini 3.5 Flash)"))
        modelField.setText(p.model)
        root.addView(modelField, a.lp(MATCH, WRAP, m = 3))
        val mr = a.hbox()
        mr.addView(a.button("Save model") { p.model = modelField.text.toString(); a.toast("Model: ${p.model}") }, a.lp(0, WRAP, 1f, 2))
        mr.addView(a.button("Load models", Theme.CYAN) { loadModels("generateContent") { m -> p.model = m; modelField.setText(m) } }, a.lp(0, WRAP, 1f, 2))
        root.addView(mr)
        root.addView(sw("Google Search grounding (live web info)", p.webSearch) { p.webSearch = it })

        // ---- Live
        root.addView(a.section("Gemini Live (real-time voice)"))
        liveField.setText(p.liveModel)
        root.addView(liveField, a.lp(MATCH, WRAP, m = 3))
        val lr = a.hbox()
        lr.addView(a.button("Save Live model") { p.liveModel = liveField.text.toString(); a.toast("Live model: ${p.liveModel}") }, a.lp(0, WRAP, 1f, 2))
        lr.addView(a.button("Load Live models", Theme.CYAN) { loadModels("bidiGenerateContent") { m -> p.liveModel = m; liveField.setText(m) } }, a.lp(0, WRAP, 1f, 2))
        root.addView(lr)
        root.addView(a.label("Live voice", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("Aoede" to "Aoede (female)", "Kore" to "Kore (female)", "Leda" to "Leda (female)", "Zephyr" to "Zephyr (female)", "Puck" to "Puck (male)", "Charon" to "Charon (male)"), p.liveVoice) { p.liveVoice = it })
        root.addView(sw("Use Gemini Live in calls", p.liveDefault) { p.liveDefault = it })
        root.addView(a.label("If Live can't connect, calls automatically fall back to classic mode and show the reason. Use 'Load Live models' to see what your key supports.", 12f, Theme.MUTED))

        // ---- Personality
        root.addView(a.section("Personality"))
        tempLabel.text = "Creativity (temperature): %.1f".format(p.temperature)
        root.addView(tempLabel)
        root.addView(a.seek(20, (p.temperature * 10).toInt()) { p.temperature = it / 10f; tempLabel.text = "Creativity (temperature): %.1f".format(p.temperature) })
        root.addView(a.label("Mode", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("girlfriend" to "💕 Girlfriend", "friend" to "🤝 Friend", "serious" to "🎯 Serious"), p.mode) { p.mode = it })
        root.addView(a.label("Language", 13f, Theme.MUTED))
        root.addView(a.radio(listOf("hinglish" to "Hinglish (default)", "hindi" to "Hindi", "english" to "English"), p.language) { p.language = it })
        val nameF = a.field("Your name (optional)"); nameF.setText(p.userName)
        val perF = a.field("Extra personality instructions (optional)", multi = true); perF.setText(p.customPersona)
        root.addView(nameF, a.lp(MATCH, WRAP, m = 3))
        root.addView(perF, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save personality text") { p.userName = nameF.text.toString().trim(); p.customPersona = perF.text.toString().trim(); a.toast("Saved") })

        // ---- Voice
        root.addView(a.section("Voice (phone TTS)"))
        root.addView(sw("Speak replies in Chat", p.speak) { p.speak = it })
        pitchLabel.text = "Pitch: %.2f".format(p.pitch); rateLabel.text = "Speed: %.2f".format(p.rate)
        root.addView(pitchLabel)
        root.addView(a.seek(20, ((p.pitch - 0.5f) * 20).toInt()) { p.pitch = 0.5f + it / 20f; pitchLabel.text = "Pitch: %.2f".format(p.pitch) })
        root.addView(rateLabel)
        root.addView(a.seek(20, ((p.rate - 0.5f) * 20).toInt()) { p.rate = 0.5f + it / 20f; rateLabel.text = "Speed: %.2f".format(p.rate) })
        val vr = a.hbox()
        vr.addView(a.button("Pick voice", Theme.CYAN) { pickVoice() }, a.lp(0, WRAP, 1f, 2))
        vr.addView(a.button("Test voice") { a.speaker.speak("Hi, main Munawar AI hoon. Kaisi ho?") }, a.lp(0, WRAP, 1f, 2))
        root.addView(vr)

        // ---- ElevenLabs
        root.addView(a.section("ElevenLabs voice (optional)"))
        root.addView(a.label("Natural voice for replies in Chat/Tools/classic calls (Live uses Gemini's own voice). Key is encrypted. If ElevenLabs fails, the phone voice is used.", 12f, Theme.MUTED))
        root.addView(sw("Use ElevenLabs for spoken replies", p.elevenEnabled) { p.elevenEnabled = it })
        root.addView(elKey, a.lp(MATCH, WRAP, m = 3))
        elVoice.setText(p.elevenVoiceId); elModel.setText(p.elevenModel)
        root.addView(elVoice, a.lp(MATCH, WRAP, m = 3))
        root.addView(elModel, a.lp(MATCH, WRAP, m = 3))
        val er = a.hbox()
        er.addView(a.button("Save") {
            val k = elKey.text.toString().trim()
            if (k.isNotEmpty()) p.saveSecret("eleven", k)
            p.elevenVoiceId = elVoice.text.toString(); p.elevenModel = elModel.text.toString().ifBlank { "eleven_multilingual_v2" }
            elKey.setText(""); refreshEl()
        }, a.lp(0, WRAP, 1f, 2))
        er.addView(a.button("Remove key", Theme.RED) { p.removeSecret("eleven"); refreshEl() }, a.lp(0, WRAP, 1f, 2))
        er.addView(a.button("Test", Theme.CYAN) { testEleven() }, a.lp(0, WRAP, 1f, 2))
        root.addView(er)
        root.addView(elStatus)

        // ---- Smart home
        root.addView(a.section("Smart home (Home Assistant)"))
        root.addView(a.label("Works with a Home Assistant server on your network. Then say “bedroom light on/off” or “bedroom light chalu/band”. The token is encrypted.", 12f, Theme.MUTED))
        haUrl.setText(p.haUrl); haDevices.setText(p.haDevices)
        root.addView(haUrl, a.lp(MATCH, WRAP, m = 3))
        root.addView(haToken, a.lp(MATCH, WRAP, m = 3))
        root.addView(haDevices, a.lp(MATCH, WRAP, m = 3))
        val hr = a.hbox()
        hr.addView(a.button("Save") {
            p.haUrl = haUrl.text.toString(); p.haDevices = haDevices.text.toString()
            val t = haToken.text.toString().trim(); if (t.isNotEmpty()) p.saveSecret("ha", t)
            haToken.setText(""); refreshHa()
        }, a.lp(0, WRAP, 1f, 2))
        hr.addView(a.button("Remove token", Theme.RED) { p.removeSecret("ha"); refreshHa() }, a.lp(0, WRAP, 1f, 2))
        root.addView(hr)
        root.addView(haStatus)

        // ---- Floating orb / messaging / performance
        root.addView(a.section("Floating orb"))
        val szLabel = a.label("Size: ${p.floatSize} dp", 13f, Theme.MUTED)
        root.addView(szLabel)
        root.addView(a.seek(144, p.floatSize - 56) { p.floatSize = 56 + it; szLabel.text = "Size: ${p.floatSize} dp" }.also { sb ->
            sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar?, v: Int, u: Boolean) { if (u) { p.floatSize = 56 + v; szLabel.text = "Size: ${p.floatSize} dp" } }
                override fun onStartTrackingTouch(s: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar?) { FloatingService.refresh(a) }
            })
        })
        root.addView(a.label("Show/hide the floating orb in ☰ → Control Center.", 12f, Theme.MUTED))

        root.addView(a.section("Messaging & calls"))
        val csLabel = a.label("Cancel window: ${if (p.confirmSeconds == 0) "off (act instantly)" else "${p.confirmSeconds} s"}", 13f, Theme.MUTED)
        root.addView(csLabel)
        root.addView(a.seek(15, p.confirmSeconds) { p.confirmSeconds = it; csLabel.text = "Cancel window: ${if (it == 0) "off (act instantly)" else "$it s"}" })
        root.addView(a.label("Before Munawar calls or sends an SMS/WhatsApp it shows the number and message with a Cancel button. If you don't cancel within this time it goes ahead. 0 = no pause (instant).", 12f, Theme.MUTED))
        ccField.setText(p.countryCode)
        root.addView(ccField, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save country code") { p.countryCode = ccField.text.toString(); a.toast("Saved: +${p.countryCode}") })

        root.addView(a.section("Image generation"))
        imgField.setText(p.imageModel)
        root.addView(imgField, a.lp(MATCH, WRAP, m = 3))
        val ir = a.hbox()
        ir.addView(a.button("Save") { p.imageModel = imgField.text.toString(); a.toast("Image model: ${p.imageModel}") }, a.lp(0, WRAP, 1f, 2))
        ir.addView(a.button("Load models", Theme.CYAN) { loadModels("generateContent") { m -> p.imageModel = m; imgField.setText(m) } }, a.lp(0, WRAP, 1f, 2))
        root.addView(ir)

        root.addView(a.section("Screen-edge light"))
        root.addView(sw("Colourful light running around the screen while Munawar is on", p.edgeLight) { p.edgeLight = it; EdgeLight.refreshPrefs() })
        val etLabel = a.label("Thickness: ${p.edgeThickness} dp", 13f, Theme.MUTED)
        root.addView(etLabel)
        root.addView(a.seek(12, p.edgeThickness - 2) { p.edgeThickness = 2 + it; etLabel.text = "Thickness: ${p.edgeThickness} dp"; EdgeLight.refreshPrefs() })
        val esLabel = a.label("Speed: ${p.edgeSpeed} / 4", 13f, Theme.MUTED)
        root.addView(esLabel)
        root.addView(a.seek(3, p.edgeSpeed - 1) { p.edgeSpeed = 1 + it; esLabel.text = "Speed: ${p.edgeSpeed} / 4"; EdgeLight.refreshPrefs() })
        root.addView(a.label("Shows over every app when 'Display over other apps' is allowed (Control Center), otherwise only inside Munawar. Adapts to your phone's screen corners.", 12f, Theme.MUTED))

        root.addView(a.section("Performance"))
        root.addView(sw("Lite animations (smoother on older phones; restart app)", p.liteMode) { p.liteMode = it })

        // ---- AI brain
        root.addView(a.section("AI brain (chat)"))
        root.addView(a.label("Gemini is the default and the only brain for voice calls and phone actions. For text chat you can switch to an OpenAI-compatible service (OpenAI, Groq, OpenRouter…) or Anthropic Claude.", 12f, Theme.MUTED))
        root.addView(a.radio(listOf("gemini" to "Google Gemini (default)", "openai" to "OpenAI-compatible", "claude" to "Anthropic Claude"), p.provider) { p.provider = it })
        oaiBase.setText(p.oaiBase); oaiModel.setText(p.oaiModel); claudeModel.setText(p.claudeModel)
        root.addView(a.label("OpenAI-compatible", 13f, Theme.CYAN, true))
        root.addView(oaiBase, a.lp(MATCH, WRAP, m = 3)); root.addView(oaiModel, a.lp(MATCH, WRAP, m = 3)); root.addView(oaiKey, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.label("Claude", 13f, Theme.CYAN, true))
        root.addView(claudeModel, a.lp(MATCH, WRAP, m = 3)); root.addView(claudeKey, a.lp(MATCH, WRAP, m = 3))
        val br = a.hbox()
        br.addView(a.button("Save") {
            p.oaiBase = oaiBase.text.toString(); p.oaiModel = oaiModel.text.toString(); p.claudeModel = claudeModel.text.toString()
            val k1 = oaiKey.text.toString().trim(); if (k1.isNotEmpty()) p.saveSecret("openai", k1)
            val k2 = claudeKey.text.toString().trim(); if (k2.isNotEmpty()) p.saveSecret("claude", k2)
            oaiKey.setText(""); claudeKey.setText(""); refreshBrain()
        }, a.lp(0, WRAP, 1f, 2))
        br.addView(a.button("Remove keys", Theme.RED) { p.removeSecret("openai"); p.removeSecret("claude"); refreshBrain() }, a.lp(0, WRAP, 1f, 2))
        root.addView(br)
        root.addView(brainStatus)

        // ---- Safety & connectors
        root.addView(a.section("Safety (SOS)"))
        sosField.setText(p.emergencyNumbers)
        root.addView(sosField, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save emergency contacts") { p.emergencyNumbers = sosField.text.toString(); a.toast("Saved") })
        root.addView(a.label("Say “SOS”: Munawar shows the message with your location and a cancel countdown, then texts these numbers.", 12f, Theme.MUTED))
        root.addView(a.section("Connectors"))
        ghField.setText(p.githubUser)
        root.addView(ghField, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save GitHub user") { p.githubUser = ghField.text.toString(); a.toast("Saved") })
        root.addView(a.label("Then ask: “my github repos”. (Public repositories only.)", 12f, Theme.MUTED))

        // ---- Updates / usage
        root.addView(a.section("Updates"))
        repo.setText(p.updateRepo)
        root.addView(repo, a.lp(MATCH, WRAP, m = 3))
        root.addView(a.button("Save repo") { p.updateRepo = repo.text.toString(); a.toast("Saved") })
        root.addView(a.section("Usage"))
        root.addView(a.button("Reset usage counters", Theme.CARD) { Stats.reset(); a.toast("Reset") })

        refreshKey(); refreshEl(); refreshHa(); refreshBrain()
        return a.scroll(root)
    }

    private fun refreshKey() {
        keyStatus.text = if (a.prefs.hasApiKey()) "✔ A key is saved (hidden). Paste a new one and tap Save to replace it." else "No key saved."
        conn.text = "Connection: not tested"
    }

    private fun refreshEl() {
        val has = a.prefs.secret("eleven") != null
        elStatus.text = (if (has) "✔ ElevenLabs key saved. " else "No ElevenLabs key. ") + (if (a.prefs.elevenVoiceId.isBlank()) "Voice ID missing." else "Voice ID set.")
    }

    private fun refreshBrain() {
        val p = a.prefs
        brainStatus.text = "Active: ${p.provider} • OpenAI key ${if (p.secret("openai") != null) "saved" else "none"} • Claude key ${if (p.secret("claude") != null) "saved" else "none"}"
    }

    private fun refreshHa() {
        val has = a.prefs.secret("ha") != null
        haStatus.text = (if (has) "✔ Token saved. " else "No token. ") + "${SmartHome.devices(a.prefs).size} device(s) mapped."
    }

    private fun test() {
        val k = a.prefs.apiKey()
        if (k == null) { conn.text = "⚠ Save an API key first"; return }
        conn.text = "Testing…"
        a.scope.launch {
            try { Gemini.test(k, a.prefs.model); conn.text = "✔ Connected. Model ${a.prefs.model} responded." }
            catch (e: Exception) { conn.text = "✖ " + (e.message ?: "Failed") }
        }
    }

    private fun testEleven() {
        val k = a.prefs.secret("eleven")
        if (k == null || a.prefs.elevenVoiceId.isBlank()) { elStatus.text = "⚠ Save the key and the Voice ID first."; return }
        elStatus.text = "Testing…"
        a.scope.launch {
            try {
                withContext(Dispatchers.IO) { ElevenLabs.tts(k, a.prefs.elevenVoiceId, a.prefs.elevenModel, "Hello") }
                elStatus.text = "✔ ElevenLabs works. Turn on 'Use ElevenLabs' to hear it."
                if (a.prefs.elevenEnabled) a.speaker.speak("Hi, main Munawar AI hoon.")
            } catch (e: Exception) { elStatus.text = "✖ " + (e.message ?: "Failed") }
        }
    }

    private fun loadModels(method: String, onPick: (String) -> Unit) {
        val k = a.prefs.apiKey()
        if (k == null) { a.toast("Save an API key first"); return }
        a.toast("Loading models…")
        a.scope.launch {
            try {
                val models = Gemini.listModels(k, method)
                if (models.isEmpty()) { a.toast("No models returned for this key"); return@launch }
                AlertDialog.Builder(a).setTitle("Choose model")
                    .setItems(models.toTypedArray()) { _, i -> onPick(models[i]) }.show()
            } catch (e: Exception) { a.toast(e.message ?: "Failed") }
        }
    }

    private fun pickVoice() {
        val v = a.speaker.voiceNames()
        if (v.isEmpty()) { a.toast("No Hindi/English voices found. Install one in Android TTS settings."); return }
        AlertDialog.Builder(a).setTitle("Choose voice")
            .setItems(v.toTypedArray()) { _, i -> a.prefs.voiceName = v[i]; a.speaker.speak("Hi, main Munawar AI hoon.") }.show()
    }
}
