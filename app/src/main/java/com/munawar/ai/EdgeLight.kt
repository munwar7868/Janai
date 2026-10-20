package com.munawar.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Path
import android.graphics.PathMeasure
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlin.math.max
import kotlin.math.min

/**
 * Edge lighting: a colour wave that runs along the WHOLE screen border (all four edges and the rounded corners).
 * The border is cut into segments; every segment gets its own hue and the hues travel around the loop, so the colours
 * keep changing while they flow. Corner radius follows the phone's real display corners when Android reports them.
 */
class EdgeLightView(ctx: Context) : View(ctx) {
    var state: OrbView.S = OrbView.S.IDLE
    @Volatile var level = 0f
    var lightOn = true
    var thicknessDp = 5
        set(v) { field = v; segs = arrayOfNulls(segCount) }
    var speed = 2

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hsv = floatArrayOf(0f, 0.9f, 1f)
    private val segCount = 72
    private var segs: Array<Path?> = arrayOfNulls(segCount)
    private var anim: ValueAnimator? = null
    private var phase = 0f
    private var last = System.nanoTime()
    private var corner = 0f
    private var lite = false
    private var skip = 0

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lite = Core.prefs.liteMode
        if (Build.VERSION.SDK_INT >= 31) {
            val ins: WindowInsets? = rootWindowInsets
            val r = ins?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)?.radius
            if (r != null && r > 0) corner = r.toFloat()
        }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (lightOn && (!lite || (++skip and 1) == 0)) invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild(w.toFloat(), h.toFloat()) }

    private fun rebuild(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        val inset = dp(thicknessDp) * 1.0f + 2f
        val r = if (corner > 0f) corner - inset * 0.5f else max(dp(26), min(w, h) * 0.085f)
        val loop = Path()
        loop.addRoundRect(RectF(inset, inset, w - inset, h - inset), r, r, Path.Direction.CW)
        val pm = PathMeasure(loop, false)
        val len = pm.length
        for (i in 0 until segCount) {
            val sp = Path()
            pm.getSegment(len * i / segCount, len * (i + 1) / segCount + 2f, sp, true)
            segs[i] = sp
        }
    }

    override fun onDraw(c: Canvas) {
        if (!lightOn) return
        if (segs[0] == null) rebuild(width.toFloat(), height.toFloat())
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
        last = now
        val base = when (state) { OrbView.S.IDLE -> 90f; OrbView.S.LISTENING -> 260f; OrbView.S.THINKING -> 480f; OrbView.S.SPEAKING -> 340f }
        phase = (phase + dt * (base + level * 300f) * (0.5f + speed * 0.5f)) % 360f
        val t = dp(thicknessDp) * (1f + level * 0.8f)
        glowPaint.strokeWidth = t * 2.6f
        corePaint.strokeWidth = t
        val boost = if (state == OrbView.S.IDLE) 0.6f else 1f
        val ga = (80 * boost).toInt()
        val ca = (255 * boost).toInt()
        for (i in 0 until segCount) {
            val sp = segs[i] ?: continue
            hsv[0] = (i * 720f / segCount + phase) % 360f   // two full colour cycles around the screen
            val colour = Color.HSVToColor(hsv)
            glowPaint.color = colour; glowPaint.alpha = ga
            corePaint.color = colour; corePaint.alpha = ca
            c.drawPath(sp, glowPaint)
            c.drawPath(sp, corePaint)
        }
    }

    private fun dp(v: Int) = v * resources.displayMetrics.density
}

/** System-wide edge light (above every app) when 'Display over other apps' is allowed. Also keeps a visible overlay window while a session runs, which lets Android allow background app launches. */
object EdgeLight {
    private var view: EdgeLightView? = null
    val systemShown: Boolean get() = view != null

    fun show(ctx: Context) {
        if (view != null || !Perms.overlay(ctx)) return
        val app = ctx.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val v = EdgeLightView(app)
        apply(v)
        val (w, h) = realSize(app, wm)
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try { wm.addView(v, lp); view = v } catch (e: Exception) { view = null }
    }

    fun hide() {
        val v = view ?: return
        try { (v.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) {}
        view = null
    }

    fun refreshPrefs() { view?.let { apply(it) } }

    fun update(s: OrbView.S, level: Float) { view?.let { it.state = s; it.level = level } }

    private fun apply(v: EdgeLightView) {
        val p = Core.prefs
        v.lightOn = p.edgeLight; v.thicknessDp = p.edgeThickness; v.speed = p.edgeSpeed
    }

    @Suppress("DEPRECATION")
    private fun realSize(ctx: Context, wm: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) { val b = wm.currentWindowMetrics.bounds; return Pair(b.width(), b.height()) }
        val m = android.util.DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }
}
