package com.munawar.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Settings + secrets. Secrets (Gemini, ElevenLabs, Home Assistant) are encrypted with an Android Keystore AES key. */
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("munawar_settings", Context.MODE_PRIVATE)
    private val sec = ctx.getSharedPreferences("munawar_secure", Context.MODE_PRIVATE)

    private fun s(k: String, d: String) = sp.getString(k, d) ?: d
    private fun putS(k: String, v: String) = sp.edit().putString(k, v).apply()
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun putB(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var model: String
        get() = s("model", "gemini-3.5-flash")
        set(v) = putS("model", v.trim().removePrefix("models/"))
    var temperature: Float
        get() = sp.getFloat("temp", 0.9f)
        set(v) = sp.edit().putFloat("temp", v).apply()
    var mode: String
        get() = s("mode", "girlfriend")
        set(v) = putS("mode", v)
    var language: String
        get() = s("lang", "hinglish")
        set(v) = putS("lang", v)
    var customPersona: String
        get() = s("persona", "")
        set(v) = putS("persona", v)
    var userName: String
        get() = s("user", "")
        set(v) = putS("user", v)
    var speak: Boolean
        get() = b("speak", true)
        set(v) = putB("speak", v)
    var webSearch: Boolean
        get() = b("web", false)
        set(v) = putB("web", v)
    var pitch: Float
        get() = sp.getFloat("pitch", 1.1f)
        set(v) = sp.edit().putFloat("pitch", v).apply()
    var rate: Float
        get() = sp.getFloat("rate", 1.0f)
        set(v) = sp.edit().putFloat("rate", v).apply()
    var voiceName: String
        get() = s("voice", "")
        set(v) = putS("voice", v)

    // Gemini Live
    var liveModel: String
        get() = s("live_model", "gemini-3.1-flash-live-preview")
        set(v) = putS("live_model", v.trim().removePrefix("models/"))
    var liveVoice: String
        get() = s("live_voice", "Aoede")
        set(v) = putS("live_voice", v)
    var liveDefault: Boolean
        get() = b("live_default", true)
        set(v) = putB("live_default", v)

    // ElevenLabs
    var elevenEnabled: Boolean
        get() = b("el_on", false)
        set(v) = putB("el_on", v)
    var elevenVoiceId: String
        get() = s("el_voice", "")
        set(v) = putS("el_voice", v.trim())
    var elevenModel: String
        get() = s("el_model", "eleven_multilingual_v2")
        set(v) = putS("el_model", v.trim())

    // Smart home (Home Assistant)
    var haUrl: String
        get() = s("ha_url", "")
        set(v) = putS("ha_url", v.trim().trimEnd('/'))
    var haDevices: String
        get() = s("ha_devices", "")
        set(v) = putS("ha_devices", v)

    // Updates / safety switches
    var updateRepo: String
        get() = s("repo", "")
        set(v) = putS("repo", v.trim())
    var onboarded: Boolean
        get() = b("onboarded", false)
        set(v) = putB("onboarded", v)
    var floatSize: Int
        get() = sp.getInt("float_size", 100)
        set(v) = sp.edit().putInt("float_size", v.coerceIn(56, 200)).apply()
    var countryCode: String
        get() = s("cc", "91")
        set(v) = putS("cc", v.filter { it.isDigit() })
    var imageModel: String
        get() = s("img_model", "gemini-2.5-flash-image")
        set(v) = putS("img_model", v.trim().removePrefix("models/"))
    var liteMode: Boolean
        get() = b("lite", false)
        set(v) = putB("lite", v)
    var confirmSeconds: Int
        get() = sp.getInt("confirm_s", 5)
        set(v) = sp.edit().putInt("confirm_s", v.coerceIn(0, 15)).apply()
    var edgeLight: Boolean
        get() = b("edge", true)
        set(v) = putB("edge", v)
    var edgeThickness: Int
        get() = sp.getInt("edge_dp", 5)
        set(v) = sp.edit().putInt("edge_dp", v.coerceIn(2, 14)).apply()
    var edgeSpeed: Int
        get() = sp.getInt("edge_speed", 2)
        set(v) = sp.edit().putInt("edge_speed", v.coerceIn(1, 4)).apply()
    var overlayAsked: Boolean
        get() = b("overlay_asked", false)
        set(v) = putB("overlay_asked", v)
    // Extra providers
    var provider: String
        get() = s("provider", "gemini")
        set(v) = putS("provider", v)
    var oaiBase: String
        get() = s("oai_base", "https://api.openai.com/v1")
        set(v) = putS("oai_base", v.trim().trimEnd('/'))
    var oaiModel: String
        get() = s("oai_model", "")
        set(v) = putS("oai_model", v.trim())
    var claudeModel: String
        get() = s("claude_model", "")
        set(v) = putS("claude_model", v.trim())
    // Safety / extras
    var emergencyNumbers: String
        get() = s("sos_numbers", "")
        set(v) = putS("sos_numbers", v)
    var githubUser: String
        get() = s("gh_user", "")
        set(v) = putS("gh_user", v.trim())
    var parking: String
        get() = s("parking", "")
        set(v) = putS("parking", v)
    var habits: String
        get() = s("habits", "{}")
        set(v) = putS("habits", v)
    var rules: String
        get() = s("rules", "[]")
        set(v) = putS("rules", v)
    var wakeEnabled: Boolean
        get() = b("wake_on", false)
        set(v) = putB("wake_on", v)
    var a11yAllowed: Boolean
        get() = b("a11y_allowed", true)
        set(v) = putB("a11y_allowed", v)

    fun sttLocale(): String = if (language == "english") "en-IN" else "hi-IN"

    // ---- encrypted secrets ----
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    fun saveSecret(name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(value.trim().toByteArray())
        sec.edit().putString("s_$name", Base64.encodeToString(out, Base64.NO_WRAP)).apply()
    }

    fun secret(name: String): String? {
        val enc = sec.getString("s_$name", null) ?: return null
        return try {
            val d = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, d.copyOfRange(0, 12)))
            String(c.doFinal(d, 12, d.size - 12))
        } catch (e: Exception) {
            null
        }
    }

    fun removeSecret(name: String) = sec.edit().remove("s_$name").apply()

    fun apiKey(): String? = secret("gemini")
    fun saveApiKey(k: String) = saveSecret("gemini", k)
    fun removeApiKey() = removeSecret("gemini")
    fun hasApiKey() = apiKey() != null

    fun wipeAll() {
        sp.edit().clear().apply(); sec.edit().clear().apply()
    }

    companion object {
        private const val ALIAS = "munawar_api_key"
    }
}
