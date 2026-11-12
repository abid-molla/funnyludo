package com.funny.ludo

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Services the panels need from the Activity. */
interface PanelHost {
    val ads: Ads
    val billing: Billing
    val games: PlayGamesHelper
    val online: Online
    fun toast(msg: String)
    fun share(text: String)
    fun promptCode(cb: (String) -> Unit)
    fun startOnline(cfg: GameConfig)
    fun closePanel()
    fun loginFinished()
    fun promptText(title: String, hint: String, cb: (String) -> Unit)
    fun panelShown(shown: Boolean)
    fun playSound(name: String)
}

/** Full-screen scrolling panels: login, store, themes, achievements, ranking, online rooms and lobby. */
class PanelView(ctx: Context, private val host: PanelHost) : View(ctx) {
    enum class Kind { LOGIN, STORE, THEMES, ACH, RANK, ONLINE, LOBBY, SPIN, PROFILE, MATCH }

    private class Btn(val id: String, val r: RectF, val fixed: Boolean)

    private val profile = Profile.get(ctx)
    private val handler = Handler(Looper.getMainLooper())
    private val buttons = ArrayList<Btn>()
    private var kind = Kind.STORE
    private var scroll = 0f
    private var contentH = 0f
    private var pressed: String? = null
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false

    private var insetTop = 0f
    private var insetBottom = 0f

    // online state
    private var maxPlayers = 4
    private var tokens = 4
    private var ranks: List<Online.Rank>? = null
    private var ranksLoading = false
    private var lobby: Online.Lobby? = null
    private var lobbyCode = ""
    private var launched = false
    private var lobbyAlive = false
    private var randomN = 0
    private var stake = Config.ONLINE_ENTRY_COINS
    private var matchHandle: Online.Match? = null
    private var matchStart = 0L

    // spin wheel
    private var spinning = false
    private var spinT0 = 0L
    private var spinFrom = 0f
    private var spinTo = 0f
    private var wheelRot = 0f
    private var spinResult = -1
    private var spinNote = ""

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
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
    private val path = Path()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    /** panel background colour: a little see-through where the menu behind can be blurred, solid otherwise */
    private fun sheet(color: Int) = Gfx.withAlpha(color, if (Build.VERSION.SDK_INT >= 31) 226 else 255)

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView === this) host.panelShown(visibility == VISIBLE)
    }

    init {
        profile.listeners.add { postInvalidate() }
    }

    @Suppress("DEPRECATION")
    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        insetTop = insets.systemWindowInsetTop.toFloat()
        insetBottom = insets.systemWindowInsetBottom.toFloat()
        return super.onApplyWindowInsets(insets)
    }

    fun open(k: Kind) {
        kind = k
        scroll = 0f
        launched = false
        if (k == Kind.RANK) loadRanks()
        visibility = VISIBLE
        invalidate()
    }

    private fun loadRanks() {
        if (!host.online.enabled || ranksLoading) return
        ranksLoading = true
        host.online.submitWins(profile.stat("wins"))
        host.online.topPlayers {
            ranks = it
            ranksLoading = false
            invalidate()
        }
    }

    // ------------------------------------------------------------------ drawing
    override fun onDraw(c: Canvas) {
        if (width == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        buttons.clear()
        // solid-looking sheet: the menu behind is blurred (Android 12+) and almost fully covered
        p.color = Color.BLACK
        p.shader = RadialGradient(w / 2f, h * 0.25f, max(w, h) * 0.95f,
            intArrayOf(sheet(0xFF5A2DA8.toInt()), sheet(0xFF24104F.toInt()), sheet(0xFF09041A.toInt())), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val top = max(insetTop, dp(10f))
        val bottom = max(insetBottom, dp(10f))
        val title = when (kind) {
            Kind.LOGIN -> "WELCOME"
            Kind.STORE -> "STORE"
            Kind.THEMES -> "THEMES"
            Kind.ACH -> "ACHIEVEMENTS"
            Kind.RANK -> "TOP PLAYERS"
            Kind.ONLINE -> "PLAY ONLINE"
            Kind.LOBBY -> if (randomN > 0) "RANDOM MATCH" else "ROOM"
            Kind.SPIN -> "LUCKY SPIN"
            Kind.PROFILE -> "PROFILE"
            Kind.MATCH -> "FINDING PLAYERS"
        }
        // header
        if (kind != Kind.LOGIN || profile.loginDone) {
            val b = RectF(dp(12f), top + dp(4f), dp(12f) + dp(42f), top + dp(4f) + dp(42f))
            buttons.add(Btn("back", b, true))
            circleButton(c, b, pressed == "back")
        }
        text(c, title, w / 2f, top + dp(36f), dp(24f), Color.WHITE, true)
        var y = top + dp(58f)
        if (kind == Kind.STORE || kind == Kind.THEMES || kind == Kind.SPIN) {
            wallet(c, w, y)
            y += dp(44f)
        }
        val listTop = y
        val listBottom = h - bottom
        c.save()
        c.clipRect(0f, listTop, w, listBottom)
        c.translate(0f, -scroll)
        val end = when (kind) {
            Kind.LOGIN -> drawLogin(c, w, listTop + dp(20f))
            Kind.STORE -> drawStore(c, w, listTop + dp(6f))
            Kind.THEMES -> drawThemes(c, w, listTop + dp(6f))
            Kind.ACH -> drawAch(c, w, listTop + dp(6f))
            Kind.RANK -> drawRank(c, w, listTop + dp(6f))
            Kind.ONLINE -> drawOnline(c, w, listTop + dp(14f))
            Kind.LOBBY -> drawLobby(c, w, listTop + dp(10f))
            Kind.SPIN -> drawSpin(c, w, listTop + dp(10f))
            Kind.PROFILE -> drawProfile(c, w, listTop + dp(10f))
            Kind.MATCH -> drawMatch(c, w, listTop + dp(30f))
        }
        c.restore()
        contentH = end - listTop + dp(20f)
        scroll = scroll.coerceIn(0f, max(0f, contentH - (listBottom - listTop)))
        if (kind == Kind.SPIN && spinning) postInvalidateOnAnimation()
        else if (kind == Kind.LOBBY || kind == Kind.RANK || kind == Kind.SPIN || kind == Kind.MATCH) postInvalidateDelayed(if (kind == Kind.MATCH) 250 else 500)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, outline: Boolean = false, align: Paint.Align = Paint.Align.CENTER) {
        tp.textAlign = align
        tp.textSize = size
        if (outline) {
            tpo.textAlign = align
            tpo.textSize = size
            tpo.strokeWidth = size * 0.2f
            tpo.color = 0xFF1B0F3F.toInt()
            c.drawText(s, x, y, tpo)
        }
        tp.color = color
        c.drawText(s, x, y, tp)
        tp.textAlign = Paint.Align.CENTER
    }

    private fun circleButton(c: Canvas, r: RectF, down: Boolean) {
        p.color = Color.BLACK
        p.shader = LinearGradient(r.left, r.top, r.left, r.bottom, 0xFF8A63D2.toInt(), 0xFF3B1E85.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(r.centerX(), r.centerY() + if (down) dp(2f) else 0f, r.width() / 2f, p)
        p.shader = null
        sp.color = 0xAAFFFFFF.toInt()
        sp.strokeWidth = dp(1.5f)
        c.drawCircle(r.centerX(), r.centerY(), r.width() / 2f, sp)
        sp.color = Color.WHITE
        sp.strokeWidth = dp(3f)
        val u = r.width() * 0.2f
        c.drawLine(r.centerX() + u * 0.6f, r.centerY() - u, r.centerX() - u * 0.6f, r.centerY(), sp)
        c.drawLine(r.centerX() - u * 0.6f, r.centerY(), r.centerX() + u * 0.6f, r.centerY() + u, sp)
    }

    private fun coinIcon(c: Canvas, cx: Float, cy: Float, r: Float) = Gfx.coin3d(c, cx, cy, r)

    private fun gemIcon(c: Canvas, cx: Float, cy: Float, r: Float) = Gfx.gem3d(c, cx, cy, r)

    private fun wallet(c: Canvas, w: Float, y: Float) {
        val pw = dp(130f)
        val ph = dp(34f)
        val gap = dp(14f)
        val x0 = (w - pw * 2 - gap) / 2f
        for (i in 0..1) {
            val r = RectF(x0 + i * (pw + gap), y, x0 + i * (pw + gap) + pw, y + ph)
            p.color = 0x66000000
            c.drawRoundRect(r, ph / 2, ph / 2, p)
            sp.color = 0x88FFFFFF.toInt()
            sp.strokeWidth = dp(1.2f)
            c.drawRoundRect(r, ph / 2, ph / 2, sp)
            if (i == 0) coinIcon(c, r.left + ph / 2, r.centerY(), ph * 0.36f) else gemIcon(c, r.left + ph / 2, r.centerY(), ph * 0.38f)
            text(c, (if (i == 0) profile.coins else profile.diamonds).toString(), r.centerX() + dp(8f), r.centerY() + dp(6f), dp(16f), Color.WHITE)
        }
    }

    private fun glossy(c: Canvas, r: RectF, color: Int, label: String, id: String?, small: Boolean = false, enabled: Boolean = true) {
        if (id != null && enabled) buttons.add(Btn(id, r, false))
        val down = id != null && pressed == id
        val col = if (enabled) color else 0xFF6B6B7B.toInt()
        val rr = r.height() * 0.3f
        val off = if (down) dp(2f) else 0f
        p.color = Gfx.darken(col, 0.5f)
        rect.set(r.left, r.top + dp(4f), r.right, r.bottom + dp(4f) - off)
        c.drawRoundRect(rect, rr, rr, p)
        rect.set(r.left, r.top + off, r.right, r.bottom + off)
        p.color = Color.BLACK
        p.shader = LinearGradient(r.left, r.top, r.left, r.bottom, Gfx.lighten(col, 0.3f), Gfx.darken(col, 0.12f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rr, rr, p)
        p.shader = null
        p.color = 0x38FFFFFF
        val g = RectF(rect.left + dp(4f), rect.top + dp(3f), rect.right - dp(4f), rect.centerY())
        c.drawRoundRect(g, rr, rr, p)
        sp.color = 0xAAFFFFFF.toInt()
        sp.strokeWidth = dp(1.4f)
        c.drawRoundRect(rect, rr, rr, sp)
        var size = dp(if (small) 13f else 18f)
        tp.textSize = size
        while (tp.measureText(label) > r.width() - dp(12f) && size > dp(8f)) {
            size -= dp(0.5f)
            tp.textSize = size
        }
        text(c, label, rect.centerX(), rect.centerY() + size * 0.35f, size, if (enabled) Color.WHITE else 0xFFD0D0D8.toInt(), true)
    }

    private fun card(c: Canvas, r: RectF, hi: Boolean = false) {
        p.color = if (hi) 0x66FFE082 else 0x40FFFFFF
        c.drawRoundRect(r, dp(16f), dp(16f), p)
        sp.color = if (hi) 0xCCFFE082.toInt() else 0x44FFFFFF
        sp.strokeWidth = dp(1.4f)
        c.drawRoundRect(r, dp(16f), dp(16f), sp)
    }

    private fun rowW(w: Float) = min(w - dp(28f), dp(440f))

    // ---------------------------------------------------------------- LOGIN
    private fun drawLogin(c: Canvas, w: Float, y0: Float): Float {
        var y = y0 + dp(30f)
        text(c, "FunnyLudo", w / 2f, y, dp(44f), 0xFFFFD54F.toInt(), true)
        y += dp(34f)
        text(c, "Sign in to save your progress,", w / 2f, y, dp(15f), 0xFFFFE9A8.toInt())
        y += dp(20f)
        text(c, "achievements and rank worldwide.", w / 2f, y, dp(15f), 0xFFFFE9A8.toInt())
        y += dp(34f)
        text(c, "New players get " + Config.WELCOME_BONUS_COINS + " free coins!", w / 2f, y, dp(18f), Color.WHITE, true)
        y += dp(36f)
        val bw = min(w - dp(50f), dp(320f))
        val gp = RectF((w - bw) / 2f, y, (w + bw) / 2f, y + dp(58f))
        glossy(c, gp, 0xFF2E7D32.toInt(), "SIGN IN WITH GOOGLE PLAY", "login_play")
        y += dp(78f)
        val gu = RectF((w - bw) / 2f, y, (w + bw) / 2f, y + dp(58f))
        glossy(c, gu, 0xFF5E35B1.toInt(), "PLAY AS GUEST", "login_guest")
        y += dp(78f)
        if (!host.games.available) {
            text(c, "(Google sign-in isn't set up in this build yet)", w / 2f, y, dp(11f), 0xAAFFFFFF.toInt())
            y += dp(20f)
        }
        text(c, "You can sign in later from your profile.", w / 2f, y, dp(12f), 0xAAFFFFFF.toInt())
        y += dp(24f)
        text(c, "By continuing you agree that your username, coins and wins", w / 2f, y, dp(10.5f), 0x99FFFFFF.toInt())
        y += dp(15f)
        text(c, "are stored online for ranking, online play and support.", w / 2f, y, dp(10.5f), 0x99FFFFFF.toInt())
        return y + dp(20f)
    }

    // ---------------------------------------------------------------- STORE
    private fun drawStore(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val rw = rowW(w)
        val x = (w - rw) / 2f
        text(c, "FREE REWARDS", x, y + dp(16f), dp(14f), 0xFFFFE9A8.toInt(), false, Paint.Align.LEFT)
        y += dp(26f)
        val free = listOf(
            Triple("ad_coins", "Watch ad  →  +" + Config.AD_REWARD_COINS + " coins", 0xFFFB8C00.toInt()),
            Triple("ad_gems", "Watch ad  →  +" + Config.AD_REWARD_DIAMONDS + " diamond", 0xFF0288D1.toInt()),
            Triple("exch", Config.DIAMOND_COST_IN_COINS.toString() + " coins  →  1 diamond", 0xFF00897B.toInt())
        )
        for (f in free) {
            val r = RectF(x, y, x + rw, y + dp(52f))
            glossy(c, r, f.third, f.second, f.first)
            y += dp(62f)
        }
        y += dp(8f)
        text(c, "COINS & DIAMONDS", x, y + dp(16f), dp(14f), 0xFFFFE9A8.toInt(), false, Paint.Align.LEFT)
        y += dp(26f)
        for (pk in Config.PACKS) {
            val r = RectF(x, y, x + rw, y + dp(58f))
            card(c, r)
            if (pk.coins > 0) coinIcon(c, r.left + dp(30f), r.centerY(), dp(14f)) else gemIcon(c, r.left + dp(30f), r.centerY(), dp(15f))
            text(c, pk.title, r.left + dp(56f), r.centerY() + dp(6f), dp(17f), Color.WHITE, false, Paint.Align.LEFT)
            val pr = host.billing.price(pk.id)
            val b = RectF(r.right - dp(110f), r.top + dp(9f), r.right - dp(10f), r.bottom - dp(9f))
            glossy(c, b, 0xFF43A047.toInt(), if (pr.isEmpty()) "BUY" else pr, "buy:" + pk.id, small = true)
            y += dp(66f)
        }
        text(c, "Diamonds are used for Undo in Quick Play.", w / 2f, y + dp(14f), dp(12f), 0xAAFFFFFF.toInt())
        return y + dp(24f)
    }

    // ---------------------------------------------------------------- THEMES
    private fun drawThemes(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val rw = rowW(w)
        val x = (w - rw) / 2f
        for (t in Themes.ALL) {
            val r = RectF(x, y, x + rw, y + dp(92f))
            val equipped = profile.theme == t.id
            card(c, r, equipped)
            thumb(c, RectF(r.left + dp(8f), r.top + dp(8f), r.left + dp(84f), r.top + dp(84f)), t)

            text(c, t.name, r.left + dp(94f), r.top + dp(28f), dp(17f), Color.WHITE, false, Paint.Align.LEFT)
            val owned = profile.ownsTheme(t.id)
            val sub = when {
                equipped -> "In use"
                owned -> if (t.free) "Free" else "Unlocked"
                else -> "Locked  •  " + profile.themeAds(t.id) + "/" + Config.THEME_ADS_NEEDED + " ads"
            }
            text(c, sub, r.left + dp(94f), r.top + dp(46f), dp(12f), 0xFFFFE9A8.toInt(), false, Paint.Align.LEFT)
            if (owned) {
                val b = RectF(r.left + dp(94f), r.bottom - dp(34f), r.left + dp(94f) + dp(110f), r.bottom - dp(8f))
                if (equipped) glossy(c, b, 0xFF9E9E9E.toInt(), "SELECTED", null, true, false)
                else glossy(c, b, 0xFF43A047.toInt(), "USE", "theme_use:" + t.id, true)
            } else {
                val bw = (rw - dp(94f) - dp(14f)) / 2f
                val b1 = RectF(r.left + dp(94f), r.bottom - dp(34f), r.left + dp(94f) + bw - dp(4f), r.bottom - dp(8f))
                val b2 = RectF(b1.right + dp(8f), b1.top, r.right - dp(10f), b1.bottom)
                glossy(c, b1, 0xFFFB8C00.toInt(), "WATCH AD", "theme_ad:" + t.id, true)
                glossy(c, b2, 0xFF1E88E5.toInt(), Config.THEME_PRICE_COINS.toString(), "theme_coin:" + t.id, true)
            }
            y += dp(102f)
        }
        return y
    }

    private val thumbPawns = arrayOfNulls<Bitmap>(16)
    private val clip = Path()

    /** a real preview of the theme: its backdrop, decorations, board colours and its own pawn */
    private fun thumb(c: Canvas, pv: RectF, t: Theme) {
        val rr = dp(12f)
        clip.reset()
        clip.addRoundRect(pv, rr, rr, Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        p.color = Color.BLACK
        p.shader = RadialGradient(pv.centerX(), pv.centerY(), pv.width() * 0.8f, t.back, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(pv, p)
        p.shader = null
        var seed = 7 + t.id * 13
        fun rnd(): Float {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return (seed % 1000) / 1000f
        }
        for (i in 0 until 9) {
            val x = pv.left + rnd() * pv.width()
            val y = pv.top + rnd() * pv.height()
            val z = dp(3f) + rnd() * dp(4f)
            when (t.deco) {
                1 -> {
                    p.color = 0x9966D94B.toInt()
                    rect.set(x - z * 1.6f, y - z * 0.6f, x + z * 1.6f, y + z * 0.6f)
                    c.drawOval(rect, p)
                }
                2 -> {
                    Gfx.diamondPath(x, y, z, path)
                    p.color = 0xAAFFC62B.toInt()
                    c.drawPath(path, p)
                }
                3 -> {
                    p.color = 0x88FFFFFF.toInt()
                    c.drawCircle(x, y, z * 1.4f, p)
                    c.drawCircle(x + z * 1.4f, y + z * 0.3f, z, p)
                }
                4 -> {
                    p.color = Color.WHITE
                    c.drawCircle(x, y, z * 0.3f, p)
                }
                5 -> {
                    p.color = 0x55FFFFFF
                    c.drawCircle(x, y, z, p)
                    c.drawRect(x - z, y, x + z, y + z * 1.2f, p)
                }
                6 -> {
                    p.color = 0x99FFFFFF.toInt()
                    c.drawCircle(x - z * 0.5f, y, z * 0.6f, p)
                    c.drawCircle(x + z * 0.5f, y, z * 0.6f, p)
                }
                7 -> {
                    p.color = Gfx.withAlpha(t.seat[i % 6], 170)
                    rect.set(x - z, y - z, x + z, y + z)
                    c.drawRoundRect(rect, z * 0.3f, z * 0.3f, p)
                }
                else -> {
                    p.color = 0x55FFFFFF
                    c.drawCircle(x, y, z * 0.4f, p)
                }
            }
        }
        c.restore()
        // mini board
        val b = RectF(pv.left + dp(9f), pv.top + dp(9f), pv.right - dp(9f), pv.bottom - dp(9f))
        p.color = Color.BLACK
        p.shader = LinearGradient(b.left, b.top, b.right, b.bottom, t.frame, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(b, dp(6f), dp(6f), p)
        p.shader = null
        b.inset(dp(3f), dp(3f))
        p.color = t.cell
        c.drawRect(b, p)
        val q = b.width() * 0.4f
        for (i in 0..3) {
            val qx = if (i <= 1) b.left else b.right - q
            val qy = if (i == 1 || i == 2) b.top else b.bottom - q
            p.color = t.seat[i]
            c.drawRect(qx, qy, qx + q, qy + q, p)
            p.color = t.surfTop
            c.drawRect(qx + q * 0.24f, qy + q * 0.24f, qx + q * 0.76f, qy + q * 0.76f, p)
        }
        val m = b.width() * 0.2f / 3f
        p.color = t.seat[0]
        c.drawRect(b.centerX() - m / 2f, b.bottom - q, b.centerX() + m / 2f, b.bottom - m, p)
        p.color = t.seat[1]
        c.drawRect(b.left + m, b.centerY() - m / 2f, b.left + q, b.centerY() + m / 2f, p)
        p.color = t.seat[2]
        c.drawRect(b.centerX() - m / 2f, b.top + m, b.centerX() + m / 2f, b.top + q, p)
        p.color = t.seat[3]
        c.drawRect(b.right - q, b.centerY() - m / 2f, b.right - m, b.centerY() + m / 2f, p)
        p.color = t.star[1]
        c.drawCircle(b.centerX(), b.centerY(), b.width() * 0.08f, p)
        sp.color = t.cellEdge
        sp.strokeWidth = dp(1f)
        c.drawRect(b, sp)
        // the theme's own pawn
        val ph = pv.height() * 0.58f
        val pw = ph / 1.32f
        var bmp = thumbPawns[t.id]
        if (bmp == null) {
            bmp = Gfx.pawn(t.seat[t.id % 6], pw.toInt().coerceAtLeast(8), ph.toInt().coerceAtLeast(10), t.pawn)
            thumbPawns[t.id] = bmp
        }
        rect.set(pv.right - pw - dp(1f), pv.bottom - ph - dp(1f), pv.right - dp(1f), pv.bottom - dp(1f))
        c.drawBitmap(bmp, null, rect, null)
        sp.color = 0xAAFFFFFF.toInt()
        sp.strokeWidth = dp(1.5f)
        c.drawRoundRect(pv, rr, rr, sp)
    }

    // ---------------------------------------------------------------- ACHIEVEMENTS
    private fun drawAch(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val rw = rowW(w)
        val x = (w - rw) / 2f
        val done = Achievements.ALL.count { profile.hasAch(it.key) }
        text(c, "$done / ${Achievements.ALL.size} unlocked   •   Wins ${profile.stat("wins")}   Games ${profile.stat("games")}",
            w / 2f, y + dp(14f), dp(13f), 0xFFFFE9A8.toInt())
        y += dp(28f)
        if (host.games.available && profile.signedIn) {
            val r = RectF(x, y, x + rw, y + dp(42f))
            glossy(c, r, 0xFF2E7D32.toInt(), "OPEN GOOGLE PLAY ACHIEVEMENTS", "ach_gp", true)
            y += dp(52f)
        }
        for (a in Achievements.ALL) {
            val r = RectF(x, y, x + rw, y + dp(74f))
            val got = profile.hasAch(a.key)
            card(c, r, got)
            val cx = r.left + dp(30f)
            p.color = if (got) 0xFFFFC107.toInt() else 0x55FFFFFF
            c.drawCircle(cx, r.centerY(), dp(20f), p)
            Gfx.star(cx, r.centerY(), dp(13f), dp(6f), path)
            p.color = if (got) Color.WHITE else 0x66FFFFFF
            c.drawPath(path, p)
            text(c, a.title, r.left + dp(60f), r.top + dp(24f), dp(16f), Color.WHITE, false, Paint.Align.LEFT)
            text(c, a.desc, r.left + dp(60f), r.top + dp(40f), dp(11.5f), 0xDDFFFFFF.toInt(), false, Paint.Align.LEFT)
            val have = min(profile.stat(a.stat), a.target)
            val bar = RectF(r.left + dp(60f), r.bottom - dp(20f), r.right - dp(90f), r.bottom - dp(12f))
            p.color = 0x44000000
            c.drawRoundRect(bar, dp(4f), dp(4f), p)
            if (have > 0) {
                rect.set(bar.left, bar.top, bar.left + bar.width() * have / a.target, bar.bottom)
                p.color = if (got) 0xFF69F0AE.toInt() else 0xFFFFD54F.toInt()
                c.drawRoundRect(rect, dp(4f), dp(4f), p)
            }
            val rew = (if (a.coins > 0) "+" + a.coins + " coins" else "") + (if (a.gems > 0) (if (a.coins > 0) " " else "") + "+" + a.gems + " gem" else "")
            text(c, if (got) "DONE" else rew, r.right - dp(10f), r.top + dp(24f), dp(11.5f), if (got) 0xFF69F0AE.toInt() else 0xFFFFE9A8.toInt(), false, Paint.Align.RIGHT)
            text(c, "$have/${a.target}", r.right - dp(10f), r.bottom - dp(11f), dp(10.5f), 0xDDFFFFFF.toInt(), false, Paint.Align.RIGHT)
            y += dp(82f)
        }
        return y
    }

    // ---------------------------------------------------------------- RANKING
    private fun drawRank(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val rw = rowW(w)
        val x = (w - rw) / 2f
        text(c, "Ranked by total wins", w / 2f, y + dp(14f), dp(13f), 0xFFFFE9A8.toInt())
        y += dp(26f)
        text(c, "Your wins: " + profile.stat("wins"), w / 2f, y + dp(10f), dp(16f), Color.WHITE, true)
        y += dp(30f)
        if (host.games.available && profile.signedIn && Config.LEADERBOARD_WINS_ID.isNotEmpty()) {
            glossy(c, RectF(x, y, x + rw, y + dp(42f)), 0xFF2E7D32.toInt(), "OPEN GOOGLE PLAY LEADERBOARD", "rank_gp", true)
            y += dp(52f)
        }
        val list = ranks
        if (!host.online.enabled) {
            text(c, "Worldwide ranking isn't set up in this build yet.", w / 2f, y + dp(20f), dp(14f), 0xCCFFFFFF.toInt())
            return y + dp(40f)
        }
        if (list == null) {
            text(c, if (ranksLoading) "Loading..." else "Couldn't load. Tap to retry.", w / 2f, y + dp(30f), dp(15f), 0xCCFFFFFF.toInt())
            if (!ranksLoading) buttons.add(Btn("rank_retry", RectF(0f, y, w, y + dp(60f)), false))
            return y + dp(60f)
        }
        for ((i, rk) in list.withIndex()) {
            val r = RectF(x, y, x + rw, y + dp(46f))
            card(c, r, rk.me)
            val medal = when (i) { 0 -> 0xFFFFD54F.toInt(); 1 -> 0xFFCFD8DC.toInt(); 2 -> 0xFFD7915C.toInt(); else -> 0x55FFFFFF }
            p.color = medal
            c.drawCircle(r.left + dp(24f), r.centerY(), dp(15f), p)
            text(c, (i + 1).toString(), r.left + dp(24f), r.centerY() + dp(5f), dp(14f), if (i < 3) 0xFF3E2723.toInt() else Color.WHITE)
            text(c, rk.name + if (rk.me) "  (you)" else "", r.left + dp(48f), r.centerY() + dp(6f), dp(15f), Color.WHITE, false, Paint.Align.LEFT)
            text(c, rk.wins.toString() + " wins", r.right - dp(12f), r.centerY() + dp(5f), dp(14f), 0xFFFFE9A8.toInt(), false, Paint.Align.RIGHT)
            y += dp(52f)
        }
        if (list.isEmpty()) text(c, "No players yet. Be the first!", w / 2f, y + dp(20f), dp(14f), 0xCCFFFFFF.toInt())
        return y + dp(30f)
    }

    // ---------------------------------------------------------------- ONLINE
    private fun drawOnline(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val bw = min(w - dp(40f), dp(340f))
        val x = (w - bw) / 2f
        if (!host.online.enabled) {
            text(c, "Online rooms aren't set up in this build yet.", w / 2f, y + dp(20f), dp(15f), Color.WHITE)
            text(c, "See SETUP.md (Firebase database URL).", w / 2f, y + dp(42f), dp(12f), 0xAAFFFFFF.toInt())
            return y + dp(60f)
        }
        text(c, "Play with friends anywhere", w / 2f, y + dp(10f), dp(16f), 0xFFFFE9A8.toInt())
        y += dp(36f)
        text(c, "Players in the room", w / 2f, y, dp(13f), 0xCCFFFFFF.toInt())
        y += dp(12f)
        val chip = dp(48f)
        val gap = dp(8f)
        var cx = (w - (chip * 5 + gap * 4)) / 2f
        for (n in 2..6) {
            val r = RectF(cx, y, cx + chip, y + chip)
            buttons.add(Btn("max$n", r, false))
            val sel = n == maxPlayers
            p.color = Color.BLACK
            p.shader = LinearGradient(r.left, r.top, r.left, r.bottom,
                if (sel) 0xFFFFE57F.toInt() else 0xFF7E57C2.toInt(), if (sel) 0xFFFF9800.toInt() else 0xFF311B92.toInt(), Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX(), r.centerY(), chip / 2f, p)
            p.shader = null
            sp.color = if (sel) Color.WHITE else 0x88FFFFFF.toInt()
            sp.strokeWidth = dp(if (sel) 3f else 1.5f)
            c.drawCircle(r.centerX(), r.centerY(), chip / 2f, sp)
            text(c, n.toString(), r.centerX(), r.centerY() + dp(8f), dp(22f), if (sel) 0xFF3E2723.toInt() else Color.WHITE)
            cx += chip + gap
        }
        y += chip + dp(18f)
        glossy(c, RectF(x, y, x + bw, y + dp(44f)), 0xFF00ACC1.toInt(), "PAWNS EACH: $tokens  (tap)", "online_tok", true)
        y += dp(64f)
        text(c, "Coins to play (winner takes the pot)", w / 2f, y, dp(13f), 0xCCFFFFFF.toInt())
        y += dp(10f)
        val sw = (bw - dp(10f)) / 3f
        for ((i, st) in Config.ONLINE_STAKES.withIndex()) {
            val r = RectF(x + (i % 3) * (sw + dp(5f)), y + (i / 3) * dp(46f), x + (i % 3) * (sw + dp(5f)) + sw, y + (i / 3) * dp(46f) + dp(38f))
            glossy(c, r, if (st == stake) 0xFFFFA000.toInt() else 0xFF5E35B1.toInt(), st.toString(), "stake$st", true)
            if (st == stake) {
                sp.color = Color.WHITE
                sp.strokeWidth = dp(2.5f)
                c.drawRoundRect(r, dp(11f), dp(11f), sp)
            }
        }
        y += dp(46f) * ((Config.ONLINE_STAKES.size + 2) / 3) + dp(14f)
        glossy(c, RectF(x, y, x + bw, y + dp(60f)), 0xFFFB8C00.toInt(), "CREATE ROOM", "create")
        y += dp(74f)
        glossy(c, RectF(x, y, x + bw, y + dp(60f)), 0xFF1E88E5.toInt(), "JOIN WITH CODE", "join")
        y += dp(80f)
        text(c, "RANDOM PLAYERS WORLDWIDE", w / 2f, y, dp(13f), 0xFFFFE9A8.toInt())
        y += dp(12f)
        val rw3 = (bw - dp(16f)) / 3f
        for (i in 0..2) {
            val r = RectF(x + i * (rw3 + dp(8f)), y, x + i * (rw3 + dp(8f)) + rw3, y + dp(54f))
            glossy(c, r, 0xFFE53935.toInt(), (i + 2).toString() + " PLAYERS", "rand" + (i + 2), true)
        }
        y += dp(78f)
        text(c, "Stake: " + stake + " coins per player  •  You have " + profile.coins, w / 2f, y, dp(12.5f), Color.WHITE)
        y += dp(20f)
        text(c, "Room games use the pawn setting above; random matches use 2 pawns each.", w / 2f, y, dp(10.5f), 0xAAFFFFFF.toInt())
        return y + dp(24f)
    }

    // ---------------------------------------------------------------- MATCH (searching)
    private fun drawMatch(c: Canvas, w: Float, y0: Float): Float {
        var y = y0 + dp(30f)
        val secs = (System.currentTimeMillis() - matchStart) / 1000
        val dots = ".".repeat((System.currentTimeMillis() / 400 % 4).toInt())
        text(c, "Searching$dots", w / 2f, y, dp(24f), Color.WHITE, true)
        y += dp(34f)
        text(c, "Looking for " + randomN + " players  •  " + secs + "s", w / 2f, y, dp(14f), 0xFFFFE9A8.toInt())
        y += dp(50f)
        // spinning pawns
        for (i in 0..3) {
            val a = System.currentTimeMillis() / 500.0 + i * 1.57
            p.color = Seats.COLORS[i]
            c.drawCircle(w / 2f + (kotlin.math.cos(a) * dp(46f)).toFloat(), y + dp(40f) + (kotlin.math.sin(a) * dp(46f)).toFloat(), dp(13f), p)
        }
        y += dp(120f)
        val bw = min(w - dp(60f), dp(260f))
        glossy(c, RectF((w - bw) / 2f, y, (w + bw) / 2f, y + dp(54f)), 0xFFE53935.toInt(), "CANCEL", "match_cancel")
        return y + dp(70f)
    }

    // ---------------------------------------------------------------- PROFILE
    private fun drawProfile(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val bw = min(w - dp(40f), dp(340f))
        val x = (w - bw) / 2f
        p.color = Color.BLACK
        p.shader = LinearGradient(w / 2f, y, w / 2f, y + dp(80f), 0xFF9575CD.toInt(), 0xFF4527A0.toInt(), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2f, y + dp(40f), dp(40f), p)
        p.shader = null
        text(c, profile.name.take(1).uppercase(), w / 2f, y + dp(54f), dp(38f), Color.WHITE, true)
        y += dp(104f)
        text(c, profile.name, w / 2f, y, dp(22f), Color.WHITE, true)
        y += dp(22f)
        text(c, if (profile.username == null) "No username yet (needed for online play)" else "Username",
            w / 2f, y, dp(12f), 0xFFFFE9A8.toInt())
        y += dp(16f)
        text(c, if (profile.signedIn) "Signed in with Google Play Games" else "Guest account", w / 2f, y, dp(12f), 0xCCFFFFFF.toInt())
        y += dp(26f)
        glossy(c, RectF(x, y, x + bw, y + dp(52f)), 0xFFFB8C00.toInt(), if (profile.username == null) "CHOOSE USERNAME" else "CHANGE USERNAME", "username")
        y += dp(66f)
        if (!profile.signedIn) {
            glossy(c, RectF(x, y, x + bw, y + dp(52f)), 0xFF2E7D32.toInt(), "SIGN IN WITH GOOGLE PLAY", "login_play2")
            y += dp(66f)
        }
        text(c, "YOUR PAWN COLOUR", w / 2f, y + dp(4f), dp(13f), 0xFFFFE9A8.toInt())
        y += dp(16f)
        val cs = dp(40f)
        val cg = (bw - cs * 6) / 5f
        for (i in 0 until 6) {
            val r = RectF(x + i * (cs + cg), y, x + i * (cs + cg) + cs, y + cs)
            buttons.add(Btn("pcol$i", r, false))
            val sel = profile.pawnColor == i
            p.color = Color.BLACK
            p.shader = RadialGradient(r.centerX() - cs * 0.15f, r.centerY() - cs * 0.2f, cs * 0.7f,
                Gfx.lighten(Seats.PALETTE[i], 0.5f), Gfx.darken(Seats.PALETTE[i], 0.25f), Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX(), r.centerY(), cs / 2f, p)
            p.shader = null
            sp.color = if (sel) Color.WHITE else 0x66FFFFFF
            sp.strokeWidth = dp(if (sel) 3.5f else 1.2f)
            c.drawCircle(r.centerX(), r.centerY(), cs / 2f, sp)
        }
        y += cs + dp(18f)
        val stats = listOf("Games played" to "games", "Wins" to "wins", "Pawns cut" to "cuts", "Pawns home" to "homes", "Sixes rolled" to "sixes")
        val card = RectF(x, y, x + bw, y + dp(16f) + stats.size * dp(28f))
        card(c, card)
        var sy = card.top + dp(26f)
        for ((label, key) in stats) {
            text(c, label, card.left + dp(16f), sy, dp(14f), Color.WHITE, false, Paint.Align.LEFT)
            text(c, profile.stat(key).toString(), card.right - dp(16f), sy, dp(14f), 0xFFFFE9A8.toInt(), false, Paint.Align.RIGHT)
            sy += dp(28f)
        }
        y = card.bottom + dp(14f)
        text(c, "Player ID: " + profile.guestId, w / 2f, y, dp(10.5f), 0x88FFFFFF.toInt())
        return y + dp(24f)
    }

    // ---------------------------------------------------------------- SPIN
    private val wheelColors = intArrayOf(0xFFE53935.toInt(), 0xFFFFB300.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt())

    private fun easeOut(t: Float): Float {
        val k = 1f - t
        return 1f - k * k * k * k
    }

    private fun drawSpin(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val r = min(w * 0.40f, dp(150f))
        val cx = w / 2f
        val cy = y + r + dp(24f)
        val now = System.currentTimeMillis()
        if (spinning) {
            val t = ((now - spinT0) / 4500f).coerceIn(0f, 1f)
            wheelRot = spinFrom + (spinTo - spinFrom) * easeOut(t)
            if (t >= 1f) {
                spinning = false
                val pr = Config.SPIN_PRIZES[spinResult]
                spinNote = "You won " + (if (pr.coins > 0) pr.coins.toString() + " coins" else pr.diamonds.toString() + " diamond" + (if (pr.diamonds > 1) "s" else "")) + "!"
                host.toast(spinNote)
            }
        }
        // glow + rim
        p.color = 0x55FFD54F
        c.drawCircle(cx, cy, r + dp(14f), p)
        p.color = 0xFF3E2723.toInt()
        c.drawCircle(cx, cy, r + dp(8f), p)
        val n = Config.SPIN_PRIZES.size
        val seg = 360f / n
        c.save()
        c.rotate(wheelRot, cx, cy)
        rect.set(cx - r, cy - r, cx + r, cy + r)
        for (i in 0 until n) {
            p.color = wheelColors[i % wheelColors.size]
            c.drawArc(rect, -90f + i * seg, seg, true, p)
        }
        sp.color = 0xCCFFFFFF.toInt()
        sp.strokeWidth = dp(1.5f)
        for (i in 0 until n) c.drawArc(rect, -90f + i * seg, seg, true, sp)
        for (i in 0 until n) {
            c.save()
            c.rotate(i * seg + seg / 2f, cx, cy)
            val pr = Config.SPIN_PRIZES[i]
            text(c, pr.label, cx, cy - r * 0.62f, dp(15f), Color.WHITE, true)
            if (pr.diamonds > 0) gemIcon(c, cx, cy - r * 0.84f, dp(7f)) else coinIcon(c, cx, cy - r * 0.84f, dp(6f))
            c.restore()
        }
        c.restore()
        // hub + pointer
        p.color = 0xFF3E2723.toInt()
        c.drawCircle(cx, cy, dp(20f), p)
        p.color = 0xFFFFD54F.toInt()
        c.drawCircle(cx, cy, dp(14f), p)
        path.reset()
        path.moveTo(cx, cy - r + dp(14f))
        path.lineTo(cx - dp(13f), cy - r - dp(16f))
        path.lineTo(cx + dp(13f), cy - r - dp(16f))
        path.close()
        p.color = Color.WHITE
        c.drawPath(path, p)
        sp.color = 0xFF3E2723.toInt()
        sp.strokeWidth = dp(2f)
        c.drawPath(path, sp)

        y = cy + r + dp(34f)
        val wait = profile.spinWaitMs()
        val bw = min(w - dp(50f), dp(320f))
        val x = (w - bw) / 2f
        if (spinNote.isNotEmpty() && !spinning) {
            text(c, spinNote, w / 2f, y, dp(17f), 0xFFFFE9A8.toInt(), true)
            y += dp(26f)
        }
        if (wait == 0L) {
            glossy(c, RectF(x, y, x + bw, y + dp(58f)), 0xFF43A047.toInt(), "FREE SPIN!", "spin_free", false, !spinning)
        } else {
            val hh = wait / 3600000
            val mm = wait / 60000 % 60
            val ss = wait / 1000 % 60
            text(c, "Next free spin in " + hh + ":" + String.format("%02d:%02d", mm, ss), w / 2f, y + dp(6f), dp(15f), Color.WHITE)
            y += dp(22f)
            glossy(c, RectF(x, y, x + bw, y + dp(58f)), 0xFFFB8C00.toInt(), "WATCH AD TO SPIN NOW", "spin_ad", false, !spinning)
        }
        y += dp(74f)
        text(c, "Watch an ad to spin again as often as you like.", w / 2f, y, dp(11.5f), 0xAAFFFFFF.toInt())
        y += dp(22f)
        val total = Config.SPIN_PRIZES.sumOf { it.weight }
        text(c, "ODDS", w / 2f, y, dp(12f), 0xFFFFE9A8.toInt())
        y += dp(6f)
        val parts = Config.SPIN_PRIZES.map {
            (if (it.coins > 0) it.coins.toString() + " coins" else it.diamonds.toString() + " gem" + (if (it.diamonds > 1) "s" else "")) +
                " " + (it.weight * 100 / total) + "%"
        }
        var i = 0
        while (i < parts.size) {
            y += dp(16f)
            val line = parts[i] + (if (i + 1 < parts.size) "   •   " + parts[i + 1] else "")
            text(c, line, w / 2f, y, dp(11f), 0xCCFFFFFF.toInt())
            i += 2
        }
        return y + dp(24f)
    }

    private fun startSpin() {
        // weighted pick
        val total = Config.SPIN_PRIZES.sumOf { it.weight }
        var roll = kotlin.random.Random.nextInt(total)
        var idx = 0
        for ((i, pz) in Config.SPIN_PRIZES.withIndex()) {
            if (roll < pz.weight) {
                idx = i
                break
            }
            roll -= pz.weight
        }
        val pr = Config.SPIN_PRIZES[idx]
        if (pr.coins > 0) profile.addCoins(pr.coins)
        if (pr.diamonds > 0) profile.addDiamonds(pr.diamonds)
        profile.addStat("spins")
        spinResult = idx
        spinNote = ""
        val seg = 360f / Config.SPIN_PRIZES.size
        val cur = ((wheelRot % 360f) + 360f) % 360f
        // pointer is at the top; segment idx centre must end up there
        val target = 360f - (idx * seg + seg / 2f) + (kotlin.random.Random.nextFloat() - 0.5f) * seg * 0.7f
        spinFrom = wheelRot
        spinTo = wheelRot - cur + 360f * 6 + ((target % 360f) + 360f) % 360f
        spinT0 = System.currentTimeMillis()
        spinning = true
        host.playSound("spin")
        invalidate()
    }

    // ---------------------------------------------------------------- LOBBY
    private fun drawLobby(c: Canvas, w: Float, y0: Float): Float {
        var y = y0
        val bw = min(w - dp(40f), dp(340f))
        val x = (w - bw) / 2f
        text(c, "ROOM CODE", w / 2f, y + dp(10f), dp(13f), 0xFFFFE9A8.toInt())
        y += dp(28f)
        // big code plate
        val plate = RectF(x, y, x + bw, y + dp(72f))
        p.color = 0x66000000
        c.drawRoundRect(plate, dp(18f), dp(18f), p)
        sp.color = 0xFFFFD54F.toInt()
        sp.strokeWidth = dp(2.5f)
        c.drawRoundRect(plate, dp(18f), dp(18f), sp)
        tp.textSize = dp(40f)
        var size = dp(40f)
        val spaced = lobbyCode.toCharArray().joinToString(" ")
        while (tp.measureText(spaced) > bw - dp(20f) && size > dp(16f)) {
            size -= dp(1f)
            tp.textSize = size
        }
        text(c, spaced, plate.centerX(), plate.centerY() + size * 0.36f, size, 0xFFFFD54F.toInt(), true)
        y += dp(90f)
        if (randomN == 0) {
            glossy(c, RectF(x, y, x + bw, y + dp(52f)), 0xFF43A047.toInt(), "SHARE CODE WITH FRIENDS", "share")
            y += dp(68f)
        } else {
            text(c, "Matched with random players. Starting soon...", w / 2f, y + dp(14f), dp(13f), Color.WHITE)
            y += dp(34f)
        }
        val fee = lobby?.fee ?: stake
        text(c, "Stake: " + fee + " coins each  •  Winner gets " + fee * (lobby?.members?.size ?: 1), w / 2f, y + dp(4f), dp(12f), 0xFFFFE9A8.toInt())
        y += dp(22f)

        val lb = lobby
        val seats = BoardLayout.seatsFor((lb?.members?.size ?: 2).coerceIn(2, 6))
        text(c, "PLAYERS (" + (lb?.members?.size ?: 0) + "/" + (lb?.max ?: maxPlayers) + ")", x, y + dp(12f), dp(14f), 0xFFFFE9A8.toInt(), false, Paint.Align.LEFT)
        y += dp(22f)
        if (lb != null) {
            for ((i, m) in lb.members.withIndex()) {
                val r = RectF(x, y, x + bw, y + dp(44f))
                card(c, r, m.id == profile.guestId)
                p.color = Seats.COLORS[seats[min(i, seats.size - 1)]]
                c.drawCircle(r.left + dp(24f), r.centerY(), dp(11f), p)
                text(c, m.name + (if (m.id == profile.guestId) "  (you)" else "") + (if (m.id == lb.host) "  ★" else ""),
                    r.left + dp(46f), r.centerY() + dp(6f), dp(15f), Color.WHITE, false, Paint.Align.LEFT)
                y += dp(50f)
            }
        }
        y += dp(10f)
        val isHost = lb != null && lb.host == profile.guestId
        if (randomN > 0) {
            // starts by itself when the room is full
        } else if (isHost) {
            val can = (lb?.members?.size ?: 0) >= 2
            glossy(c, RectF(x, y, x + bw, y + dp(60f)), 0xFFFB8C00.toInt(), if (can) "START GAME" else "WAITING FOR PLAYERS...", "start", false, can)
            y += dp(76f)
        } else {
            text(c, "Waiting for the host to start...", w / 2f, y + dp(20f), dp(14f), 0xFFFFE9A8.toInt())
            y += dp(40f)
        }
        glossy(c, RectF(x, y, x + bw, y + dp(46f)), 0xFFE53935.toInt(), "LEAVE ROOM", "leave", true)
        return y + dp(60f)
    }

    // ------------------------------------------------------------------ lobby logic
    private fun enterLobby(code: String) {
        Seats.setFirst(0)
        lobbyCode = code
        lobby = null
        launched = false
        lobbyAlive = true
        kind = Kind.LOBBY
        scroll = 0f
        invalidate()
        pollLobby()
    }

    private fun pollLobby() {
        if (!lobbyAlive || kind != Kind.LOBBY) return
        host.online.readLobby(lobbyCode) { l ->
            if (!lobbyAlive || kind != Kind.LOBBY) return@readLobby
            if (l == null) {
                if (lobby != null) {
                    host.toast("The room was closed")
                    leaveLobby(false)
                    return@readLobby
                }
            } else {
                lobby = l
                if (randomN > 0 && l.host == profile.guestId && !launched && l.members.size >= randomN) {
                    launched = true
                    val order = l.members.map { it.id }
                    host.online.markStarted(lobbyCode, order)
                    launch(order, l)
                    return@readLobby
                }
                if (l.started && !launched && l.host != profile.guestId) {
                    launched = true
                    host.online.readOrder(lobbyCode) { order ->
                        if (order != null && order.contains(profile.guestId)) launch(order, l) else {
                            host.toast("Couldn't join the game")
                            launched = false
                        }
                    }
                }
            }
            invalidate()
            handler.postDelayed({ pollLobby() }, 1000)
        }
    }

    private fun launch(order: List<String>, l: Online.Lobby) {
        if (!profile.spendCoins(l.fee)) {
            host.toast("You need " + l.fee + " coins for this game")
            leaveLobby(true)
            return
        }
        matchHandle?.finish()
        matchHandle = null
        lobbyAlive = false
        val idx = order.indexOf(profile.guestId)
        val cfg = GameConfig(order.size, BooleanArray(order.size) { true }, l.tokens, false, OnlineInfo(host.online, lobbyCode, idx, l.fee))
        host.startOnline(cfg)
    }

    private fun leaveLobby(notify: Boolean) {
        lobbyAlive = false
        handler.removeCallbacksAndMessages(null)
        matchHandle?.finish()
        matchHandle = null
        if (notify && lobbyCode.isNotEmpty()) host.online.leaveRoom(lobbyCode)
        lobbyCode = ""
        kind = Kind.ONLINE
        scroll = 0f
        invalidate()
    }

    // ------------------------------------------------------------------ input
    private fun hit(x: Float, y: Float): String? {
        for (b in buttons.asReversed()) {
            val yy = if (b.fixed) y else y + scroll
            if (b.r.contains(x, yy)) return b.id
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                downY = e.y
                lastY = e.y
                dragging = false
                pressed = hit(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && abs(e.y - downY) > dp(8f)) {
                    dragging = true
                    pressed = null
                }
                if (dragging) {
                    scroll = (scroll - (e.y - lastY)).coerceIn(0f, max(0f, contentH - height * 0.5f))
                    lastY = e.y
                }
            }
            MotionEvent.ACTION_UP -> {
                val id = if (dragging) null else hit(e.x, e.y)
                val was = pressed
                pressed = null
                if (id != null && id == was) click(id)
            }
            MotionEvent.ACTION_CANCEL -> pressed = null
        }
        invalidate()
        return true
    }

    fun onBack(): Boolean {
        if (visibility != VISIBLE) return false
        if (kind == Kind.LOBBY) leaveLobby(true)
        else if (kind != Kind.LOGIN || profile.loginDone) close()
        return true
    }

    private fun close() {
        lobbyAlive = false
        handler.removeCallbacksAndMessages(null)
        visibility = GONE
        host.closePanel()
    }

    private fun click(id: String) {
        when {
            id == "back" -> onBack()
            id == "login_guest" -> {
                profile.loginDone = true
                host.loginFinished()
                visibility = GONE
            }
            id == "login_play" -> host.games.signIn { ok ->
                if (ok) {
                    profile.loginDone = true
                    host.toast("Signed in as " + profile.name)
                    host.loginFinished()
                    visibility = GONE
                } else host.toast("Google sign-in isn't available. Use Guest for now.")
            }
            id == "ad_coins" -> rewardedFor { profile.addCoins(Config.AD_REWARD_COINS); host.toast("+" + Config.AD_REWARD_COINS + " coins!") }
            id == "ad_gems" -> rewardedFor { profile.addDiamonds(Config.AD_REWARD_DIAMONDS); host.toast("+" + Config.AD_REWARD_DIAMONDS + " diamond!") }
            id == "exch" -> if (profile.spendCoins(Config.DIAMOND_COST_IN_COINS)) {
                profile.addDiamonds(1)
                host.toast("Exchanged for 1 diamond")
            } else host.toast("Not enough coins")
            id.startsWith("buy:") -> host.billing.buy(context as Activity, id.substring(4))
            id.startsWith("theme_use:") -> profile.theme = id.substring(10).toInt()
            id.startsWith("theme_ad:") -> {
                val t = id.substring(9).toInt()
                rewardedFor {
                    if (profile.addThemeAd(t)) host.toast(Themes.get(t).name + " unlocked!")
                    else host.toast("Ad watched: " + profile.themeAds(t) + "/" + Config.THEME_ADS_NEEDED)
                }
            }
            id.startsWith("theme_coin:") -> {
                val t = id.substring(11).toInt()
                if (profile.spendCoins(Config.THEME_PRICE_COINS)) {
                    profile.unlockTheme(t)
                    host.toast(Themes.get(t).name + " unlocked!")
                    Achievements.check(profile).forEach { host.toast("Achievement: " + it.title) }
                } else host.toast("Not enough coins. Visit the Store!")
            }
            id == "ach_gp" -> if (!host.games.showAchievements()) host.toast("Google Play Games isn't available")
            id == "rank_gp" -> if (!host.games.showLeaderboard()) host.toast("Google Play Games isn't available")
            id == "rank_retry" -> loadRanks()
            id.startsWith("max") -> maxPlayers = id.substring(3).toInt()
            id.startsWith("stake") -> stake = id.substring(5).toInt()
            id == "online_tok" -> tokens = if (tokens == 4) 2 else 4
            id == "create" -> ensureOnline(stake) {
                host.toast("Creating room...")
                host.online.createRoom(maxPlayers, tokens, stake) { code ->
                    if (code == null) host.toast("Couldn't create the room. Check your internet.") else {
                        randomN = 0
                        enterLobby(code)
                    }
                }
            }
            id == "join" -> ensureOnline {
                host.promptCode { code ->
                    host.online.joinRoom(code) { err ->
                        if (err == null) {
                            randomN = 0
                            enterLobby(code.trim().uppercase())
                        } else host.toast(err)
                    }
                }
            }
            id.startsWith("rand") -> ensureOnline(stake) { startRandom(id.substring(4).toInt()) }
            id == "match_cancel" -> {
                matchHandle?.cancel()
                matchHandle = null
                kind = Kind.ONLINE
            }
            id.startsWith("pcol") -> profile.pawnColor = id.substring(4).toInt()
            id == "username" -> askUsername {}
            id == "login_play2" -> host.games.signIn { ok ->
                if (ok) host.toast("Signed in as " + profile.name) else host.toast("Google sign-in isn't available in this build")
            }
            id == "spin_free" -> if (!spinning && profile.spinWaitMs() == 0L) {
                profile.lastSpin = System.currentTimeMillis()
                startSpin()
            }
            id == "spin_ad" -> if (!spinning) {
                if (!Config.ADS_ENABLED) host.toast("Ads are turned off in this build")
                else host.ads.showRewarded { ok -> if (ok) startSpin() else host.toast("No ad ready right now. Try again in a moment.") }
            }
            id == "share" -> host.share(
                "Join my FunnyLudo game! Room code: $lobbyCode\nOpen FunnyLudo > Play Online > Join with code."
            )
            id == "start" -> lobby?.let { l ->
                if (l.members.size >= 2 && !launched) {
                    launched = true
                    val order = l.members.map { it.id }
                    host.online.markStarted(lobbyCode, order)
                    launch(order, l)
                }
            }
            id == "leave" -> leaveLobby(true)
        }
    }

    /** online features need a unique username and the entry fee in coins */
    private fun ensureOnline(need: Int = Config.ONLINE_ENTRY_COINS, action: () -> Unit) {
        if (!host.online.enabled) {
            host.toast("Online play isn't set up in this build yet")
            return
        }
        if (profile.username == null) {
            askUsername { ensureOnline(need, action) }
            return
        }
        if (profile.coins < Config.ONLINE_ENTRY_COINS) {
            host.toast("You need at least " + Config.ONLINE_ENTRY_COINS + " coins to play online. Spin the wheel or visit the Store!")
            return
        }
        if (profile.coins < need) {
            host.toast("You need " + need + " coins for that stake")
            return
        }
        action()
    }

    private fun askUsername(then: () -> Unit) {
        host.promptText("Choose a username", Config.USERNAME_MIN.toString() + "-" + Config.USERNAME_MAX + " letters, numbers or _") { raw ->
            val n = raw.trim()
            if (!host.online.validUsername(n)) {
                host.toast("Use " + Config.USERNAME_MIN + "-" + Config.USERNAME_MAX + " letters, numbers or _")
            } else if (!host.online.enabled) {
                profile.username = n
                host.toast("Username saved")
                then()
            } else {
                host.online.claimUsername(n) { err ->
                    if (err == null) {
                        host.toast("Username set to $n")
                        then()
                    } else host.toast(err)
                }
            }
        }
    }

    private fun startRandom(n: Int) {
        randomN = n
        kind = Kind.MATCH
        matchStart = System.currentTimeMillis()
        host.toast("Looking for players...")
        val m = host.online.match(n, stake, { code ->
            enterLobby(code)
        }, { msg ->
            host.toast(msg)
            matchHandle = null
            if (kind == Kind.MATCH) kind = Kind.ONLINE
            invalidate()
        })
        matchHandle = m
        m.start()
    }

    private fun rewardedFor(onReward: () -> Unit) {
        if (!Config.ADS_ENABLED) {
            host.toast("Ads are turned off in this build")
            return
        }
        host.ads.showRewarded { ok ->
            if (ok) {
                onReward()
                Achievements.check(profile).forEach { host.toast("Achievement: " + it.title) }
            } else host.toast("No ad ready right now. Try again in a moment.")
        }
    }
}
