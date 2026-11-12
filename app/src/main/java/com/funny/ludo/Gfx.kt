package com.funny.ludo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object Seats {
    val BASE_NAMES = arrayOf("Red", "Green", "Yellow", "Blue", "Purple", "Orange")
    val BASE_COLORS = intArrayOf(
        0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFFDD835.toInt(),
        0xFF1E88E5.toInt(), 0xFF8E24AA.toInt(), 0xFFFB8C00.toInt()
    )

    /** colour of each board seat for the current game (seat 0 = first player, bottom-left) */
    val NAMES = BASE_NAMES.copyOf()
    val COLORS = BASE_COLORS.copyOf()

    /** the six colours of the selected theme, and its pawn shape */
    var PALETTE: IntArray = BASE_COLORS
    var pawnStyle = 0
    private var first = 0

    fun applyTheme(t: Theme) {
        PALETTE = t.seat
        pawnStyle = t.pawn
        setFirst(first)
    }
    val GREY = 0xFFB9B1A4.toInt()

    /** gives seat 0 the chosen colour; the other seats follow in the usual colour order */
    fun setFirst(choice: Int) {
        val c = choice.coerceIn(0, 5)
        first = c
        for (k in 0 until 6) {
            NAMES[k] = BASE_NAMES[(c + k) % 6]
            COLORS[k] = PALETTE[(c + k) % 6]
        }
    }
}

object Gfx {
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val isp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val ipath = Path()
    private val irect = RectF()

    /** chunky 3D gold coin: dark edge underneath, shiny face, embossed star, highlight */
    fun coin3d(c: Canvas, cx: Float, cy: Float, r: Float) {
        ip.shader = null
        ip.color = 0x55000000
        irect.set(cx - r * 0.9f, cy + r * 0.95f, cx + r * 0.9f, cy + r * 1.3f)
        c.drawOval(irect, ip)
        ip.color = 0xFF9A5B00.toInt()
        c.drawCircle(cx, cy + r * 0.2f, r, ip)                       // thickness
        ip.color = Color.BLACK
        ip.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.45f, r * 1.5f,
            intArrayOf(0xFFFFFDE0.toInt(), 0xFFFFD23A.toInt(), 0xFFEF8F00.toInt()), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, ip)
        ip.shader = null
        isp.color = 0xFFB86E00.toInt()
        isp.strokeWidth = r * 0.12f
        c.drawCircle(cx, cy, r * 0.72f, isp)
        star(cx, cy, r * 0.48f, r * 0.2f, ipath)
        ip.color = 0xFFC77800.toInt()
        c.drawPath(ipath, ip)
        isp.color = 0xCCFFFFFF.toInt()
        isp.strokeWidth = r * 0.1f
        irect.set(cx - r * 0.86f, cy - r * 0.86f, cx + r * 0.86f, cy + r * 0.86f)
        c.drawArc(irect, 200f, 70f, false, isp)                      // rim glint
    }

    /** sound mode icon: 0 meme (laughing face), 1 normal (music note), 2 off (muted speaker) */
    fun soundIcon(c: Canvas, cx: Float, cy: Float, u: Float, mode: Int) {
        ip.shader = null
        isp.color = Color.WHITE
        isp.strokeWidth = u * 0.22f
        when (mode) {
            0 -> {
                ip.color = 0xFFFFD93B.toInt()
                c.drawCircle(cx, cy, u * 1.15f, ip)
                isp.color = 0xFF5A3A00.toInt()
                isp.strokeWidth = u * 0.14f
                c.drawCircle(cx, cy, u * 1.15f, isp)
                irect.set(cx - u * 0.75f, cy - u * 0.5f, cx - u * 0.2f, cy + u * 0.05f)
                c.drawArc(irect, 200f, 140f, false, isp)
                irect.set(cx + u * 0.2f, cy - u * 0.5f, cx + u * 0.75f, cy + u * 0.05f)
                c.drawArc(irect, 200f, 140f, false, isp)
                ip.color = 0xFF7A1F1F.toInt()
                irect.set(cx - u * 0.7f, cy - u * 0.55f, cx + u * 0.7f, cy + u * 0.8f)
                c.drawArc(irect, 0f, 180f, true, ip)
                ip.color = 0xFFFF6B81.toInt()
                irect.set(cx - u * 0.35f, cy + u * 0.4f, cx + u * 0.35f, cy + u * 0.85f)
                c.drawArc(irect, 180f, 180f, true, ip)
            }
            1 -> {
                ip.color = Color.WHITE
                irect.set(cx - u * 0.95f, cy + u * 0.35f, cx - u * 0.15f, cy + u * 1.0f)
                c.drawOval(irect, ip)
                irect.set(cx + u * 0.15f, cy + u * 0.1f, cx + u * 0.95f, cy + u * 0.75f)
                c.drawOval(irect, ip)
                c.drawLine(cx - u * 0.25f, cy + u * 0.68f, cx - u * 0.25f, cy - u * 0.75f, isp)
                c.drawLine(cx + u * 0.85f, cy + u * 0.42f, cx + u * 0.85f, cy - u * 1.0f, isp)
                isp.strokeWidth = u * 0.34f
                c.drawLine(cx - u * 0.25f, cy - u * 0.7f, cx + u * 0.85f, cy - u * 0.95f, isp)
            }
            else -> {
                ipath.reset()
                ipath.moveTo(cx - u * 1.0f, cy - u * 0.4f)
                ipath.lineTo(cx - u * 0.4f, cy - u * 0.4f)
                ipath.lineTo(cx + u * 0.25f, cy - u * 1.0f)
                ipath.lineTo(cx + u * 0.25f, cy + u * 1.0f)
                ipath.lineTo(cx - u * 0.4f, cy + u * 0.4f)
                ipath.lineTo(cx - u * 1.0f, cy + u * 0.4f)
                ipath.close()
                ip.color = 0xFFCFD8DC.toInt()
                c.drawPath(ipath, ip)
                isp.color = 0xFFFF5252.toInt()
                isp.strokeWidth = u * 0.3f
                c.drawLine(cx - u * 1.1f, cy - u * 1.1f, cx + u * 1.1f, cy + u * 1.1f, isp)
            }
        }
    }

    /** faceted 3D diamond: crown, pavilion facets, drop shadow, sparkle */
    fun gem3d(c: Canvas, cx: Float, cy: Float, r: Float) {
        val w = r * 0.95f
        val top = cy - r * 0.8f
        val mid = cy - r * 0.22f
        val bot = cy + r
        ip.shader = null
        ip.color = 0x55000000
        irect.set(cx - w * 0.7f, bot - r * 0.05f, cx + w * 0.7f, bot + r * 0.25f)
        c.drawOval(irect, ip)
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, col: Int) {
            ipath.reset()
            ipath.moveTo(x1, y1)
            ipath.lineTo(x2, y2)
            ipath.lineTo(x3, y3)
            ipath.close()
            ip.color = col
            c.drawPath(ipath, ip)
        }
        val xl = cx - w
        val xr = cx + w
        val tl = cx - w * 0.55f
        val trr = cx + w * 0.55f
        val ml = cx - w * 0.38f
        val mr = cx + w * 0.38f
        // pavilion (bottom)
        tri(xl, mid, ml, mid, cx, bot, 0xFF0277BD.toInt())
        tri(ml, mid, mr, mid, cx, bot, 0xFF29B6F6.toInt())
        tri(mr, mid, xr, mid, cx, bot, 0xFF01579B.toInt())
        // crown (top)
        tri(xl, mid, tl, top, ml, mid, 0xFF4FC3F7.toInt())
        tri(tl, top, cx, top, ml, mid, 0xFFB3E5FC.toInt())
        tri(cx, top, mr, mid, ml, mid, 0xFFE1F5FE.toInt())
        tri(cx, top, trr, top, mr, mid, 0xFF81D4FA.toInt())
        tri(trr, top, xr, mid, mr, mid, 0xFF039BE5.toInt())
        // outline
        ipath.reset()
        ipath.moveTo(tl, top)
        ipath.lineTo(trr, top)
        ipath.lineTo(xr, mid)
        ipath.lineTo(cx, bot)
        ipath.lineTo(xl, mid)
        ipath.close()
        isp.color = 0xFFFFFFFF.toInt()
        isp.strokeWidth = r * 0.09f
        c.drawPath(ipath, isp)
        // sparkle
        ip.color = Color.WHITE
        star(cx - w * 0.3f, top + r * 0.28f, r * 0.2f, r * 0.06f, ipath)
        c.drawPath(ipath, ip)
    }

    /** small diamond (gem) outline */
    fun diamondPath(cx: Float, cy: Float, r: Float, path: Path) {
        path.reset()
        path.moveTo(cx, cy - r)
        path.lineTo(cx + r * 0.85f, cy - r * 0.2f)
        path.lineTo(cx, cy + r)
        path.lineTo(cx - r * 0.85f, cy - r * 0.2f)
        path.close()
    }

    fun lighten(c: Int, f: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = min(1f, hsv[2] * (1f + f))
        hsv[1] = max(0f, hsv[1] * (1f - f * 0.45f))
        return Color.HSVToColor(Color.alpha(c), hsv)
    }

    fun darken(c: Int, f: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(c, hsv)
        hsv[2] = max(0f, hsv[2] * (1f - f))
        return Color.HSVToColor(Color.alpha(c), hsv)
    }

    fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    fun star(cx: Float, cy: Float, outer: Float, inner: Float, path: Path = Path()): Path {
        path.reset()
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val a = (-PI / 2 + i * PI / 5).toFloat()
            val x = cx + r * cos(a)
            val y = cy + r * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** A shiny 3D-looking "goti" (pawn): base disc, tapered body, spherical head. Anchored at the bottom centre. */
    fun pawn(color: Int, w: Int, h: Int, style: Int = Seats.pawnStyle): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val W = w.toFloat()
        val H = h.toFloat()
        val dark = darken(color, 0.45f)
        val light = lighten(color, 0.55f)
        val edge = darken(color, 0.7f)

        val baseRect = RectF(W * 0.06f, H * 0.78f, W * 0.94f, H * 0.99f)
        p.shader = LinearGradient(0f, baseRect.top, 0f, baseRect.bottom, light, dark, Shader.TileMode.CLAMP)
        c.drawOval(baseRect, p)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = max(1f, W * 0.035f)
            strokeJoin = Paint.Join.ROUND
            this.color = withAlpha(edge, 200)
        }
        c.drawOval(baseRect, line)
        val hx = W * 0.5f
        fun ball(cy: Float, r: Float) = RadialGradient(hx - r * 0.35f, cy - r * 0.4f, r * 1.6f,
            intArrayOf(Color.WHITE, light, color, dark), floatArrayOf(0f, 0.18f, 0.6f, 1f), Shader.TileMode.CLAMP)
        fun side() = LinearGradient(W * 0.17f, 0f, W * 0.83f, 0f,
            intArrayOf(dark, color, light, color, dark), floatArrayOf(0f, 0.25f, 0.42f, 0.72f, 1f), Shader.TileMode.CLAMP)
        fun fill(path: Path, sh: Shader) {
            p.shader = sh
            c.drawPath(path, p)
            p.shader = null
            c.drawPath(path, line)
        }
        fun glint(cx: Float, cy: Float, r: Float) {
            p.shader = null
            p.color = withAlpha(Color.WHITE, 190)
            c.drawOval(RectF(cx - r * 0.62f, cy - r * 0.72f, cx - r * 0.12f, cy - r * 0.32f), p)
        }
        fun eyes(cy: Float, dx: Float, r: Float) {
            p.shader = null
            p.color = withAlpha(Color.BLACK, 210)
            c.drawCircle(hx - dx, cy, r, p)
            c.drawCircle(hx + dx, cy, r, p)
            p.color = Color.WHITE
            c.drawCircle(hx - dx - r * 0.3f, cy - r * 0.3f, r * 0.35f, p)
            c.drawCircle(hx + dx - r * 0.3f, cy - r * 0.3f, r * 0.35f, p)
        }
        // the classic cone body, used by several styles
        fun cone(topY: Float, halfTop: Float) {
            val body = Path().apply {
                moveTo(W * 0.17f, H * 0.89f)
                quadTo(W * 0.36f, H * 0.80f, hx - halfTop, topY)
                lineTo(hx + halfTop, topY)
                quadTo(W * 0.64f, H * 0.80f, W * 0.83f, H * 0.89f)
                quadTo(W * 0.5f, H * 0.98f, W * 0.17f, H * 0.89f)
                close()
            }
            fill(body, side())
        }
        fun collar() {
            val r = RectF(W * 0.30f, H * 0.42f, W * 0.70f, H * 0.53f)
            p.shader = LinearGradient(0f, r.top, 0f, r.bottom, light, dark, Shader.TileMode.CLAMP)
            c.drawOval(r, p)
            p.shader = null
        }
        fun head(cy: Float, r: Float) {
            p.shader = ball(cy, r)
            c.drawCircle(hx, cy, r, p)
            p.shader = null
            c.drawCircle(hx, cy, r, line)
            glint(hx, cy, r)
        }

        when (style) {
            1 -> {  // jungle: pawn with a leaf sprout
                cone(H * 0.48f, W * 0.10f)
                collar()
                head(H * 0.33f, W * 0.25f)
                for (sgn in intArrayOf(-1, 1)) {
                    val leaf = Path().apply {
                        moveTo(hx, H * 0.12f)
                        quadTo(hx + sgn * W * 0.10f, H * -0.02f, hx + sgn * W * 0.34f, H * 0.02f)
                        quadTo(hx + sgn * W * 0.26f, H * 0.14f, hx, H * 0.12f)
                        close()
                    }
                    p.color = if (sgn < 0) 0xFF6BD44B.toInt() else 0xFF3FAE3A.toInt()
                    c.drawPath(leaf, p)
                    line.color = 0xCC1E5B1C.toInt()
                    c.drawPath(leaf, line)
                    line.color = withAlpha(edge, 200)
                }
            }
            2 -> {  // mining: cut crystal on a stand
                cone(H * 0.56f, W * 0.12f)
                val top = H * 0.04f
                val mid = H * 0.22f
                val bot = H * 0.60f
                val hw = W * 0.34f
                val gem = Path().apply {
                    moveTo(hx - hw * 0.55f, top)
                    lineTo(hx + hw * 0.55f, top)
                    lineTo(hx + hw, mid)
                    lineTo(hx, bot)
                    lineTo(hx - hw, mid)
                    close()
                }
                fill(gem, LinearGradient(hx - hw, top, hx + hw, bot, intArrayOf(Color.WHITE, light, color, dark), null, Shader.TileMode.CLAMP))
                line.color = withAlpha(Color.WHITE, 170)
                c.drawLine(hx - hw, mid, hx + hw, mid, line)
                c.drawLine(hx - hw * 0.35f, mid, hx, bot, line)
                c.drawLine(hx + hw * 0.35f, mid, hx, bot, line)
                c.drawLine(hx - hw * 0.55f, top, hx - hw * 0.35f, mid, line)
                c.drawLine(hx + hw * 0.55f, top, hx + hw * 0.35f, mid, line)
                line.color = withAlpha(edge, 200)
            }
            3 -> {  // sky: balloon on a string
                p.shader = null
                line.color = withAlpha(Color.WHITE, 220)
                c.drawLine(hx, H * 0.58f, hx, H * 0.84f, line)
                line.color = withAlpha(edge, 200)
                val knot = Path().apply {
                    moveTo(hx - W * 0.07f, H * 0.62f)
                    lineTo(hx + W * 0.07f, H * 0.62f)
                    lineTo(hx, H * 0.55f)
                    close()
                }
                p.color = dark
                c.drawPath(knot, p)
                val r = RectF(W * 0.14f, H * 0.03f, W * 0.86f, H * 0.58f)
                p.shader = ball(H * 0.30f, W * 0.36f)
                c.drawOval(r, p)
                p.shader = null
                c.drawOval(r, line)
                glint(hx, H * 0.27f, W * 0.34f)
            }
            4 -> {  // space: rocket
                val body = Path().apply {
                    moveTo(hx, H * 0.02f)
                    quadTo(W * 0.80f, H * 0.26f, W * 0.70f, H * 0.74f)
                    lineTo(W * 0.30f, H * 0.74f)
                    quadTo(W * 0.20f, H * 0.26f, hx, H * 0.02f)
                    close()
                }
                for (sgn in intArrayOf(-1, 1)) {
                    val fin = Path().apply {
                        moveTo(hx + sgn * W * 0.19f, H * 0.56f)
                        lineTo(hx + sgn * W * 0.44f, H * 0.88f)
                        lineTo(hx + sgn * W * 0.19f, H * 0.76f)
                        close()
                    }
                    fill(fin, LinearGradient(0f, H * 0.56f, 0f, H * 0.88f, color, dark, Shader.TileMode.CLAMP))
                }
                val flame = Path().apply {
                    moveTo(W * 0.38f, H * 0.74f)
                    lineTo(hx, H * 0.93f)
                    lineTo(W * 0.62f, H * 0.74f)
                    close()
                }
                p.color = 0xFFFFB300.toInt()
                c.drawPath(flame, p)
                fill(body, LinearGradient(W * 0.22f, 0f, W * 0.78f, 0f, intArrayOf(dark, color, Color.WHITE, light, dark),
                    floatArrayOf(0f, 0.25f, 0.42f, 0.6f, 1f), Shader.TileMode.CLAMP))
                p.color = 0xFF263238.toInt()
                c.drawCircle(hx, H * 0.36f, W * 0.14f, p)
                p.color = 0xFF81D4FA.toInt()
                c.drawCircle(hx, H * 0.36f, W * 0.10f, p)
                p.color = Color.WHITE
                c.drawCircle(hx - W * 0.04f, H * 0.34f, W * 0.03f, p)
            }
            5 -> {  // ghost
                val gh = Path().apply {
                    moveTo(W * 0.14f, H * 0.90f)
                    lineTo(W * 0.14f, H * 0.38f)
                    quadTo(W * 0.14f, H * 0.04f, hx, H * 0.04f)
                    quadTo(W * 0.86f, H * 0.04f, W * 0.86f, H * 0.38f)
                    lineTo(W * 0.86f, H * 0.90f)
                    lineTo(W * 0.68f, H * 0.80f)
                    lineTo(hx, H * 0.90f)
                    lineTo(W * 0.32f, H * 0.80f)
                    close()
                }
                fill(gh, LinearGradient(0f, H * 0.04f, 0f, H * 0.9f, intArrayOf(Color.WHITE, light, color), null, Shader.TileMode.CLAMP))
                eyes(H * 0.36f, W * 0.16f, W * 0.085f)
                p.color = withAlpha(Color.BLACK, 190)
                c.drawOval(RectF(hx - W * 0.07f, H * 0.48f, hx + W * 0.07f, H * 0.60f), p)
            }
            6 -> {  // love: heart on a stand
                cone(H * 0.56f, W * 0.09f)
                val t0 = H * 0.06f
                val hw = W * 0.40f
                val heart = Path().apply {
                    moveTo(hx, H * 0.62f)
                    cubicTo(hx - hw * 1.5f, H * 0.36f, hx - hw * 0.9f, t0 - H * 0.08f, hx, t0 + H * 0.12f)
                    cubicTo(hx + hw * 0.9f, t0 - H * 0.08f, hx + hw * 1.5f, H * 0.36f, hx, H * 0.62f)
                    close()
                }
                fill(heart, ball(H * 0.28f, W * 0.34f))
                glint(hx - W * 0.10f, H * 0.24f, W * 0.22f)
            }
            7 -> {  // toys: brick figure
                val legs = RectF(W * 0.24f, H * 0.52f, W * 0.76f, H * 0.90f)
                p.shader = side()
                c.drawRoundRect(legs, W * 0.08f, W * 0.08f, p)
                p.shader = null
                c.drawRoundRect(legs, W * 0.08f, W * 0.08f, line)
                val stud = RectF(W * 0.38f, H * 0.02f, W * 0.62f, H * 0.14f)
                p.color = light
                c.drawRoundRect(stud, W * 0.04f, W * 0.04f, p)
                c.drawRoundRect(stud, W * 0.04f, W * 0.04f, line)
                val hd = RectF(W * 0.20f, H * 0.10f, W * 0.80f, H * 0.54f)
                p.shader = LinearGradient(0f, hd.top, 0f, hd.bottom, intArrayOf(Color.WHITE, light, color), null, Shader.TileMode.CLAMP)
                c.drawRoundRect(hd, W * 0.14f, W * 0.14f, p)
                p.shader = null
                c.drawRoundRect(hd, W * 0.14f, W * 0.14f, line)
                eyes(H * 0.28f, W * 0.13f, W * 0.055f)
                line.color = withAlpha(Color.BLACK, 200)
                c.drawArc(RectF(hx - W * 0.13f, H * 0.28f, hx + W * 0.13f, H * 0.46f), 20f, 140f, false, line)
                line.color = withAlpha(edge, 200)
            }
            else -> {  // classic
                cone(H * 0.46f, W * 0.10f)
                collar()
                head(H * 0.29f, W * 0.27f)
            }
        }
        return bmp
    }
}

/**
 * A real 3D die: six faces are rotated with a 3D matrix and projected, so it can tumble.
 * Faces: 1 +z, 6 -z, 2 +x, 5 -x, 3 +y, 4 -y (opposite faces add up to 7).
 */
class DiceRenderer {
    private class Face(val value: Int, val n: FloatArray, val eu: FloatArray, val ev: FloatArray)

    private fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    private val faces = listOf(
        Face(1, v(0f, 0f, 1f), v(1f, 0f, 0f), v(0f, 1f, 0f)),
        Face(6, v(0f, 0f, -1f), v(1f, 0f, 0f), v(0f, 1f, 0f)),
        Face(2, v(1f, 0f, 0f), v(0f, 0f, 1f), v(0f, 1f, 0f)),
        Face(5, v(-1f, 0f, 0f), v(0f, 0f, 1f), v(0f, 1f, 0f)),
        Face(3, v(0f, 1f, 0f), v(1f, 0f, 0f), v(0f, 0f, 1f)),
        Face(4, v(0f, -1f, 0f), v(1f, 0f, 0f), v(0f, 0f, 1f))
    )

    private val pips = arrayOf(
        intArrayOf(), intArrayOf(4), intArrayOf(0, 8), intArrayOf(0, 4, 8),
        intArrayOf(0, 2, 6, 8), intArrayOf(0, 2, 4, 6, 8), intArrayOf(0, 2, 3, 5, 6, 8)
    )

    private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x99503C28.toInt()
    }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val tmp = FloatArray(3)
    private val out = FloatArray(3)

    private val vx = -0.36f   // fixed viewing tilt: the rolled face dominates, two side faces peek out
    private val vy = -0.48f
    private val light = floatArrayOf(-0.35f, -0.65f, 0.68f)

    /** angles (ay, ax) that bring the face with this value to the front */
    fun finalAngles(value: Int, res: FloatArray) {
        val hp = (PI / 2).toFloat()
        when (value) {
            1 -> { res[0] = 0f; res[1] = 0f }
            6 -> { res[0] = PI.toFloat(); res[1] = 0f }
            2 -> { res[0] = -hp; res[1] = 0f }
            5 -> { res[0] = hp; res[1] = 0f }
            3 -> { res[0] = 0f; res[1] = hp }
            else -> { res[0] = 0f; res[1] = -hp }
        }
    }

    private fun xf(p: FloatArray, ay: Float, ax: Float, o: FloatArray) {
        val x = p[0]
        val y = p[1]
        val z = p[2]
        val y1 = y * cos(ax) - z * sin(ax)
        val z1 = y * sin(ax) + z * cos(ax)
        val x2 = x * cos(ay) + z1 * sin(ay)
        val z2 = -x * sin(ay) + z1 * cos(ay)
        val x3 = x2 * cos(vy) + z2 * sin(vy)
        val z3 = -x2 * sin(vy) + z2 * cos(vy)
        o[0] = x3
        o[1] = y1 * cos(vx) - z3 * sin(vx)
        o[2] = y1 * sin(vx) + z3 * cos(vx)
    }

    fun draw(c: Canvas, cx: Float, cy: Float, size: Float, ay: Float, ax: Float, alpha: Int = 255) {
        val sc = size * 0.36f
        facePaint.pathEffect = CornerPathEffect(size * 0.07f)
        edgePaint.pathEffect = CornerPathEffect(size * 0.07f)
        edgePaint.strokeWidth = max(1f, size * 0.025f)
        val corner = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)
        for (f in faces) {
            xf(f.n, ay, ax, out)
            if (out[2] < 0.02f) continue                        // back face
            val diffuse = max(0f, out[0] * light[0] + out[1] * light[1] + out[2] * light[2])
            val shade = 0.58f + 0.42f * diffuse
            path.reset()
            for (i in 0 until 4) {
                val sx = corner[i * 2]
                val sy = corner[i * 2 + 1]
                tmp[0] = f.n[0] + f.eu[0] * sx + f.ev[0] * sy
                tmp[1] = f.n[1] + f.eu[1] * sx + f.ev[1] * sy
                tmp[2] = f.n[2] + f.eu[2] * sx + f.ev[2] * sy
                xf(tmp, ay, ax, out)
                val px = cx + out[0] * sc
                val py = cy + out[1] * sc
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            val g = (255 * shade).toInt().coerceIn(0, 255)
            facePaint.color = Color.argb(alpha, g, (g * 0.985f).toInt(), (g * 0.94f).toInt())
            c.drawPath(path, facePaint)
            edgePaint.alpha = (alpha * 0.6f).toInt()
            c.drawPath(path, edgePaint)

            // pips
            val big = f.value == 1
            val pr = if (big) 0.3f else 0.17f
            pipPaint.color = if (big) Color.argb(alpha, 215, 30, 45) else Color.argb(alpha, 30, 28, 40)
            for (idx in pips[f.value]) {
                val pu = (idx % 3 - 1) * 0.52f
                val pv = (idx / 3 - 1) * 0.52f
                path.reset()
                for (s in 0 until 14) {
                    val t = (s * 2 * PI / 14).toFloat()
                    val ru = pr * cos(t)
                    val rv = pr * sin(t)
                    tmp[0] = f.n[0] + f.eu[0] * (pu + ru) + f.ev[0] * (pv + rv)
                    tmp[1] = f.n[1] + f.eu[1] * (pu + ru) + f.ev[1] * (pv + rv)
                    tmp[2] = f.n[2] + f.eu[2] * (pu + ru) + f.ev[2] * (pv + rv)
                    xf(tmp, ay, ax, out)
                    val px = cx + out[0] * sc
                    val py = cy + out[1] * sc
                    if (s == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                c.drawPath(path, pipPaint)
            }
        }
    }

    private val flatRect = RectF()

    /** Flat top-down die: always shows one face squarely so the number is easy to read. */
    fun drawFlat(c: Canvas, cx: Float, cy: Float, size: Float, value: Int, alpha: Int = 255, rot: Float = 0f) {
        val half = size * 0.46f
        val rad = half * 0.34f
        c.save()
        c.rotate(rot, cx, cy)
        // body: soft ivory gradient with a darker rim for a chunky 3D-icon look
        flatRect.set(cx - half, cy - half, cx + half, cy + half)
        facePaint.pathEffect = null
        facePaint.shader = LinearGradient(cx, cy - half, cx, cy + half,
            Color.argb(alpha, 250, 250, 255), Color.argb(alpha, 200, 205, 225), Shader.TileMode.CLAMP)
        c.drawRoundRect(flatRect, rad, rad, facePaint)
        facePaint.shader = null
        edgePaint.pathEffect = null
        edgePaint.strokeWidth = max(1.5f, size * 0.05f)
        edgePaint.color = Color.argb((alpha * 0.55f).toInt(), 70, 60, 110)
        c.drawRoundRect(flatRect, rad, rad, edgePaint)
        // inner bevel
        val inset = half * 0.12f
        flatRect.inset(inset, inset)
        edgePaint.strokeWidth = max(1f, size * 0.025f)
        edgePaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)
        c.drawRoundRect(flatRect, rad * 0.8f, rad * 0.8f, edgePaint)
        // gloss highlight across the top
        flatRect.set(cx - half * 0.8f, cy - half * 0.92f, cx + half * 0.8f, cy - half * 0.25f)
        facePaint.color = Color.argb((alpha * 0.35f).toInt(), 255, 255, 255)
        c.drawRoundRect(flatRect, rad, rad, facePaint)
        // pips
        val big = value == 1
        val pr = half * if (big) 0.30f else 0.17f
        val step = half * 0.5f
        for (idx in pips[value.coerceIn(1, 6)]) {
            val px = cx + (idx % 3 - 1) * step
            val py = cy + (idx / 3 - 1) * step
            pipPaint.color = if (big) Color.argb(alpha, 215, 30, 45) else Color.argb(alpha, 30, 28, 45)
            c.drawCircle(px, py, pr, pipPaint)
            pipPaint.color = Color.argb((alpha * 0.45f).toInt(), 255, 255, 255)
            c.drawCircle(px - pr * 0.3f, py - pr * 0.3f, pr * 0.35f, pipPaint)
        }
        c.restore()
    }
}
