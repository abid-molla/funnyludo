package com.funny.ludo

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import com.google.android.gms.common.images.ImageManager
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk

/** Google Play Games: sign-in, achievements mirror and the worldwide leaderboard. Off until Config.PLAY_GAMES_ENABLED. */
class PlayGamesHelper(private val act: Activity, private val profile: Profile) {
    val available get() = Config.PLAY_GAMES_ENABLED

    fun init() {
        if (!available) return
        try {
            PlayGamesSdk.initialize(act)
        } catch (_: Throwable) {
        }
    }

    /** interactive sign-in. callback(true) when signed in. */
    fun signIn(callback: (Boolean) -> Unit) {
        if (!available) {
            callback(false)
            return
        }
        try {
            val c = PlayGames.getGamesSignInClient(act)
            c.isAuthenticated().addOnCompleteListener { t ->
                if (t.isSuccessful && t.result.isAuthenticated) {
                    onSignedIn(callback)
                } else {
                    c.signIn().addOnCompleteListener { s ->
                        if (s.isSuccessful && s.result.isAuthenticated) onSignedIn(callback) else callback(false)
                    }
                }
            }
        } catch (_: Throwable) {
            callback(false)
        }
    }

    /** quiet check at startup for players who signed in before */
    fun silent() {
        if (!available || !profile.signedIn) return
        try {
            PlayGames.getGamesSignInClient(act).isAuthenticated().addOnCompleteListener { t ->
                if (t.isSuccessful && t.result.isAuthenticated) onSignedIn {}
            }
        } catch (_: Throwable) {
        }
    }

    private fun onSignedIn(callback: (Boolean) -> Unit) {
        profile.signedIn = true
        try {
            PlayGames.getPlayersClient(act).currentPlayer.addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    profile.name = t.result.displayName
                    profile.playerId = t.result.playerId
                    loadAvatar(t.result.iconImageUri)
                }
                callback(true)
            }
        } catch (_: Throwable) {
            callback(true)
        }
    }

    /** the player's Google profile picture */
    private fun loadAvatar(uri: Uri?) {
        if (uri == null) return
        try {
            ImageManager.create(act).loadImage({ _, drawable, _ ->
                (drawable as? BitmapDrawable)?.bitmap?.let { profile.avatar = it }
            }, uri)
        } catch (_: Throwable) {
        }
    }

    fun unlock(key: String) {
        val id = Config.ACHIEVEMENT_IDS[key] ?: return
        if (!available || !profile.signedIn) return
        try {
            PlayGames.getAchievementsClient(act).unlock(id)
        } catch (_: Throwable) {
        }
    }

    fun submitWins(wins: Int) {
        if (!available || !profile.signedIn || Config.LEADERBOARD_WINS_ID.isEmpty()) return
        try {
            PlayGames.getLeaderboardsClient(act).submitScore(Config.LEADERBOARD_WINS_ID, wins.toLong())
        } catch (_: Throwable) {
        }
    }

    /** opens Google's worldwide leaderboard screen. returns false if it cannot be shown. */
    fun showLeaderboard(): Boolean {
        if (!available || !profile.signedIn || Config.LEADERBOARD_WINS_ID.isEmpty()) return false
        try {
            PlayGames.getLeaderboardsClient(act).getLeaderboardIntent(Config.LEADERBOARD_WINS_ID)
                .addOnSuccessListener { act.startActivityForResult(it, 9004) }
        } catch (_: Throwable) {
            return false
        }
        return true
    }

    /** opens Google's own achievements screen */
    fun showAchievements(): Boolean {
        if (!available || !profile.signedIn) return false
        try {
            PlayGames.getAchievementsClient(act).achievementsIntent
                .addOnSuccessListener { act.startActivityForResult(it, 9003) }
        } catch (_: Throwable) {
            return false
        }
        return true
    }
}
