package com.funny.ludo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Main menu + the "how many players" setup screen. */
class MenuView(
    ctx: Context, private val sfx: Sfx,
    private val onStart: (GameConfig) -> Unit,
    private val onPanel: (String) -> Unit
) : View(ctx) {
    private val profile = Profile.get(ctx)


    private enum class Screen { MAIN, SETUP }
    private enum class Mode { CLASSIC, FRIENDS, BOTS }

    private class Btn(val id: String, val r: RectF)

    private var screen = Screen.MAIN
    private var mode = Mode.CLASSIC
    private var players = 4
    private var tokens = 4
    private val humans = BooleanArray(6) { it == 0 }
    private val buttons = ArrayList<Btn>()
    private var pressed: String? = null

    private var insetTop = 0f
    private var insetBottom = 0f
    private var bgBmp: Bitmap? = null
    private var pawns: Array<Bitmap>? = null
    private val dice = DiceRenderer()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tpo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rect = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        insetTop = insets.systemWindowInsetTop.toFloat()
        insetBottom = insets.systemWindowInsetBottom.toFloat()
        return super.onApplyWindowInsets(insets)
    }

    fun showMain() {
        screen = Screen.MAIN
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bgBmp = null
        rays = null
    }

    fun themeChanged() {
        bgBmp = null
        invalidate()
    }

    /** theme-coloured backdrop with a soft pattern of tilted dice tiles */
    private fun renderBackground() {
        val w = width
        val h = height
        val th = Themes.get(profile.theme)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        p.color = Color.BLACK
        p.shader = RadialGradient(w / 2f, h * 0.32f, max(w, h) * 0.95f, th.back, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null
        val step = dp(118f)
        val tile = dp(74f)
        c.save()
        c.rotate(-24f, w / 2f, h / 2f)
        var row = 0
        var yy = -h * 0.4f
        while (yy < h * 1.4f) {
            var xx = -w * 0.6f + if (row % 2 == 0) 0f else step / 2f
            var col = 0
            while (xx < w * 1.6f) {
                val tint = th.seat[(row + col * 2) % 6]
                p.color = Gfx.withAlpha(tint, 26)
                rect.set(xx, yy, xx + tile, yy + tile)
                c.drawRoundRect(rect, tile * 0.22f, tile * 0.22f, p)
                sp.color = 0x16FFFFFF
                sp.strokeWidth = dp(2f)
                c.drawRoundRect(rect, tile * 0.22f, tile * 0.22f, sp)
                p.color = 0x18FFFFFF
                val n = 1 + (row * 3 + col) % 3
                for (i in 0 until n) {
                    val f = (i + 1f) / (n + 1f)
                    c.drawCircle(xx + tile * f, yy + tile * (if (n == 1) 0.5f else f), tile * 0.09f, p)
                }
                xx += step
                col++
            }
            yy += step * 0.86f
            row++
        }
        c.restore()
        bgBmp = bmp
        val sz = dp(54f).toInt()
        pawns = Array(4) { Gfx.pawn(th.seat[it], sz, (sz * 1.32f).toInt(), th.pawn) }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        if (width == 0) return
        if (bgBmp == null) renderBackground()
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f
        val w = width.toFloat()
        val h = height.toFloat()
        buttons.clear()
        c.drawBitmap(bgBmp!!, 0f, 0f, null)

        // drifting bokeh
        for (i in 0 until 18) {
            val fx = ((i * 0.37f + t * 0.012f * (1 + i % 3)) % 1f)
            val fy = 1f - ((i * 0.23f + t * 0.02f * (1 + i % 4)) % 1f)
            p.color = Gfx.withAlpha(Seats.COLORS[i % 6], 34)
            c.drawCircle(fx * w, fy * h, dp(10f + (i % 5) * 9f), p)
        }

        if (screen == Screen.MAIN) drawMain(c, w, h, t) else drawSetup(c, w, h, t)
        postInvalidateOnAnimation()
    }

    private fun drawTitle(c: Canvas, w: Float, y: Float, t: Float, size: Float) {
        val text = "FunnyLudo"
        tp.textSize = size
        tpo.textSize = size
        tpo.strokeWidth = size * 0.2f
        tpo.color = 0xFF1B0F3F.toInt()
        var total = 0f
        val widths = FloatArray(text.length) { tp.measureText(text[it].toString()) }
        for (v in widths) total += v
        var x = (w - total) / 2f
        tp.textAlign = Paint.Align.LEFT
        tpo.textAlign = Paint.Align.LEFT
        for (i in text.indices) {
            val bob = sin(t * 3f + i * 0.6f) * dp(4f)
            val col = Seats.COLORS[(i / 2) % 4]
            // soft drop shadow
            tpo.color = 0x66000000
            c.drawText(text[i].toString(), x + dp(2f), y + dp(5f) + bob, tpo)
            tpo.color = 0xFF1B0F3F.toInt()
            c.drawText(text[i].toString(), x, y + bob, tpo)
            tp.color = Gfx.lighten(col, 0.15f)
            c.drawText(text[i].toString(), x, y + bob, tp)
            x += widths[i]
        }
        tp.textAlign = Paint.Align.CENTER
        tpo.textAlign = Paint.Align.CENTER
    }

    private fun drawMain(c: Canvas, w: Float, h: Float, t: Float) {
        val th = Themes.get(profile.theme)
        val top = max(insetTop, dp(10f))
        val bottom = max(insetBottom, dp(6f))

        // ---- top bar: avatar, sound, diamonds, coins (nothing overlaps: widths are worked out from the screen)
        val barH = dp(54f)
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, 0f, 0f, top + barH, 0xF0020A30.toInt(), 0xCC0A1E5E.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, top + barH, p)
        p.shader = null
        p.color = 0xFFFFC53D.toInt()
        c.drawRect(0f, top + barH - dp(2.5f), w, top + barH, p)

        val pad = dp(8f)
        val avS = dp(40f)
        val av = RectF(pad, top + (barH - avS) / 2f, pad + avS, top + (barH + avS) / 2f)
        buttons.add(Btn("profile", RectF(0f, top, av.right + pad / 2f, top + barH)))
        val photo = profile.avatar
        if (photo != null) {
            clip.reset()
            clip.addRoundRect(av, dp(10f), dp(10f), Path.Direction.CW)
            c.save()
            c.clipPath(clip)
            c.drawBitmap(photo, null, av, null)
            c.restore()
        } else {
            p.color = Color.BLACK
            p.shader = LinearGradient(av.left, av.top, av.left, av.bottom, Gfx.lighten(th.seat[profile.pawnColor], 0.4f), Gfx.darken(th.seat[profile.pawnColor], 0.3f), Shader.TileMode.CLAMP)
            c.drawRoundRect(av, dp(10f), dp(10f), p)
            p.shader = null
            text(c, profile.name.take(1).uppercase(), av.centerX(), av.centerY() + dp(8f), dp(22f), Color.WHITE)
        }
        sp.color = 0xFFFFC53D.toInt()
        sp.strokeWidth = dp(2f)
        c.drawRoundRect(av, dp(10f), dp(10f), sp)

        val sS = dp(38f)
        val sr = RectF(av.right + pad, top + (barH - sS) / 2f - dp(1f), av.right + pad + sS, top + (barH + sS) / 2f - dp(1f))
        buttons.add(Btn("sound", sr))
        roundIcon(c, sr, when (sfx.mode) { Sfx.Mode.MEME -> 0xFF7B3FD1.toInt(); Sfx.Mode.NORMAL -> 0xFF00897B.toInt(); Sfx.Mode.OFF -> 0xFF616161.toInt() }, 0, pressed == "sound", t)

        val pillH = dp(32f)
        val py = top + (barH - pillH) / 2f
        val free = w - sr.right - pad * 3
        val cw = min(dp(120f), free * 0.57f)
        val gw = min(dp(90f), free * 0.43f)
        val cr = RectF(w - pad - cw, py, w - pad, py + pillH)
        val gr = RectF(cr.left - pad - gw, py, cr.left - pad, py + pillH)
        wallet(c, gr, true, profile.diamonds.toString())
        wallet(c, cr, false, compact(profile.coins))
        buttons.add(Btn("store", gr))
        buttons.add(Btn("store", cr))

        // ---- logo on a spinning sunburst, with the name underneath
        val ls = min(dp(150f), h * 0.2f)
        val ly = top + barH + ls * 0.46f
        c.save()
        c.rotate(t * 12f, w / 2f, ly)
        val rr = ls * 0.95f
        p.color = Color.BLACK
        p.shader = rays ?: RadialGradient(w / 2f, ly, rr, 0x55FFF3B0, 0x00FFF3B0, Shader.TileMode.CLAMP).also { rays = it }
        for (i in 0 until 12) {
            path.reset()
            val a0 = i * Math.PI / 6
            val a1 = a0 + Math.PI / 12
            path.moveTo(w / 2f, ly)
            path.lineTo(w / 2f + (Math.cos(a0) * rr).toFloat(), ly + (Math.sin(a0) * rr).toFloat())
            path.lineTo(w / 2f + (Math.cos(a1) * rr).toFloat(), ly + (Math.sin(a1) * rr).toFloat())
            path.close()
            c.drawPath(path, p)
        }
        p.shader = null
        c.restore()
        val bob = sin(t * 2f) * dp(4f)
        val lg = logo ?: BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_fg).also { logo = it }
        if (lg != null) {
            rect.set(w / 2f - ls * 0.82f, ly - ls * 0.82f + bob, w / 2f + ls * 0.82f, ly + ls * 0.82f + bob)
            c.drawBitmap(lg, null, rect, null)
        }
        val titleY = ly + ls * 0.5f + dp(34f)
        drawTitle(c, w, titleY, t, min(w * 0.135f, dp(50f)))

        // ---- bottom tab bar
        val navH = dp(64f)
        val navTop = h - bottom - navH
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, navTop, 0f, h, 0xF0102A6B.toInt(), 0xF0050F33.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, navTop, w, h, p)
        p.shader = null
        p.color = 0xFFFFC53D.toInt()
        c.drawRect(0f, navTop, w, navTop + dp(2f), p)
        val tabs = arrayOf("HOME", "AWARDS", "RANKING", "THEMES", "STORE")
        val tabIds = arrayOf("home", "ach", "rank", "themes", "store")
        val tabKinds = intArrayOf(5, 2, 3, 4, 6)
        val tabCols = intArrayOf(0xFF1E88E5.toInt(), 0xFFFFA000.toInt(), 0xFF3949AB.toInt(), 0xFFD81B60.toInt(), 0xFF43A047.toInt())
        val tw = w / 5f
        for (i in 0 until 5) {
            val r = RectF(tw * i, navTop, tw * (i + 1), h - bottom)
            buttons.add(Btn(tabIds[i], r))
            if (i == 0) {
                p.color = Color.BLACK
                p.shader = LinearGradient(0f, r.top - dp(8f), 0f, r.bottom, 0xFF4FA8FF.toInt(), 0xFF1456C8.toInt(), Shader.TileMode.CLAMP)
                rect.set(r.left + dp(3f), r.top - dp(8f), r.right - dp(3f), r.bottom + dp(20f))
                c.drawRoundRect(rect, dp(14f), dp(14f), p)
                p.shader = null
            }
            val isz = dp(if (i == 0) 36f else 32f)
            val ic = RectF(r.centerX() - isz / 2f, r.top + dp(if (i == 0) 0f else 6f), r.centerX() + isz / 2f, r.top + dp(if (i == 0) 0f else 6f) + isz)
            roundIcon(c, ic, tabCols[i], tabKinds[i], pressed == tabIds[i], t)
            text(c, tabs[i], r.centerX(), r.bottom - dp(7f), dp(10.5f), Color.WHITE)
        }

        // ---- floating buttons above the tab bar: sound (left) and lucky spin (right)
        val fsz = dp(54f)
        val fy = navTop - dp(38f) - fsz
        val wr = RectF(w - dp(14f) - fsz, fy, w - dp(14f), fy + fsz)
        buttons.add(Btn("spin", wr))
        c.save()
        c.rotate(t * 40f, wr.centerX(), wr.centerY())
        val segCols = intArrayOf(0xFFE53935.toInt(), 0xFFFFB300.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt())
        for (i in 0 until 8) {
            p.color = segCols[i % 4]
            c.drawArc(wr, i * 45f, 45f, true, p)
        }
        c.restore()
        sp.color = 0xFFFFD54F.toInt()
        sp.strokeWidth = dp(3f)
        c.drawCircle(wr.centerX(), wr.centerY(), fsz / 2f, sp)
        p.color = 0xFFFFD54F.toInt()
        c.drawCircle(wr.centerX(), wr.centerY(), fsz * 0.2f, p)
        if (profile.spinWaitMs() == 0L) {
            p.color = 0xFFFF1744.toInt()
            c.drawCircle(wr.right - dp(4f), wr.top + dp(6f), dp(8f), p)
            text(c, "!", wr.right - dp(4f), wr.top + dp(10f), dp(11f), Color.WHITE)
        }
        text(c, "Spin", wr.centerX(), wr.bottom + dp(14f), dp(11f), Color.WHITE)

        // ---- mode tiles: 3 on the first row, 2 on the second
        val areaTop = titleY + dp(26f)
        val areaBottom = fy - dp(8f)
        val gap = dp(10f)
        val tileW = min((w - dp(24f) - gap * 2) / 3f, dp(150f))
        val tileH = min((areaBottom - areaTop - gap) / 2f, tileW * 0.92f)
        val startY = areaTop + (areaBottom - areaTop - (tileH * 2 + gap)) / 2f
        val x3 = (w - (tileW * 3 + gap * 2)) / 2f
        val row1 = arrayOf(Triple("online", "ONLINE", 0), Triple("quick", "QUICK", 1), Triple("friends", "FRIENDS", 2))
        for ((i, it) in row1.withIndex()) {
            val r = RectF(x3 + i * (tileW + gap), startY, x3 + i * (tileW + gap) + tileW, startY + tileH)
            tile(c, r, it.first, it.second, it.third, th, t)
        }
        val w2 = tileW * 1.25f
        val x2 = (w - (w2 * 2 + gap)) / 2f
        val row2 = arrayOf(Triple("bots", "COMPUTER", 3), Triple("classic", "CLASSIC", 4))
        for ((i, it) in row2.withIndex()) {
            val r = RectF(x2 + i * (w2 + gap), startY + tileH + gap, x2 + i * (w2 + gap) + w2, startY + tileH * 2 + gap)
            tile(c, r, it.first, it.second, it.third, th, t)
        }
    }

    /** dark pill with a 3D coin or diamond on the left, the amount, and a green plus button */
    private fun wallet(c: Canvas, r: RectF, gemIcon: Boolean, value: String) {
        p.color = 0xCC02103A.toInt()
        c.drawRoundRect(r, dp(8f), dp(8f), p)
        sp.color = 0xFF3D6BD6.toInt()
        sp.strokeWidth = dp(1.5f)
        c.drawRoundRect(r, dp(8f), dp(8f), sp)
        val ir = r.height() * 0.36f
        if (gemIcon) gem(c, r.left + r.height() * 0.52f, r.centerY() - dp(1f), ir) else coin(c, r.left + r.height() * 0.52f, r.centerY() - dp(1f), ir)
        val plus = RectF(r.right - r.height() + dp(4f), r.top + dp(4f), r.right - dp(4f), r.bottom - dp(4f))
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, plus.top, 0f, plus.bottom, 0xFF7BE35A.toInt(), 0xFF1E9E2E.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(plus, dp(6f), dp(6f), p)
        p.shader = null
        sp.color = Color.WHITE
        sp.strokeWidth = dp(3f)
        val u = plus.width() * 0.24f
        c.drawLine(plus.centerX() - u, plus.centerY(), plus.centerX() + u, plus.centerY(), sp)
        c.drawLine(plus.centerX(), plus.centerY() - u, plus.centerX(), plus.centerY() + u, sp)
        text(c, value, plus.left - dp(6f), r.centerY() + dp(6f), dp(16f), Color.WHITE, Paint.Align.RIGHT)
    }

    /** a big mode tile: picture on a blue panel, gold label strip, chunky 3D edge */
    private fun tile(c: Canvas, r: RectF, id: String, label: String, art: Int, th: Theme, t: Float) {
        buttons.add(Btn(id, r))
        val down = pressed == id
        val off = if (down) dp(3f) else 0f
        val rad = dp(18f)
        p.shader = null
        p.color = 0x66000000
        rect.set(r.left, r.top + dp(8f), r.right, r.bottom + dp(8f))
        c.drawRoundRect(rect, rad, rad, p)
        p.color = 0xFFB86A00.toInt()
        rect.set(r.left, r.top + dp(5f), r.right, r.bottom + dp(5f) - off)
        c.drawRoundRect(rect, rad, rad, p)
        val body = RectF(r.left, r.top + off, r.right, r.bottom + off)
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, body.top, 0f, body.bottom, 0xFFFFD75A.toInt(), 0xFFFF9D0A.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(body, rad, rad, p)
        val labelH = body.height() * 0.30f
        val pic = RectF(body.left + dp(5f), body.top + dp(5f), body.right - dp(5f), body.bottom - labelH)
        p.shader = LinearGradient(0f, pic.top, 0f, pic.bottom, 0xFF4C9BFF.toInt(), 0xFF1548B5.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(pic, rad * 0.75f, rad * 0.75f, p)
        p.shader = null
        p.color = 0x30FFFFFF
        rect.set(pic.left + dp(4f), pic.top + dp(3f), pic.right - dp(4f), pic.top + pic.height() * 0.42f)
        c.drawRoundRect(rect, rad * 0.6f, rad * 0.6f, p)

        val cx = pic.centerX()
        val cy = pic.centerY()
        val u = min(pic.width(), pic.height()) * 0.36f
        when (art) {
            0 -> {  // online: globe with two little phones
                p.color = Color.BLACK
                p.shader = RadialGradient(cx - u * 0.3f, cy - u * 0.3f, u * 1.5f, 0xFF7FD4FF.toInt(), 0xFF0B4FC2.toInt(), Shader.TileMode.CLAMP)
                c.drawCircle(cx, cy, u, p)
                p.shader = null
                p.color = 0xFF57D04C.toInt()
                c.drawCircle(cx - u * 0.35f, cy - u * 0.3f, u * 0.36f, p)
                c.drawCircle(cx + u * 0.3f, cy + u * 0.25f, u * 0.42f, p)
                c.drawCircle(cx - u * 0.05f, cy + u * 0.55f, u * 0.2f, p)
                sp.color = 0xAAFFFFFF.toInt()
                sp.strokeWidth = dp(1.5f)
                c.drawCircle(cx, cy, u, sp)
                rect.set(cx - u * 1.6f, cy - u * 0.35f, cx + u * 1.6f, cy + u * 0.35f)
                c.save()
                c.rotate(-18f + sin(t) * 4f, cx, cy)
                sp.color = 0xFFFFD54F.toInt()
                sp.strokeWidth = dp(2.5f)
                c.drawOval(rect, sp)
                c.restore()
            }
            1 -> {  // quick: lightning bolt
                path.reset()
                path.moveTo(cx + u * 0.25f, cy - u * 1.15f)
                path.lineTo(cx - u * 0.65f, cy + u * 0.15f)
                path.lineTo(cx - u * 0.05f, cy + u * 0.15f)
                path.lineTo(cx - u * 0.3f, cy + u * 1.15f)
                path.lineTo(cx + u * 0.7f, cy - u * 0.25f)
                path.lineTo(cx + u * 0.08f, cy - u * 0.25f)
                path.close()
                p.color = Color.BLACK
                p.shader = LinearGradient(0f, cy - u, 0f, cy + u, 0xFFFFF59D.toInt(), 0xFFFF9800.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(path, p)
                p.shader = null
                sp.color = Color.WHITE
                sp.strokeWidth = dp(2f)
                c.drawPath(path, sp)
            }
            2 -> {  // friends: three pawns together
                val ph = u * 2.0f
                val pw = ph / 1.32f
                for (i in intArrayOf(0, 2, 1)) {
                    val bob = sin(t * 2.4f + i) * dp(2f)
                    val px = cx + (i - 1) * pw * 0.72f
                    val s2 = if (i == 1) 1.12f else 0.92f
                    rect.set(px - pw * s2 / 2f, cy - ph * s2 / 2f + bob, px + pw * s2 / 2f, cy + ph * s2 / 2f + bob)
                    pawns?.getOrNull(i)?.let { c.drawBitmap(it, null, rect, null) }
                }
            }
            3 -> {  // computer: friendly robot head
                val hd = RectF(cx - u, cy - u * 0.7f, cx + u, cy + u * 0.85f)
                p.color = Color.BLACK
                p.shader = LinearGradient(0f, hd.top, 0f, hd.bottom, 0xFFF5F7FA.toInt(), 0xFF9FB1C7.toInt(), Shader.TileMode.CLAMP)
                c.drawRoundRect(hd, u * 0.3f, u * 0.3f, p)
                p.shader = null
                sp.color = 0xFF37474F.toInt()
                sp.strokeWidth = dp(2f)
                c.drawRoundRect(hd, u * 0.3f, u * 0.3f, sp)
                c.drawLine(cx, hd.top, cx, hd.top - u * 0.35f, sp)
                p.color = 0xFFFF5252.toInt()
                c.drawCircle(cx, hd.top - u * 0.42f, u * 0.14f, p)
                val blink = if ((t % 3f) < 0.12f) 0.2f else 1f
                p.color = 0xFF00C2FF.toInt()
                rect.set(cx - u * 0.62f, cy - u * 0.2f * blink - u * 0.05f, cx - u * 0.2f, cy + u * 0.2f * blink - u * 0.05f)
                c.drawOval(rect, p)
                rect.set(cx + u * 0.2f, cy - u * 0.2f * blink - u * 0.05f, cx + u * 0.62f, cy + u * 0.2f * blink - u * 0.05f)
                c.drawOval(rect, p)
                p.color = 0xFF37474F.toInt()
                rect.set(cx - u * 0.45f, cy + u * 0.4f, cx + u * 0.45f, cy + u * 0.58f)
                c.drawRoundRect(rect, u * 0.08f, u * 0.08f, p)
            }
            else -> {  // classic: mini board
                val b = RectF(cx - u, cy - u, cx + u, cy + u)
                p.color = Color.WHITE
                c.drawRoundRect(b, u * 0.16f, u * 0.16f, p)
                val q = u * 0.8f
                for (i in 0..3) {
                    p.color = th.seat[i]
                    val qx = if (i <= 1) b.left else b.right - q
                    val qy = if (i == 1 || i == 2) b.top else b.bottom - q
                    rect.set(qx + dp(2f), qy + dp(2f), qx + q - dp(2f), qy + q - dp(2f))
                    c.drawRoundRect(rect, u * 0.12f, u * 0.12f, p)
                }
                sp.color = 0xFF37474F.toInt()
                sp.strokeWidth = dp(1.5f)
                c.drawRoundRect(b, u * 0.16f, u * 0.16f, sp)
                dice.drawFlat(c, cx, cy, u * 0.8f, 1 + ((t / 1.2f).toInt() % 6), 255, sin(t * 2f) * 10f)
            }
        }
        // label
        var size = min(labelH * 0.52f, dp(17f))
        tp.textSize = size
        while (tp.measureText(label) > body.width() - dp(10f) && size > dp(8f)) {
            size -= dp(0.5f)
            tp.textSize = size
        }
        val ly = body.bottom - labelH / 2f + size * 0.36f
        tpo.textSize = size
        tpo.strokeWidth = size * 0.28f
        tpo.color = 0xFF12235E.toInt()
        c.drawText(label, body.centerX(), ly, tpo)
        text(c, label, body.centerX(), ly, size, Color.WHITE)
    }

    private val path = Path()
    private val clip = Path()
    private var logo: Bitmap? = null
    private var rays: Shader? = null

    private fun compact(n: Int): String = if (n >= 100000) (n / 1000).toString() + "K" else if (n >= 10000) String.format("%.1fK", n / 1000f) else n.toString()

    private fun pill(c: Canvas, r: RectF) {
        p.color = 0x66000000
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p)
        sp.color = 0x88FFFFFF.toInt()
        sp.strokeWidth = dp(1.2f)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, sp)
    }

    private fun coin(c: Canvas, cx: Float, cy: Float, r: Float) = Gfx.coin3d(c, cx, cy, r)

    private fun gem(c: Canvas, cx: Float, cy: Float, r: Float) = Gfx.gem3d(c, cx, cy, r)

    private val gemPath = Path()

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.CENTER) {
        tp.textAlign = align
        tp.textSize = size
        tp.color = color
        c.drawText(s, x, y, tp)
        tp.textAlign = Paint.Align.CENTER
    }

    /** glossy round icon button: 0 sound, 1 awards (star), 2 ranking (podium), 3 themes (palette), 4 store (coin) */
    private fun roundIcon(c: Canvas, r: RectF, color: Int, kind: Int, down: Boolean, t: Float) {
        val cx = r.centerX()
        val cy = r.centerY() + if (down) dp(2f) else 0f
        val rad = r.width() / 2f
        p.color = 0x55000000
        c.drawCircle(cx, cy + dp(7f), rad * 0.92f, p)
        p.color = Gfx.darken(color, 0.5f)
        c.drawCircle(cx, cy + dp(4f), rad, p)
        p.color = Color.BLACK
        p.shader = LinearGradient(cx, cy - rad, cx, cy + rad, Gfx.lighten(color, 0.32f), Gfx.darken(color, 0.12f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, rad, p)
        p.shader = null
        sp.color = 0xAAFFFFFF.toInt()
        sp.strokeWidth = dp(1.5f)
        c.drawCircle(cx, cy, rad, sp)
        p.color = 0x40FFFFFF
        rect.set(cx - rad * 0.7f, cy - rad * 0.9f, cx + rad * 0.7f, cy - rad * 0.1f)
        c.drawOval(rect, p)
        val u = rad * 0.5f
        p.color = Color.WHITE
        when (kind) {
            0 -> Gfx.soundIcon(c, cx, cy, u, sfx.mode.ordinal)
            1 -> {
                // spin wheel icon
                sp.color = Color.WHITE
                sp.strokeWidth = dp(2.2f)
                c.drawCircle(cx, cy, u * 1.1f, sp)
                for (k in 0 until 4) {
                    val a = k * Math.PI / 4
                    c.drawLine(cx - (Math.cos(a) * u * 1.1f).toFloat(), cy - (Math.sin(a) * u * 1.1f).toFloat(),
                        cx + (Math.cos(a) * u * 1.1f).toFloat(), cy + (Math.sin(a) * u * 1.1f).toFloat(), sp)
                }
                if (profile.spinWaitMs() == 0L) {
                    p.color = 0xFFFF1744.toInt()
                    c.drawCircle(cx + rad * 0.8f, cy - rad * 0.8f, dp(6f), p)
                    p.color = Color.WHITE
                }
            }
            2 -> {
                Gfx.star(cx, cy, u * 1.25f, u * 0.55f, gemPath)
                c.drawPath(gemPath, p)
            }
            3 -> {
                c.drawRect(cx - u * 1.1f, cy, cx - u * 0.4f, cy + u, p)
                c.drawRect(cx - u * 0.35f, cy - u * 0.8f, cx + u * 0.35f, cy + u, p)
                c.drawRect(cx + u * 0.4f, cy - u * 0.3f, cx + u * 1.1f, cy + u, p)
            }
            4 -> {
                val cs = intArrayOf(0xFFFFEB3B.toInt(), 0xFF69F0AE.toInt(), 0xFF40C4FF.toInt(), 0xFFFF5252.toInt())
                for (i in 0..3) {
                    p.color = cs[i]
                    c.drawCircle(cx + (i % 2 - 0.5f) * u * 1.0f, cy + (i / 2 - 0.5f) * u * 1.0f, u * 0.5f, p)
                }
            }
            5 -> {
                gemPath.reset()
                gemPath.moveTo(cx, cy - u * 1.1f)
                gemPath.lineTo(cx + u * 1.15f, cy - u * 0.05f)
                gemPath.lineTo(cx + u * 0.75f, cy - u * 0.05f)
                gemPath.lineTo(cx + u * 0.75f, cy + u)
                gemPath.lineTo(cx - u * 0.75f, cy + u)
                gemPath.lineTo(cx - u * 0.75f, cy - u * 0.05f)
                gemPath.lineTo(cx - u * 1.15f, cy - u * 0.05f)
                gemPath.close()
                c.drawPath(gemPath, p)
            }
            else -> coin(c, cx, cy, u * 1.0f)
        }
    }

    private fun abs4(v: Float) = if (v < 0f) -v else v

    private fun drawSetup(c: Canvas, w: Float, h: Float, t: Float) {
        val top = max(insetTop, dp(12f))
        val bottom = max(insetBottom, dp(12f))
        val title = when (mode) {
            Mode.CLASSIC -> "CLASSIC"
            Mode.FRIENDS -> "PLAY WITH FRIENDS"
            Mode.BOTS -> "PLAY WITH BOTS"
        }
        tpo.textSize = dp(26f)
        tpo.strokeWidth = dp(5f)
        tpo.color = 0xFF1B0F3F.toInt()
        c.drawText(title, w / 2f, top + dp(52f), tpo)
        tp.textSize = dp(26f)
        tp.color = Color.WHITE
        c.drawText(title, w / 2f, top + dp(52f), tp)

        var y = top + dp(100f)
        tp.textSize = dp(16f)
        tp.color = 0xFFFFE9A8.toInt()
        c.drawText(if (mode == Mode.BOTS) "How many players in total?" else "How many players?", w / 2f, y, tp)
        y += dp(18f)

        // number chips 2..6
        val chip = dp(52f)
        val gap = dp(10f)
        val rowW = chip * 5 + gap * 4
        var cx = (w - rowW) / 2f
        for (n in 2..6) {
            val r = RectF(cx, y, cx + chip, y + chip)
            buttons.add(Btn("p$n", r))
            val sel = n == players
            val pr = pressed == "p$n"
            p.color = Color.BLACK
            p.shader = LinearGradient(r.left, r.top, r.left, r.bottom,
                if (sel) 0xFFFFE57F.toInt() else 0xFF7E57C2.toInt(), if (sel) 0xFFFF9800.toInt() else 0xFF311B92.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX(), r.centerY() + (if (pr) dp(2f) else 0f), chip / 2f, p)
            p.shader = null
            sp.color = if (sel) Color.WHITE else 0x88FFFFFF.toInt()
            sp.strokeWidth = dp(if (sel) 3f else 1.5f)
            c.drawCircle(r.centerX(), r.centerY(), chip / 2f, sp)
            tp.textSize = dp(24f)
            tp.color = if (sel) 0xFF3E2723.toInt() else Color.WHITE
            c.drawText(n.toString(), r.centerX(), r.centerY() + dp(9f), tp)
            cx += chip + gap
        }
        y += chip + dp(14f)

        tp.textSize = dp(12f)
        tp.color = 0xCCFFFFFF.toInt()
        c.drawText(
            if (players <= 4) "Classic cross board" else "Big 6-arm star board",
            w / 2f, y, tp
        )
        y += dp(12f)
        val tr0 = RectF(w / 2f - dp(105f), y, w / 2f + dp(105f), y + dp(38f))
        buttons.add(Btn("ntok", tr0))
        glossy(c, tr0, 0xFF00ACC1.toInt(), "PAWNS EACH: $tokens  (tap)", null, pressed == "ntok", small = true)
        y += dp(52f)

        // your pawn colour (player 1, bottom-left); the others follow in order
        tp.textSize = dp(12f)
        tp.color = 0xFFFFE9A8.toInt()
        c.drawText("Player 1 colour", w / 2f, y - dp(2f), tp)
        y += dp(6f)
        val cs = dp(34f)
        val cg = dp(10f)
        var ccx = (w - (cs * 6 + cg * 5)) / 2f
        for (i in 0 until 6) {
            val r = RectF(ccx, y, ccx + cs, y + cs)
            buttons.add(Btn("col$i", r))
            val sel = profile.pawnColor == i
            p.color = Color.BLACK
            p.shader = RadialGradient(r.centerX() - cs * 0.15f, r.centerY() - cs * 0.2f, cs * 0.7f,
                Gfx.lighten(Seats.PALETTE[i], 0.5f), Gfx.darken(Seats.PALETTE[i], 0.25f), Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX(), r.centerY(), cs / 2f, p)
            p.shader = null
            sp.color = if (sel) Color.WHITE else 0x66FFFFFF
            sp.strokeWidth = dp(if (sel) 3.5f else 1.2f)
            c.drawCircle(r.centerX(), r.centerY(), cs / 2f, sp)
            ccx += cs + cg
        }
        y += cs + dp(16f)
        Seats.setFirst(profile.pawnColor)

        val seats = BoardLayout.seatsFor(players)
        val listBottom = h - bottom - dp(150f)
        if (mode == Mode.CLASSIC) {
            val rh = min(dp(52f), (listBottom - y) / players - dp(8f))
            val rw = min(w - dp(40f), dp(360f))
            for (i in 0 until players) {
                val r = RectF((w - rw) / 2f, y, (w + rw) / 2f, y + rh)
                p.color = 0x33FFFFFF
                c.drawRoundRect(r, rh * 0.3f, rh * 0.3f, p)
                p.color = Seats.COLORS[seats[i]]
                c.drawCircle(r.left + rh * 0.6f, r.centerY(), rh * 0.3f, p)
                tp.textAlign = Paint.Align.LEFT
                tp.textSize = dp(16f)
                tp.color = Color.WHITE
                c.drawText("Player ${i + 1}  •  ${Seats.NAMES[seats[i]]}", r.left + rh * 1.1f, r.centerY() + dp(6f), tp)
                tp.textAlign = Paint.Align.CENTER
                val tr = RectF(r.right - dp(96f), r.top + rh * 0.14f, r.right - dp(10f), r.bottom - rh * 0.14f)
                buttons.add(Btn("t$i", tr))
                glossy(c, tr, if (humans[i]) 0xFF43A047.toInt() else 0xFFE53935.toInt(),
                    if (humans[i]) "HUMAN" else "BOT", null, pressed == "t$i", small = true)
                y += rh + dp(8f)
            }
        } else {
            val msg = if (mode == Mode.FRIENDS) "Pass the phone around - every player is a human."
            else "You play Red. The other ${players - 1} seat(s) are bots."
            tp.textSize = dp(14f)
            tp.color = Color.WHITE
            c.drawText(msg, w / 2f, y + dp(10f), tp)
            val dot = dp(20f)
            var dx = (w - (players * dot * 2 + (players - 1) * dp(6f))) / 2f
            for (i in 0 until players) {
                p.color = Seats.COLORS[seats[i]]
                c.drawCircle(dx + dot, y + dp(52f) + (sin(t * 4f + i) * dp(3f)), dot, p)
                sp.color = Color.WHITE
                sp.strokeWidth = dp(2f)
                c.drawCircle(dx + dot, y + dp(52f) + (sin(t * 4f + i) * dp(3f)), dot, sp)
                dx += dot * 2 + dp(6f)
            }
        }

        val bw = min(w - dp(60f), dp(300f))
        val sr = RectF((w - bw) / 2f, h - bottom - dp(130f), (w + bw) / 2f, h - bottom - dp(66f))
        buttons.add(Btn("start", sr))
        glossy(c, sr, 0xFF43A047.toInt(), "START GAME", null, pressed == "start")
        val br = RectF((w - bw) / 2f, h - bottom - dp(56f), (w + bw) / 2f, h - bottom - dp(10f))
        buttons.add(Btn("back", br))
        glossy(c, br, 0xFF5E35B1.toInt(), "BACK", null, pressed == "back", small = true)
    }

    private fun glossy(c: Canvas, r: RectF, color: Int, title: String, sub: String?, down: Boolean, small: Boolean = false) {
        val rr = r.height() * 0.3f
        val off = if (down) dp(3f) else 0f
        val depth = dp(if (small) 3f else 5f)
        p.color = Gfx.darken(color, 0.5f)
        rect.set(r.left, r.top + depth, r.right, r.bottom + depth - off)
        c.drawRoundRect(rect, rr, rr, p)
        rect.set(r.left, r.top + off, r.right, r.bottom + off)
        p.color = Color.BLACK
        p.shader = LinearGradient(r.left, r.top, r.left, r.bottom, Gfx.lighten(color, 0.32f), Gfx.darken(color, 0.12f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rr, rr, p)
        p.shader = null
        // gloss
        p.color = 0x40FFFFFF
        val g = RectF(rect.left + dp(4f), rect.top + dp(3f), rect.right - dp(4f), rect.centerY())
        c.drawRoundRect(g, rr, rr, p)
        sp.color = 0xAAFFFFFF.toInt()
        sp.strokeWidth = dp(1.5f)
        c.drawRoundRect(rect, rr, rr, sp)

        val ty = rect.centerY()
        if (sub == null) {
            tp.textSize = dp(if (small) 14f else 20f)
            tpo.textSize = tp.textSize
            tpo.strokeWidth = tp.textSize * 0.14f
            tpo.color = Gfx.withAlpha(Gfx.darken(color, 0.6f), 200)
            c.drawText(title, rect.centerX(), ty + tp.textSize * 0.35f, tpo)
            tp.color = Color.WHITE
            c.drawText(title, rect.centerX(), ty + tp.textSize * 0.35f, tp)
        } else {
            tp.textSize = dp(19f)
            tpo.textSize = tp.textSize
            tpo.strokeWidth = tp.textSize * 0.14f
            tpo.color = Gfx.withAlpha(Gfx.darken(color, 0.6f), 200)
            c.drawText(title, rect.centerX(), ty - dp(2f), tpo)
            tp.color = Color.WHITE
            c.drawText(title, rect.centerX(), ty - dp(2f), tp)
            tp.textSize = dp(11.5f)
            tp.color = 0xDDFFFFFF.toInt()
            c.drawText(sub, rect.centerX(), ty + dp(16f), tp)
        }
    }

    // ------------------------------------------------------------------ input

    private fun hit(x: Float, y: Float): String? = buttons.firstOrNull { it.r.contains(x, y) }?.id

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> pressed = hit(e.x, e.y)
            MotionEvent.ACTION_UP -> {
                val id = hit(e.x, e.y)
                val was = pressed
                pressed = null
                if (id != null && id == was) click(id)
            }
            MotionEvent.ACTION_CANCEL -> pressed = null
        }
        return true
    }

    private fun click(id: String) {
        sfx.play("step")
        when {
            id == "quick" -> onStart(GameConfig(4, booleanArrayOf(true, false, false, false), 2, true))
            id == "home" -> {}
            id == "online" -> onPanel("online")
            id == "profile" -> onPanel("profile")
            id == "store" -> onPanel("store")
            id == "spin" -> onPanel("spin")
            id == "themes" -> onPanel("themes")
            id == "ach" -> onPanel("ach")
            id == "rank" -> onPanel("rank")
            id == "classic" -> openSetup(Mode.CLASSIC)
            id == "friends" -> openSetup(Mode.FRIENDS)
            id == "bots" -> openSetup(Mode.BOTS)
            id == "sound" -> sfx.cycle()
            id == "ntok" -> tokens = if (tokens == 4) 2 else 4
            id.startsWith("col") -> profile.pawnColor = id.substring(3).toInt()
            id == "back" -> screen = Screen.MAIN
            id.startsWith("p") -> players = id.substring(1).toInt()
            id.startsWith("t") -> {
                val i = id.substring(1).toInt()
                humans[i] = !humans[i]
            }
            id == "start" -> {
                val hs = when (mode) {
                    Mode.FRIENDS -> BooleanArray(players) { true }
                    Mode.BOTS -> BooleanArray(players) { it == 0 }
                    Mode.CLASSIC -> BooleanArray(players) { humans[it] }
                }
                onStart(GameConfig(players, hs, tokens))
            }
        }
    }

    private fun openSetup(m: Mode) {
        mode = m
        screen = Screen.SETUP
        players = 4
        tokens = 4
        for (i in humans.indices) humans[i] = i == 0
    }
}
