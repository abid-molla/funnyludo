package com.funny.ludo

import android.content.Context
import android.graphics.Bitmap
import java.util.UUID

/** Player profile, wallet, stats, unlocked themes and achievements. Saved on the device. */
class Profile private constructor(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("funnyludo_profile", Context.MODE_PRIVATE)

    /** called whenever coins, gems, theme or stats change so screens can refresh */
    val listeners = ArrayList<() -> Unit>()
    private fun changed() {
        for (l in listeners.toList()) l()
    }

    // ------------------------------------------------------------ account
    var loginDone: Boolean
        get() = sp.getBoolean("login_done", false)
        set(v) {
            sp.edit().putBoolean("login_done", v).apply()
        }

    var signedIn: Boolean
        get() = sp.getBoolean("signed_in", false)
        set(v) {
            sp.edit().putBoolean("signed_in", v).apply()
            changed()
        }

    /** unique name chosen by the player (claimed on the server). null until chosen. */
    var username: String?
        get() = sp.getString("username", null)
        set(v) {
            sp.edit().putString("username", v).apply()
            changed()
        }

    /** name shown to others: username, else Google Play name, else Guest number */
    var name: String
        get() = username ?: sp.getString("name", null) ?: ("Guest" + (1000 + (guestId.hashCode() and 0x7FFFFFFF) % 9000))
        set(v) {
            sp.edit().putString("name", v).apply()
            changed()
        }

    /** time of the last spin of the wheel (device clock) */
    var lastSpin: Long
        get() = sp.getString("last_spin", "0")?.toLongOrNull() ?: 0L
        set(v) {
            sp.edit().putString("last_spin", v.toString()).apply()
            changed()
        }

    /** milliseconds until the free spin is ready (0 = ready now) */
    fun spinWaitMs(): Long {
        val now = System.currentTimeMillis()
        val last = lastSpin
        if (last == 0L) return 0L
        if (now < last) return Config.SPIN_COOLDOWN_MS   // clock set back: not ready
        return (last + Config.SPIN_COOLDOWN_MS - now).coerceAtLeast(0L)
    }

    /** random id used to identify this player in online rooms */
    val guestId: String
        get() {
            var id = sp.getString("guest_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                sp.edit().putString("guest_id", id).apply()
            }
            return id
        }

    var playerId: String?
        get() = sp.getString("player_id", null)
        set(v) {
            sp.edit().putString("player_id", v).apply()
        }

    /** Google account picture (kept in memory only), null for guests */
    var avatar: Bitmap? = null
        set(v) {
            field = v
            changed()
        }

    // ------------------------------------------------------------ wallet
    val coins: Int get() = sp.getInt("coins", 0)
    val diamonds: Int get() = sp.getInt("diamonds", 0)

    fun addCoins(n: Int) {
        val v = coins + n
        sp.edit().putInt("coins", v).apply()
        if (v > stat("maxcoins")) setStat("maxcoins", v)
        changed()
    }

    fun spendCoins(n: Int): Boolean {
        if (coins < n) return false
        sp.edit().putInt("coins", coins - n).apply()
        changed()
        return true
    }

    fun addDiamonds(n: Int) {
        sp.edit().putInt("diamonds", diamonds + n).apply()
        changed()
    }

    fun spendDiamonds(n: Int): Boolean {
        if (diamonds < n) return false
        sp.edit().putInt("diamonds", diamonds - n).apply()
        changed()
        return true
    }

    /** gives the one-time welcome gift. Returns true the first time only. */
    fun grantWelcomeBonus(): Boolean {
        if (sp.getBoolean("welcome_given", false)) return false
        sp.edit().putBoolean("welcome_given", true).apply()
        addCoins(Config.WELCOME_BONUS_COINS)
        addDiamonds(Config.WELCOME_BONUS_DIAMONDS)
        return true
    }

    // ------------------------------------------------------------ stats
    fun stat(key: String) = sp.getInt("stat_$key", 0)

    fun addStat(key: String, n: Int = 1) {
        sp.edit().putInt("stat_$key", stat(key) + n).apply()
        changed()
    }

    fun setStat(key: String, v: Int) {
        sp.edit().putInt("stat_$key", v).apply()
        changed()
    }

    // ------------------------------------------------------------ achievements
    fun hasAch(key: String) = sp.getBoolean("ach_$key", false)

    fun unlockAch(key: String) {
        sp.edit().putBoolean("ach_$key", true).apply()
        changed()
    }

    /** colour of your own pawns (index into Seats.BASE_COLORS) */
    var pawnColor: Int
        get() = sp.getInt("pawn_color", 0)
        set(v) {
            sp.edit().putInt("pawn_color", v).apply()
            changed()
        }

    // ------------------------------------------------------------ themes
    var theme: Int
        get() = sp.getInt("theme", 0)
        set(v) {
            sp.edit().putInt("theme", v).apply()
            changed()
        }

    fun ownsTheme(id: Int) = Themes.get(id).free || sp.getBoolean("theme_own_$id", false)

    fun ownedThemeCount() = Themes.ALL.count { ownsTheme(it.id) }

    fun unlockTheme(id: Int) {
        sp.edit().putBoolean("theme_own_$id", true).apply()
        setStat("themes", ownedThemeCount())
    }

    fun themeAds(id: Int) = sp.getInt("theme_ads_$id", 0)

    /** counts one watched ad towards a theme; returns true when the theme just got unlocked */
    fun addThemeAd(id: Int): Boolean {
        val n = themeAds(id) + 1
        sp.edit().putInt("theme_ads_$id", n).apply()
        if (n >= Config.THEME_ADS_NEEDED) {
            unlockTheme(id)
            return true
        }
        changed()
        return false
    }

    companion object {
        private var inst: Profile? = null
        fun get(ctx: Context): Profile {
            val i = inst ?: Profile(ctx)
            inst = i
            return i
        }
    }
}
