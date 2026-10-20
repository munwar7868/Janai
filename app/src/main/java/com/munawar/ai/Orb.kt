package com.munawar.ai

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Munawar AI orb in 3D: logo sphere with moving highlight (follows phone tilt), three colourful orbit rings that pass
 * in front of and behind the sphere, a glowing pedestal, audio-reactive edge bars and a colour-cycling halo.
 */
class OrbView(ctx: Context) : View(ctx) {
    enum class S { IDLE, LISTENING, THINKING, SPEAKING }

    var state: S = S.IDLE
        set(v) { if (field != v) { field = v; invalidate() } }

    /** 0..1 live audio level (mic while listening, playback while speaking). */
    @Volatile var level = 0f

    /** Lite mode: no edge bars, half the frame rate. */
    var lite = false

    /** Always-listening (wake word) is active. */
    var standby = false
        set(v) { field = v; invalidate() }

    private val logo: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.orb_logo)
    private val logoShader = BitmapShader(logo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val logoMatrix = Matrix()
    private val ringMatrix = Matrix()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = logoShader }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val hsv = floatArrayOf(0f, 0.75f, 1f)
    private val gHsv = floatArrayOf(0f, 0.75f, 1f)
    private val hsv2 = floatArrayOf(0f, 0.8f, 1f)
    private val oval = RectF()
    private val n = 56
    private val cosT = FloatArray(n) { cos(Math.toRadians(it * 360.0 / n)).toFloat() }
    private val sinT = FloatArray(n) { sin(Math.toRadians(it * 360.0 / n)).toFloat() }
    private val sweeps = arrayOfNulls<SweepGradient>(3)
    private val tilts = floatArrayOf(-24f, 18f, 68f)
    private val dirs = floatArrayOf(1f, -1f, 1f)
    private var sm = 0f
    private var hue = 0f
    private var lastNs = System.nanoTime()
    private val t0 = lastNs
    private var anim: ValueAnimator? = null
    private var skip = 0
    private var shadeKey = -1
    private var hlShader: Shader? = null
    private var shShader: Shader? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (!lite || (++skip and 1) == 0) invalidate() }
            start()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) anim?.resume() else anim?.pause()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel(); anim = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f; val cy = h * CY
        val palettes = arrayOf(
            intArrayOf(Color.parseColor("#22D3EE"), Color.parseColor("#3B82F6"), Color.parseColor("#8B5CF6"), Color.parseColor("#22D3EE")),
            intArrayOf(Color.parseColor("#F472B6"), Color.parseColor("#F59E0B"), Color.parseColor("#F43F5E"), Color.parseColor("#F472B6")),
            intArrayOf(Color.parseColor("#84CC16"), Color.parseColor("#22D3EE"), Color.parseColor("#A78BFA"), Color.parseColor("#84CC16"))
        )
        for (i in 0..2) sweeps[i] = SweepGradient(cx, cy, palettes[i], null)
        shadeKey = -1
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceAtMost(0.1f)
        lastNs = now
        val t = (now - t0) / 1e9f
        val density = resources.displayMetrics.density
        val big = w > 140 * density
        val cx = w / 2f; val cy = h * CY
        val full = minOf(w, h) / 2f
        val r = if (big) full * 0.6f else full * 0.7f
        val tx = Parallax.tx; val ty = Parallax.ty

        val target = when (state) {
            S.SPEAKING -> maxOf(level, 0.22f + 0.2f * abs(sin(t * 9f)))
            S.LISTENING -> level
            S.THINKING -> 0.12f + 0.1f * abs(sin(t * 3f))
            S.IDLE -> 0f
        }
        sm += (target - sm) * 0.3f
        val hueSpeed = when (state) { S.IDLE -> 70f; S.LISTENING -> 130f; S.THINKING -> 280f; S.SPEAKING -> 170f }
        hue = (hue + dt * hueSpeed) % 360f
        gHsv[0] = hue
        val col = Color.HSVToColor(gHsv)
        val breathe = 1f + 0.02f * sin(t * 1.6f) + sm * 0.05f
        val rb = r * breathe

        // 1. colour-cycling halo
        val glowA = when (state) { S.IDLE -> 80; S.LISTENING -> 160 + (sm * 90).toInt(); else -> 130 }.coerceAtMost(255)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, full * 1.05f, intArrayOf(col, Color.TRANSPARENT), floatArrayOf(0.4f, 1f), Shader.TileMode.CLAMP)
        p.alpha = glowA
        c.drawCircle(cx, cy, full * 1.05f, p)
        p.shader = null; p.alpha = 255

        // 2. pedestal
        if (big) pedestal(c, cx, cy + rb * 1.22f + ty * rb * 0.05f, rb, t, col)

        // 3. back halves of the orbit rings (behind the sphere)
        rings(c, w, h, cx, cy, rb, t, true)

        // 4. audio-reactive edge bars
        if (!lite && big) {
            barPaint.strokeWidth = full * 0.016f + 2f
            val base = if (state == S.IDLE) 60 else 110 + (sm * 145).toInt()
            for (i in 0 until n) {
                val amp = if (state == S.IDLE) 0.02f else 0.05f + sm * 0.45f * (0.5f + 0.5f * sin(t * 7f + i * 0.9f))
                val r0 = rb * 1.06f; val r1 = r0 + rb * amp
                hsv[0] = (i * 6.4f + t * 50f) % 360f
                barPaint.color = Color.HSVToColor(base.coerceAtMost(255), hsv)
                c.drawLine(cx + cosT[i] * r0, cy + sinT[i] * r0, cx + cosT[i] * r1, cy + sinT[i] * r1, barPaint)
            }
        }

        // 5. logo sphere (slightly shifts with tilt = depth)
        val rr = rb * 0.93f
        logoMatrix.reset()
        val sc = 2f * rr / logo.width
        logoMatrix.postScale(sc, sc)
        logoMatrix.postTranslate(cx - rr + tx * rr * 0.05f, cy - rr + ty * rr * 0.05f)
        logoShader.setLocalMatrix(logoMatrix)
        c.drawCircle(cx, cy, rr, logoPaint)

        // 6. 3D shading: highlight follows the tilt
        val key = (rr * 2f).toInt() * 100000 + ((tx * 10f).toInt() + 20) * 100 + ((ty * 10f).toInt() + 20)
        if (key != shadeKey || hlShader == null) {
            shadeKey = key
            hlShader = RadialGradient(cx - rr * 0.35f + tx * rr * 0.3f, cy - rr * 0.4f + ty * rr * 0.3f, rr * 0.9f,
                intArrayOf(Color.argb(115, 255, 255, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
            shShader = RadialGradient(cx + rr * 0.3f - tx * rr * 0.2f, cy + rr * 0.4f - ty * rr * 0.2f, rr * 1.15f,
                intArrayOf(Color.TRANSPARENT, Color.argb(150, 0, 0, 20)), floatArrayOf(0.55f, 1f), Shader.TileMode.CLAMP)
        }
        p.shader = hlShader; c.drawCircle(cx, cy, rr, p)
        p.shader = shShader; c.drawCircle(cx, cy, rr, p)
        p.shader = null

        // 7. front halves of the orbit rings (in front of the sphere)
        rings(c, w, h, cx, cy, rb, t, false)

        // 8. sparkles circling the orb
        if (big) {
            p.style = Paint.Style.FILL
            for (k in 0..5) {
                val a = t * (0.9f + sm) + k * 1.047f
                val x = cx + cos(a) * rb * 1.55f
                val y = cy + sin(a) * rb * 0.5f
                hsv2[0] = (hue + k * 60f) % 360f
                p.color = Color.HSVToColor(hsv2)
                p.alpha = (140 + 100 * sin(a)).toInt().coerceIn(40, 255)
                c.drawCircle(x, y, full * 0.02f + 1.5f, p)
            }
            p.alpha = 255
        }

        // 9. thinking dots
        if (state == S.THINKING) {
            p.style = Paint.Style.FILL; p.color = Color.WHITE
            for (k in 0..2) {
                val a = Math.toRadians(t * 240.0 + k * 120.0)
                c.drawCircle(cx + cos(a).toFloat() * rb * 1.2f, cy + sin(a).toFloat() * rb * 1.2f, full * 0.025f + 2f, p)
            }
        }

        // 10. always-listening indicator
        if (standby) {
            val pulse = 0.5f + 0.5f * sin(t * 3f)
            p.style = Paint.Style.FILL
            p.color = Color.parseColor("#22C55E")
            p.alpha = (120 + 135 * pulse).toInt()
            c.drawCircle(w - full * 0.12f, full * 0.12f, full * 0.05f + 2f + pulse * 2f, p)
            p.alpha = 255
        }
    }

    private fun rings(c: Canvas, w: Float, h: Float, cx: Float, cy: Float, r: Float, t: Float, back: Boolean) {
        val sw = r * 0.03f + 1.5f
        val mul = if (state == S.IDLE) 0.8f else 1f
        for (i in 0..2) {
            val sh = sweeps[i] ?: continue
            val rx = r * (1.4f + i * 0.17f)
            val ry = rx * (0.28f + i * 0.09f)
            val tilt = tilts[i] + sin(t * 0.5f + i) * 6f + Parallax.tx * 9f
            val speed = (40f + i * 35f) * (1f + sm * 1.5f) * dirs[i]
            ringMatrix.setRotate(t * speed, cx, cy)
            sh.setLocalMatrix(ringMatrix)
            ringPaint.shader = sh
            c.save()
            if (back) c.clipRect(0f, 0f, w, cy) else c.clipRect(0f, cy, w, h)
            c.rotate(tilt, cx, cy)
            oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
            ringPaint.strokeWidth = sw * 3.2f; ringPaint.alpha = (45 * mul).toInt(); c.drawOval(oval, ringPaint)
            ringPaint.strokeWidth = sw; ringPaint.alpha = ((if (back) 150 else 255) * mul).toInt(); c.drawOval(oval, ringPaint)
            c.restore()
        }
        ringPaint.shader = null
    }

    private fun pedestal(c: Canvas, cx: Float, y: Float, r: Float, t: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, y, r * 1.5f, intArrayOf(col, Color.TRANSPARENT), floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP)
        p.alpha = 150
        c.save(); c.scale(1f, 0.22f, cx, y); c.drawCircle(cx, y, r * 1.5f, p); c.restore()
        p.shader = null; p.alpha = 255
        for (i in 0..2) {
            val rx = r * (1.3f - i * 0.28f)
            val ry = rx * 0.2f
            hsv2[0] = (hue + 90f * i) % 360f
            ringPaint.color = Color.HSVToColor(hsv2)
            ringPaint.strokeWidth = r * 0.035f + 1.5f
            ringPaint.alpha = (140 + 100 * sin(t * 2f - i)).toInt().coerceIn(40, 255)
            oval.set(cx - rx, y - ry, cx + rx, y + ry)
            c.drawOval(oval, ringPaint)
        }
    }

    companion object { private const val CY = 0.44f }
}
