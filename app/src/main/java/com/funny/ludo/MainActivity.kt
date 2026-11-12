package com.funny.ludo

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity(), PanelHost {
    private lateinit var sfx: Sfx
    private lateinit var menu: MenuView
    private lateinit var game: LudoView
    private lateinit var panel: PanelView
    private lateinit var profile: Profile
    private var lastConfig: GameConfig? = null
    private val syncHandler = Handler(Looper.getMainLooper())
    private var leftAt = 0L
    private var busyElsewhere = false   // share sheet or purchase screen is open
    private val syncRun = Runnable { online.syncProfile() }

    override lateinit var ads: Ads
    override lateinit var billing: Billing
    override lateinit var games: PlayGamesHelper
    override lateinit var online: Online

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        profile = Profile.get(this)
        Seats.applyTheme(Themes.get(profile.theme))
        sfx = Sfx(this)
        ads = Ads(this)
        billing = Billing(this, profile)
        games = PlayGamesHelper(this, profile)
        online = Online(profile)
        billing.onMessage = { toast(it) }
        billing.onPurchase = { id ->
            online.logPurchase(id)
            online.syncProfile(true)
        }
        // keep the server copy of the profile fresh (coins, wins...) a few seconds after any change
        var shownTheme = profile.theme
        profile.listeners.add {
            if (profile.theme != shownTheme) {
                shownTheme = profile.theme
                Seats.applyTheme(Themes.get(shownTheme))
                menu.themeChanged()
            }
            if (online.enabled && profile.loginDone) {
                syncHandler.removeCallbacks(syncRun)
                syncHandler.postDelayed(syncRun, 8000)
            }
        }

        menu = MenuView(this, sfx, { cfg -> startGame(cfg) }, { id -> openPanel(id) })
        game = LudoView(this, sfx)
        game.onMenu = { showMenu() }
        game.onPlayAgain = {
            val cfg = lastConfig
            if (cfg == null || cfg.online != null) showMenu() else startGame(cfg)
        }
        game.onGameOver = { won, _ -> gameOver(won) }
        game.onToast = { toast(it) }
        game.onNeedDiamonds = { toast("Not enough diamonds! Get some in the Store.") }
        game.onAchievements = { list -> announce(list) }
        game.onInsets = { top -> ads.setTopMargin(top) }
        game.visibility = View.GONE
        panel = PanelView(this, this)
        panel.visibility = View.GONE

        val root = FrameLayout(this)
        root.addView(menu, FrameLayout.LayoutParams(-1, -1))
        root.addView(game, FrameLayout.LayoutParams(-1, -1))
        root.addView(panel, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        ads.canShowAppOpen = { menu.visibility == View.VISIBLE && panel.visibility != View.VISIBLE && hasWindowFocus() }
        ads.init(root)
        games.init()
        billing.connect()
        goFullscreen()

        if (!profile.loginDone) panel.open(PanelView.Kind.LOGIN) else {
            games.silent()
            online.syncProfile(true)
        }
    }

    // ---------------------------------------------------------------- flow
    private fun startGame(cfg: GameConfig) {
        lastConfig = cfg
        // your colour sits bottom-left; online games keep the standard order so every phone agrees
        Seats.setFirst(if (cfg.online != null) 0 else profile.pawnColor)
        menu.visibility = View.GONE
        panel.visibility = View.GONE
        game.visibility = View.VISIBLE
        ads.showBanner(true)
        game.bannerH = ads.bannerHeightPx().toFloat()
        game.startGame(cfg)
    }

    private fun showMenu() {
        game.stop()
        game.visibility = View.GONE
        ads.showBanner(false)
        game.bannerH = 0f
        menu.visibility = View.VISIBLE
        menu.showMain()
    }

    private fun openPanel(id: String) {
        when (id) {
            "store" -> panel.open(PanelView.Kind.STORE)
            "themes" -> panel.open(PanelView.Kind.THEMES)
            "ach" -> panel.open(PanelView.Kind.ACH)
            "rank" -> panel.open(PanelView.Kind.RANK)
            "online" -> panel.open(PanelView.Kind.ONLINE)
            "login" -> panel.open(PanelView.Kind.LOGIN)
            "spin" -> panel.open(PanelView.Kind.SPIN)
            "profile" -> panel.open(if (profile.loginDone) PanelView.Kind.PROFILE else PanelView.Kind.LOGIN)
        }
    }

    /** a game just ended: pay rewards, update stats and achievements, then show the full-screen ad */
    private fun gameOver(won: Boolean) {
        val cfg = lastConfig
        val oi = cfg?.online
        var coins = Config.PLAY_REWARD_COINS
        var note = ""
        if (oi != null) {
            // achievements, stats and the ranking only count online games
            profile.addStat("games")
            if (won) {
                profile.addStat("wins")
                if (cfg.players >= 3) profile.addStat("bigwins")
                if (oi.fee >= 1000) profile.addStat("highwins")
                val pot = oi.fee * cfg.players
                coins += pot
                note = "  (pot " + pot + ")"
            }
        } else if (won) coins += Config.WIN_REWARD_COINS
        profile.addCoins(coins)
        game.resultText = (if (won) "You won!  " else "Better luck next time!  ") + "+" + coins + " coins" + note
        announce(Achievements.check(profile))
        val wins = profile.stat("wins")
        games.submitWins(wins)
        if (online.enabled) online.submitWins(wins)
        ads.showInterstitial {}
    }

    private fun announce(list: List<Ach>) {
        for (a in list) {
            toast("Achievement unlocked: " + a.title)
            games.unlock(a.key)
        }
    }

    // ---------------------------------------------------------------- PanelHost
    override fun toast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun share(text: String) {
        busyElsewhere = true
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(i, "Share room code"))
    }

    override fun promptCode(cb: (String) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        et.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(6))
        et.hint = "ABC123"
        AlertDialog.Builder(this)
            .setTitle("Enter room code")
            .setView(et)
            .setPositiveButton("Join") { _, _ ->
                val code = et.text.toString().trim()
                if (code.length >= 4) cb(code) else toast("Enter the 6 letter code")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun promptText(title: String, hint: String, cb: (String) -> Unit) {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.filters = arrayOf(InputFilter.LengthFilter(Config.USERNAME_MAX))
        et.hint = hint
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et)
            .setPositiveButton("OK") { _, _ -> cb(et.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun startOnline(cfg: GameConfig) {
        startGame(cfg)
    }

    override fun closePanel() {}

    override fun playSound(name: String) = sfx.play(name)

    /** blur the menu while a panel is open (Android 12+); older phones get a solid panel instead */
    override fun panelShown(shown: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            menu.setRenderEffect(
                if (shown) RenderEffect.createBlurEffect(48f, 48f, Shader.TileMode.CLAMP) else null
            )
        }
    }

    override fun loginFinished() {
        online.syncProfile(true)
        if (profile.grantWelcomeBonus()) {
            toast("Welcome! " + Config.WELCOME_BONUS_COINS + " bonus coins added")
        }
    }

    // ---------------------------------------------------------------- lifecycle
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            panel.visibility == View.VISIBLE -> panel.onBack()
            game.visibility == View.VISIBLE -> showMenu()
            else -> super.onBackPressed()
        }
    }

    override fun onStop() {
        super.onStop()
        leftAt = System.currentTimeMillis()
    }

    /** coming back after a real break (not from our own ad, a purchase or the share sheet): show the app-open ad */
    override fun onStart() {
        super.onStart()
        val away = if (leftAt == 0L) 0L else System.currentTimeMillis() - leftAt
        val onMenu = menu.visibility == View.VISIBLE && panel.visibility != View.VISIBLE
        if (Config.ADS_ENABLED && away >= Config.APP_OPEN_MIN_AWAY_MS && onMenu && !busyElsewhere && !ads.showingFullScreen) {
            ads.showAppOpen()
        }
        busyElsewhere = false
    }

    override fun onPause() {
        ads.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        ads.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    /** true full-screen: hides the status and navigation bars, swipe from the edge to peek at them */
    @Suppress("DEPRECATION")
    private fun goFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onDestroy() {
        game.stop()
        ads.destroy()
        billing.destroy()
        sfx.release()
        super.onDestroy()
    }
}
