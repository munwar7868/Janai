package com.munawar.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Other chat brains: any OpenAI-compatible endpoint (OpenAI, Groq, OpenRouter, local servers...) and Anthropic Claude. Plain chat only. */
object Providers {
    private fun post(url: String, headers: Map<String, String>, body: String): String {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.connectTimeout = 15000; c.readTimeout = 90000
            c.setRequestProperty("Content-Type", "application/json")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    val e = JSONObject(text).opt("error")
                    if (e is JSONObject) e.optString("message") else e?.toString() ?: ""
                } catch (x: Exception) { "" }
                throw GeminiException(
                    when (code) {
                        401, 403 -> "The API key was rejected ($code). Check it in Settings."
                        404 -> "Model or URL not found. Check the model name / base URL in Settings."
                        429 -> "Rate limit or quota reached."
                        else -> "Error $code: ${msg.take(160)}"
                    }
                )
            }
            return text
        } catch (e: IOException) {
            throw GeminiException("Network problem. Check your internet connection.")
        }
    }

    suspend fun chat(p: Prefs, provider: String, system: String, turns: List<Turn>): String = withContext(Dispatchers.IO) {
        val key = p.secret(provider) ?: throw GeminiException("No API key saved for $provider. Add it in Settings → AI brain.")
        val msgs = JSONArray()
        for (t in turns.dropWhile { it.role != "user" }) {
            msgs.put(JSONObject().put("role", if (t.role == "model") "assistant" else "user").put("content", t.text))
        }
        val out: String
        if (provider == "claude") {
            val model = p.claudeModel
            if (model.isEmpty()) throw GeminiException("Set the Claude model name in Settings → AI brain.")
            val body = JSONObject().put("model", model).put("max_tokens", 1024).put("system", system).put("messages", msgs)
            val res = JSONObject(post("https://api.anthropic.com/v1/messages", mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"), body.toString()))
            val arr = res.optJSONArray("content") ?: JSONArray()
            val sb = StringBuilder()
            for (i in 0 until arr.length()) sb.append(arr.getJSONObject(i).optString("text"))
            out = sb.toString()
        } else {
            val model = p.oaiModel
            if (model.isEmpty()) throw GeminiException("Set the model name in Settings → AI brain.")
            val all = JSONArray().put(JSONObject().put("role", "system").put("content", system))
            for (i in 0 until msgs.length()) all.put(msgs.getJSONObject(i))
            val body = JSONObject().put("model", model).put("messages", all)
            val res = JSONObject(post(p.oaiBase.trimEnd('/') + "/chat/completions", mapOf("Authorization" to "Bearer $key"), body.toString()))
            out = res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        }
        val r = out.trim()
        if (r.isEmpty()) throw GeminiException("The model returned an empty answer.")
        Stats.ok(0)
        r
    }
}
