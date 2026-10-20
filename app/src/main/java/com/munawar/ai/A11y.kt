package com.munawar.ai

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Optional accessibility control. Only active when the user enabled it in Android Settings AND the in-app
 * "Allow actions" switch is on. Acts only when a command is given; it does not record or log screen content.
 * Password fields are never read or typed into.
 */
class MunawarAccessibility : AccessibilityService() {
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { if (inst === this) inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (inst === this) inst = null; super.onDestroy() }

    companion object { @Volatile var inst: MunawarAccessibility? = null }

    fun screenText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        walk(root, sb)
        return sb.toString().take(6000)
    }

    private fun walk(n: AccessibilityNodeInfo, sb: StringBuilder) {
        if (sb.length > 6000 || !n.isVisibleToUser || n.isPassword) return
        val t = n.text?.toString() ?: n.contentDescription?.toString()
        if (!t.isNullOrBlank()) sb.append(t.trim()).append('\n')
        for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, sb) }
    }

    /** True only if the target app accepted the click. */
    fun clickText(q: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(q)) {
            if (n.isPassword) continue
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } ?: findEditable(root) ?: return false
        if (f.isPassword) return false
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun findEditable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isEditable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findEditable(c)?.let { return it } }
        return null
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val s = findScrollable(root) ?: return false
        return s.performAction(if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable && n.isVisibleToUser) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findScrollable(c)?.let { return it } }
        return null
    }

    /** Clicks a button whose text/description equals one of [names] (e.g. WhatsApp "Send"). True if the app accepted the click. */
    fun clickByLabel(names: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (name in names) {
            for (n in root.findAccessibilityNodeInfosByText(name)) {
                val d = (n.contentDescription?.toString() ?: n.text?.toString() ?: "").trim()
                if (!d.equals(name, true) || n.isPassword) continue
                var c: AccessibilityNodeInfo? = n
                while (c != null && !c.isClickable) c = c.parent
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    /** On a YouTube results page: taps the first video result. */
    fun clickFirstVideo(): Boolean {
        val root = rootInActiveWindow ?: return false
        val n = findVideo(root) ?: return false
        var c: AccessibilityNodeInfo? = n
        while (c != null && !c.isClickable) c = c.parent
        return c?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private val videoRe = Regex("(?i)(play video|\\d+ (minutes?|seconds?|hours?)|views|मिनट|सेकंड)")

    private fun findVideo(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val d = n.contentDescription?.toString()
        if (d != null && d.length > 20 && n.isVisibleToUser && !d.contains("shorts", true) && videoRe.containsMatchIn(d)) return n
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> findVideo(c)?.let { return it } }
        return null
    }

    fun global(action: Int): Boolean = performGlobalAction(action)

    fun screenshot(cb: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { cb(null); return }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(r: AccessibilityService.ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val bmp = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val sw = bmp?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    cb(sw)
                }
                override fun onFailure(code: Int) { cb(null) }
            })
        } catch (e: Exception) { cb(null) }
    }
}

object A11y {
    @Volatile var allowed = true
    const val NEEDS = "Turn on Accessibility control first: Control tab → Accessibility (and switch 'Allow actions' on)."
    fun svc(): MunawarAccessibility? = if (allowed) MunawarAccessibility.inst else null
}

/** Explains whatever is on screen using a screenshot (Android 11+) or the screen text. */
object ScreenAI {
    suspend fun explain(prefs: Prefs, question: String): Pair<Boolean, String> {
        val key = prefs.apiKey() ?: return Pair(false, "Add your Gemini API key in Settings first.")
        val s = A11y.svc() ?: return Pair(false, A11y.NEEDS)
        var att: Attachment? = null
        if (Build.VERSION.SDK_INT >= 30) {
            val bmp = withTimeoutOrNull(4000) {
                suspendCancellableCoroutine<Bitmap?> { c -> s.screenshot { if (c.isActive) c.resume(it) } }
            }
            if (bmp != null) att = Attachment("image/jpeg", Media.bitmapToB64(bmp))
        }
        var text = ""
        if (att == null) {
            text = s.screenText()
            if (text.isBlank()) return Pair(false, "Couldn't read this screen (some apps block screen access).")
        }
        val prompt = if (att != null) "Explain what is on my phone screen and what I can do here. Request: $question"
        else "This is the text on my phone screen:\n$text\n\nExplain it simply. Request: $question"
        return try {
            Pair(true, Gemini.generate(key, prefs.model, 0.5f, Persona.build(prefs, emptyList(), false, true), listOf(Turn("user", prompt)), false, att))
        } catch (e: GeminiException) { Pair(false, e.message ?: "Failed") }
    }
}
