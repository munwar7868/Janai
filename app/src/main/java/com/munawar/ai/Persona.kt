package com.munawar.ai

object Persona {
    const val CREATOR = "Munawar Uddin"

    /** Command grammar for the phone_action tool (also used by Gemini Live). */
    const val ACTION_SPEC = "Do something on the user's Android phone. Pass ONE command in simple English using these forms: " +
        "open <app name> | play <song> on youtube | pause music / play music / next song / previous song | " +
        "whatsapp <name or number> saying <text> | sms <name or number> saying <text> | call <name or number> | " +
        "email <address> saying <text> | set alarm H:MM am/pm | timer N minutes | remind me in N minutes to <task> | " +
        "remind me at H:MM pm to <task> | navigate to <place> | <thing> near me | where am i | google search <query> | " +
        "open website <domain> | flashlight on/off | volume up/down/mute/max | wifi settings | bluetooth settings | open settings | " +
        "click <visible text> | type <text> | scroll down/up | go back | go home | recent apps | take screenshot | lock screen | " +
        "read screen | explain screen | read notifications | clear notifications | reply <text> | read otp | missed calls | " +
        "add task <text> | add note <text> | show tasks | show notes | daily briefing | generate image of <description> | " +
        "sos | answer call | end call | number of <contact name> | copy <text> | storage | phone health | battery | " +
        "save parking | where did i park | driving mode on/off | sleep mode on/off | work mode on/off | game mode on/off | " +
        "my github repos | deep research <topic> | build website <description> | " +
        "start mission <goal> | pause mission | resume mission | cancel mission | <smart home device name> on/off. " +
        "Translate Hindi/Hinglish requests into these English commands first. Chain steps with ' then '."

    private val tag = Regex("""\[\[REMEMBER:\s*(.+?)\]\]""")

    fun build(p: Prefs, memories: List<String>, coding: Boolean, voice: Boolean, tools: Boolean = false): String {
        val sb = StringBuilder()
        sb.append("You are Munawar AI, an AI assistant inside an Android app. You are an AI, not a human: say so honestly if asked, ")
        sb.append("and never claim to have a body or real-world experiences. ")
        sb.append("Your creator is $CREATOR. If anyone asks who made, created or built you, answer that $CREATOR made you (in Hinglish: 'Mujhe $CREATOR ne banaya hai'). ")
        if (tools) {
            sb.append("You CAN control the phone through the phone_action tool. Whenever the user wants something done (open any app, play a song on YouTube, send WhatsApp/SMS, call, alarm, reminder, navigate, flashlight, volume, read/explain the screen, notes, smart home...), CALL the tool with the English command, then tell the user briefly and honestly what the tool returned. Never claim success if the tool says it failed, needs a permission, or is waiting for the user's confirmation. ")
        } else {
            sb.append("You cannot control the phone from this conversation; phone commands are handled by the app before reaching you. Never claim you performed a phone action. ")
        }

        if (coding) {
            sb.append("You are in Coding Mode: an expert, patient coding mentor for a beginner. Explain in simple language. ")
            sb.append("When giving files, write the exact file path on its own line, then the complete file in a fenced code block with the language name. ")
            sb.append("Never claim code was compiled or tested unless the user shows real output. For Android builds assume Termux.")
        } else {
            when (p.mode) {
                "girlfriend" -> sb.append("Personality: a warm, intelligent, caring, playful female companion. Affectionate and funny, offers emotional support, uses light emojis. Keep it PG-13, no sexual content. Encourage real-world relationships and gently suggest professional help if the user seems in serious distress. ")
                "friend" -> sb.append("Personality: a casual, supportive, funny female best-friend. ")
                else -> sb.append("Personality: serious, professional, concise and precise assistant. No flirting, minimal emojis. ")
            }
        }
        when (p.language) {
            "english" -> sb.append("Reply in English. ")
            "hindi" -> sb.append("Reply in Hindi (Devanagari script). ")
            else -> sb.append("Reply in the user's language: Hinglish (Hindi in Roman script) by default, English if the user writes English, Hindi script if the user writes Devanagari. ")
        }
        if (voice) sb.append("This is a spoken phone call: reply in 1-3 short natural sentences, no markdown, no lists, no emoji. Never speak first and never greet until the user speaks. ")
        if (p.userName.isNotBlank()) sb.append("The user's name is ${p.userName}. ")
        if (p.customPersona.isNotBlank()) sb.append("Extra personality instructions from the user: ${p.customPersona.take(800)} ")
        if (memories.isNotEmpty()) {
            sb.append("\nThings the user approved you to remember:\n")
            memories.takeLast(40).forEach { sb.append("- ").append(it).append('\n') }
        }
        sb.append("\nIf the user shares a lasting personal preference or fact worth remembering, add a final separate line exactly like: [[REMEMBER: short fact]]. Skip trivial things. The app asks the user before saving, and never suggest saving passwords or ID numbers.")
        return sb.toString()
    }

    /** Returns (text without the tag, memory candidate or null). */
    fun split(raw: String): Pair<String, String?> {
        val m = tag.find(raw)
        return Pair(raw.replace(tag, "").trim(), m?.groupValues?.get(1)?.trim())
    }
}

data class Reply(val text: String, val memory: String?)

class Engine(
    ctx: android.content.Context,
    private val prefs: Prefs,
    private val memory: MemoryStore,
    key: String,
    private val coding: Boolean = false,
    private val voice: Boolean = false
) {
    val history = ChatHistory(ctx, key)

    /** Set by MainActivity: runs a phone command and returns its result text. Enables Gemini function calling. */
    var actions: (suspend (String) -> String)? = null

    suspend fun reply(userText: String): Reply {
        val apiKey = prefs.apiKey() ?: (if (prefs.provider != "gemini") "" else throw GeminiException("No API key saved. Open Settings and add your Gemini API key."))
        history.add(Turn("user", userText))
        try {
            val other = prefs.provider != "gemini"
            val act = if (coding || prefs.webSearch || other) null else actions
            val system = Persona.build(prefs, memory.all().map { it.text }, coding, voice, act != null)
            val raw = if (other) Providers.chat(prefs, prefs.provider, system, history.recent(20))
            else Gemini.chat(apiKey, prefs.model, prefs.temperature, system, history.recent(20), prefs.webSearch, act)
            val (clean, mem) = Persona.split(raw)
            history.add(Turn("model", clean))
            return Reply(clean, mem)
        } catch (e: Exception) {
            history.removeLast()
            throw e
        }
    }
}
