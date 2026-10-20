package com.munawar.ai

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*

object Theme {
    val BG = Color.parseColor("#0A0714")
    val CARD = Color.parseColor("#D9161026")
    val PURPLE = Color.parseColor("#8B5CF6")
    val PURPLE_DARK = Color.parseColor("#4C2A8C")
    val CYAN = Color.parseColor("#22D3EE")
    val PINK = Color.parseColor("#F472B6")
    val TEXT = Color.parseColor("#EDE9FE")
    val MUTED = Color.parseColor("#9CA3AF")
    val RED = Color.parseColor("#EF4444")
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun gradBg(c: Int, r: Float) = GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(c, Color.rgb(minOf(255, Color.red(c) + 60), minOf(255, Color.green(c) + 40), minOf(255, Color.blue(c) + 70)))
).apply { cornerRadius = r }

fun roundBg(color: Int, radius: Float, stroke: Int = 0) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke != 0) setStroke(2, stroke)
}

/** LinearLayout params; w/h in px (or MATCH/WRAP), margin m in dp. */
fun Context.lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, m: Int = 0) =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(m), dp(m), dp(m), dp(m)) }

fun Context.vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.hbox() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }

fun Context.label(text: String, size: Float = 15f, color: Int = Theme.TEXT, bold: Boolean = false) =
    TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.button(text: String, color: Int = Theme.PURPLE, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(Color.WHITE); textSize = 14f
    minimumHeight = dp(48); minHeight = dp(48)
    background = if (color == Theme.CARD) roundBg(Theme.CARD, dp(24).toFloat(), Theme.PURPLE_DARK) else gradBg(color, dp(24).toFloat()); setPadding(dp(16), 0, dp(16), 0)
    setOnClickListener { onClick() }
}

fun Context.field(hint: String, password: Boolean = false, multi: Boolean = false) = EditText(this).apply {
    this.hint = hint; setHintTextColor(Theme.MUTED); setTextColor(Theme.TEXT); textSize = 15f
    background = roundBg(Theme.CARD, dp(12).toFloat(), Theme.PURPLE_DARK)
    setPadding(dp(12), dp(10), dp(12), dp(10))
    inputType = when {
        password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        else -> InputType.TYPE_CLASS_TEXT
    }
    if (password) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    if (multi) maxLines = 5
}

fun Context.scroll(content: View) = ScrollView(this).apply { addView(content) }

fun Context.section(title: String) = label(title, 17f, Theme.CYAN, true).apply { setPadding(0, dp(18), 0, dp(6)) }

fun Context.card() = vbox().apply {
    background = roundBg(Theme.CARD, dp(16).toFloat()); setPadding(dp(14), dp(12), dp(14), dp(12))
}

fun Context.seek(max: Int, progress: Int, onChange: (Int) -> Unit) = SeekBar(this).apply {
    this.max = max; this.progress = progress
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    })
}

fun Context.radio(options: List<Pair<String, String>>, current: String, onSel: (String) -> Unit) = RadioGroup(this).apply {
    options.forEachIndexed { idx, (value, text) ->
        addView(RadioButton(context).apply {
            id = 5000 + idx; this.text = text; setTextColor(Theme.TEXT); isChecked = value == current
            minimumHeight = dp(44)
        })
    }
    setOnCheckedChangeListener { _, id -> onSel(options[id - 5000].first) }
}

/** Title text with a colour gradient that keeps sliding. */
class RainbowText(ctx: Context, txt: String, size: Float) : TextView(ctx) {
    private var sh: LinearGradient? = null
    private val m = Matrix()
    private var anim: ValueAnimator? = null

    init {
        text = txt; textSize = size; setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.06f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sh = LinearGradient(
            0f, 0f, maxOf(w, 1).toFloat(), 0f,
            intArrayOf(Theme.CYAN, Theme.PURPLE, Theme.PINK, Color.parseColor("#F59E0B"), Theme.CYAN), null, Shader.TileMode.MIRROR
        )
        paint.shader = sh
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1600; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { m.setTranslate(width * 2f * it.animatedFraction, 0f); sh?.setLocalMatrix(m); invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
}

/**
 * Deep-space background: twinkling stars, two planets and three drifting colour-cycling nebula clouds.
 * Layers move with the phone tilt (parallax) for a 3D feel. Draws at ~30 fps and stops while the app is hidden.
 */
class AuroraView(ctx: Context) : View(ctx) {
    @Volatile var paused = false
    var lite = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(0f, 0.8f, 1f)
    private val t0 = System.nanoTime()
    private var anim: ValueAnimator? = null
    private var frame = 0
    private val starCount = 70
    private val sx = FloatArray(starCount)
    private val sy = FloatArray(starCount)
    private val ss = FloatArray(starCount)
    private val sd = FloatArray(starCount)

    init {
        val r = java.util.Random(7)
        for (i in 0 until starCount) { sx[i] = r.nextFloat(); sy[i] = r.nextFloat(); ss[i] = 0.5f + r.nextFloat() * 1.8f; sd[i] = 0.3f + r.nextFloat() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1000; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { if (!paused && !lite && ++frame % 2 == 0) invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val d = resources.displayMetrics.density
        val t = (System.nanoTime() - t0) / 1e9f
        val tx = Parallax.tx; val ty = Parallax.ty
        c.drawColor(Color.parseColor("#05030F"))
        // nebula clouds
        val r = maxOf(w, h) * 0.6f
        for (i in 0..2) {
            val cx = w * (0.5f + 0.45f * kotlin.math.sin(t * 0.45f + i * 2.1f)) + tx * 18f * d
            val cy = h * (0.5f + 0.45f * kotlin.math.cos(t * 0.33f + i * 1.7f)) + ty * 18f * d
            hsv[0] = (t * 70f + i * 120f) % 360f
            p.shader = RadialGradient(cx, cy, r, Color.HSVToColor(95, hsv), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, p)
        }
        p.shader = null
        // stars (deeper stars move less)
        p.style = Paint.Style.FILL
        for (i in 0 until starCount) {
            val x = sx[i] * w + tx * 14f * d * sd[i]
            val y = sy[i] * h + ty * 14f * d * sd[i]
            val a = 90 + (120 * (0.5f + 0.5f * kotlin.math.sin(t * (1.5f + sd[i]) + i))).toInt()
            p.color = Color.WHITE; p.alpha = a.coerceIn(0, 255)
            c.drawCircle(x, y, ss[i] * d, p)
        }
        p.alpha = 255
        // planets
        planet(c, w * 0.86f + tx * 30f * d, h * 0.16f + ty * 24f * d, 34f * d, Color.parseColor("#C084FC"), Color.parseColor("#312E81"), true, t)
        planet(c, w * 0.06f + tx * 36f * d, h * 0.52f + ty * 28f * d, 48f * d, Color.parseColor("#F59E0B"), Color.parseColor("#451A03"), false, t)
    }

    private fun planet(c: Canvas, x: Float, y: Float, r: Float, light: Int, dark: Int, ring: Boolean, t: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(x - r * 0.4f, y - r * 0.4f, r * 1.5f, light, dark, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
        p.shader = null
        if (ring) {
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.12f; p.color = Color.WHITE; p.alpha = 150
            c.save(); c.rotate(-20f, x, y)
            c.drawOval(RectF(x - r * 1.9f, y - r * 0.5f, x + r * 1.9f, y + r * 0.5f), p)
            c.restore()
            p.style = Paint.Style.FILL; p.alpha = 255
        }
    }
}

/**
 * Glass tile with a colour-cycling neon border, big icon, subtitle and a 3D press-tilt. [hue] is its base colour.
 */
class TileView(ctx: Context, emoji: String, title: String, subtitle: String, private val hue: Float, onClick: () -> Unit) : LinearLayout(ctx) {
    private val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.HSVToColor(110, floatArrayOf(hue, 0.8f, 0.55f)), Color.parseColor("#E6080414")))
        .apply { cornerRadius = ctx.dp(24).toFloat(); setStroke(ctx.dp(2), Color.HSVToColor(floatArrayOf(hue, 0.8f, 1f))) }
    private val strokeHsv = floatArrayOf(hue, 0.85f, 1f)

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        background = bg
        setPadding(ctx.dp(8), ctx.dp(12), ctx.dp(8), ctx.dp(10))
        cameraDistance = 9000f * ctx.resources.displayMetrics.density
        elevation = ctx.dp(8).toFloat()
        addView(TextView(ctx).apply {
            text = emoji; textSize = 36f; gravity = Gravity.CENTER
            setShadowLayer(ctx.dp(14).toFloat(), 0f, 0f, Color.HSVToColor(floatArrayOf(hue, 0.7f, 1f)))
        })
        addView(TextView(ctx).apply { text = title; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD) })
        addView(TextView(ctx).apply { text = subtitle; textSize = 10.5f; setTextColor(Theme.MUTED); gravity = Gravity.CENTER; maxLines = 2 })
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val nx = (e.x / v.width - 0.5f).coerceIn(-0.5f, 0.5f); val ny = (e.y / v.height - 0.5f).coerceIn(-0.5f, 0.5f)
                    v.rotationY = nx * 22f; v.rotationX = -ny * 22f; v.scaleX = 0.96f; v.scaleY = 0.96f
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).setDuration(160).start()
                    v.performClick(); onClick()
                }
                MotionEvent.ACTION_CANCEL -> v.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            true
        }
    }

    /** Animate the neon border: [shift] degrees away from the base hue. */
    fun glow(shift: Float) {
        strokeHsv[0] = (hue + shift + 360f) % 360f
        bg.setStroke(context.dp(2), Color.HSVToColor(strokeHsv))
    }
}

/** Small animated equaliser bars (idle = gentle, active = lively). */
class WaveView(ctx: Context) : View(ctx) {
    var active = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var anim: ValueAnimator? = null
    private val t0 = System.nanoTime()
    private val hsv = floatArrayOf(0f, 0.8f, 1f)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim = ValueAnimator.ofFloat(0f, 1f).apply { duration = 1000; repeatCount = ValueAnimator.INFINITE; addUpdateListener { invalidate() }; start() }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val t = (System.nanoTime() - t0) / 1e9f
        val n = 14
        val gap = w / n
        p.strokeWidth = gap * 0.45f
        for (i in 0 until n) {
            val amp = if (active) 0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(t * 6f + i * 0.7f)) else 0.18f + 0.12f * kotlin.math.sin(t * 2f + i)
            hsv[0] = (200f + i * 12f + t * 40f) % 360f
            p.color = Color.HSVToColor(hsv)
            val x = gap * (i + 0.5f)
            c.drawLine(x, h / 2f - h * amp / 2f, x, h / 2f + h * amp / 2f, p)
        }
    }
}
