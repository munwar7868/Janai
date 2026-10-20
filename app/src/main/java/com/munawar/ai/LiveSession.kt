package com.munawar.ai

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Gemini Live API over WebSocket (documented BidiGenerateContent protocol).
 * Mic: 16 kHz PCM16 mono -> server. Server: 24 kHz PCM16 audio -> speaker. Server-side VAD handles barge-in.
 * Auto-reconnects up to 3 times. If setup fails before connecting, onClosed(reason, false) lets the UI fall back.
 */
class LiveSession(
    private val ctx: Context, private val key: String, private val model: String,
    private val voice: String, private val system: String, private val cb: Cb
) {
    interface Cb {
        fun onConnected()
        fun onStatus(s: String)
        fun onUserText(t: String)
        fun onModelText(t: String)
        fun onSpeaking(on: Boolean)
        fun onLevel(l: Float)
        fun onTurnComplete()
        /** Gemini asked to run a phone command. Call [done] with the result text. */
        fun onCommand(command: String, done: (String) -> Unit)
        fun onClosed(reason: String, wasConnected: Boolean)
    }

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null

    @Volatile var muted = false
    @Volatile private var running = false
    @Volatile private var connected = false
    private var everConnected = false
    private var retries = 0
    private var variant = 0
    private var rec: AudioRecord? = null
    private var track: AudioTrack? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var oldMode = AudioManager.MODE_NORMAL
    @Volatile private var speaking = false

    val toolsActive: Boolean get() = variant < 2

    fun start() { running = true; connect() }

    private fun clean(s: String) = s.replace(key, "***").take(160)

    private fun setup(): JSONObject {
        val setup = JSONObject()
            .put("model", "models/" + model.removePrefix("models/"))
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("inputAudioTranscription", JSONObject())
            .put("outputAudioTranscription", JSONObject())
        if (variant < 2) {
            setup.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", JSONArray().put(
                JSONObject().put("name", "phone_action").put("description", Persona.ACTION_SPEC).put(
                    "parameters", JSONObject().put("type", "OBJECT")
                        .put("properties", JSONObject().put("command", JSONObject().put("type", "STRING").put("description", "One command in the grammar above")))
                        .put("required", JSONArray().put("command"))
                )
            ))))
        }
        if (variant == 0) {
            setup.put(
                "generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice))))
            )
        } else {
            setup.put("responseModalities", JSONArray().put("AUDIO")) // flat form shown in current docs
        }
        return JSONObject().put("setup", setup)
    }

    private fun connect() {
        connected = false
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"
        ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) { w.send(setup().toString()) }
            override fun onMessage(w: WebSocket, text: String) { if (w === ws) handle(text) }
            override fun onMessage(w: WebSocket, bytes: ByteString) { if (w === ws) handle(bytes.utf8()) }
            override fun onClosing(w: WebSocket, code: Int, reason: String) { w.close(1000, null) }
            override fun onClosed(w: WebSocket, code: Int, reason: String) { drop(clean(reason.ifBlank { "closed ($code)" }), w) }
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) {
                drop("Connection failed (${t.javaClass.simpleName}${r?.let { " HTTP " + it.code } ?: ""})", w)
            }
        })
    }

    private fun drop(reason: String, w: WebSocket) {
        if (w !== ws || !running) return
        connected = false
        if (!everConnected) {
            if (variant < 2) { variant++; main.postDelayed({ if (running) connect() }, 300); return }
            running = false; stopAudio()
            main.post { cb.onClosed(reason, false) }
            return
        }
        if (retries < 3) {
            retries++
            main.post { cb.onStatus("Reconnecting… ($retries/3)") }
            main.postDelayed({ if (running) connect() }, 1200L * retries)
        } else {
            running = false; stopAudio()
            main.post { cb.onClosed(reason, true) }
        }
    }

    private fun handle(text: String) {
        try {
            val j = JSONObject(text)
            if (j.has("setupComplete")) {
                connected = true; everConnected = true; retries = 0
                if (rec == null) startAudio()
                main.post { cb.onConnected() }
                return
            }
            j.optJSONObject("toolCall")?.optJSONArray("functionCalls")?.let { fcs ->
                for (i in 0 until fcs.length()) {
                    val fc = fcs.getJSONObject(i)
                    val id = fc.optString("id"); val name = fc.optString("name")
                    val cmd = fc.optJSONObject("args")?.optString("command") ?: ""
                    main.post {
                        cb.onCommand(cmd) { result ->
                            ws?.send(JSONObject().put("toolResponse", JSONObject().put("functionResponses", JSONArray().put(
                                JSONObject().put("id", id).put("name", name).put("response", JSONObject().put("result", result))
                            ))).toString())
                        }
                    }
                }
                return
            }
            val sc = j.optJSONObject("serverContent") ?: return
            if (sc.optBoolean("interrupted")) { flush(); setSpeaking(false) }
            val parts = sc.optJSONObject("modelTurn")?.optJSONArray("parts")
            if (parts != null) for (i in 0 until parts.length()) {
                val d = parts.getJSONObject(i).optJSONObject("inlineData") ?: continue
                if (d.optString("mimeType").startsWith("audio/pcm")) {
                    queue.put(Base64.decode(d.getString("data"), Base64.DEFAULT)); setSpeaking(true)
                }
            }
            sc.optJSONObject("inputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) main.post { cb.onUserText(it) } }
            sc.optJSONObject("outputTranscription")?.optString("text")?.let { if (it.isNotEmpty()) main.post { cb.onModelText(it) } }
            if (sc.optBoolean("turnComplete")) main.post { cb.onTurnComplete() }
        } catch (e: Exception) { /* ignore malformed frame */ }
    }

    private fun setSpeaking(on: Boolean) {
        if (speaking != on) { speaking = on; main.post { cb.onSpeaking(on) } }
    }

    private fun level(buf: ByteArray, n: Int): Float {
        var sum = 0.0; var cnt = 0; var i = 0
        while (i + 1 < n) {
            val s = ((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)).toShort().toInt()
            sum += s.toDouble() * s; cnt++; i += 2
        }
        if (cnt == 0) return 0f
        return (sqrt(sum / cnt) / 6000.0).toFloat().coerceIn(0f, 1f)
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        oldMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION") run { am.isSpeakerphoneOn = true }

        val min = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 6400) * 2)
        } catch (e: Exception) { null }
        if (r == null || r.state != AudioRecord.STATE_INITIALIZED) {
            running = false
            main.post { cb.onClosed("Microphone couldn't start (is another app using it?)", true) }
            return
        }
        try { if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(r.audioSessionId)?.setEnabled(true) } catch (e: Exception) {}
        try { if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(r.audioSessionId)?.setEnabled(true) } catch (e: Exception) {}
        rec = r
        r.startRecording()

        val outMin = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(outMin * 2, 48000))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()

        thread(name = "live-rec", isDaemon = true) {
            val buf = ByteArray(3200)
            try {
                while (running) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    if (!speaking) { val l = level(buf, n); main.post { cb.onLevel(l) } }
                    if (connected && !muted) {
                        ws?.send(
                            JSONObject().put(
                                "realtimeInput", JSONObject().put(
                                    "audio", JSONObject().put("data", Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)).put("mimeType", "audio/pcm;rate=16000")
                                )
                            ).toString()
                        )
                    }
                }
            } catch (e: Exception) {
            } finally {
                try { r.stop() } catch (e: Exception) {}
                try { r.release() } catch (e: Exception) {}
            }
        }
        thread(name = "live-play", isDaemon = true) {
            try {
                while (running) {
                    val b = queue.poll(300, TimeUnit.MILLISECONDS)
                    if (b == null) { if (speaking) setSpeaking(false); continue }
                    val l = level(b, b.size); main.post { cb.onLevel(l) }
                    t.write(b, 0, b.size)
                }
            } catch (e: Exception) {
            } finally {
                try { t.stop() } catch (e: Exception) {}
                try { t.release() } catch (e: Exception) {}
            }
        }
    }

    private fun flush() {
        queue.clear()
        try { track?.apply { pause(); flush(); play() } } catch (e: Exception) {}
    }

    /** Cut Munawar off locally (the server also detects you speaking). */
    fun interruptLocal() { flush(); setSpeaking(false) }

    private fun stopAudio() {
        try { am.mode = oldMode } catch (e: Exception) {}
        @Suppress("DEPRECATION") run { try { am.isSpeakerphoneOn = false } catch (e: Exception) {} }
        queue.clear()
        rec = null; track = null
    }

    fun stop() {
        running = false
        try { ws?.close(1000, "bye") } catch (e: Exception) {}
        ws = null
        stopAudio()
        client.dispatcher.executorService.shutdown()
    }
}
