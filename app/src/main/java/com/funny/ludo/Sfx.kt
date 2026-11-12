package com.funny.ludo

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Sound effects with three modes: MEME (your mp3 clips), NORMAL (clean synthesized sounds) and OFF.
 * Dice rattle / step pop / boing are tiny effects shared by MEME and NORMAL.
 * "Can't move" and "three sixes" always use the plain NORMAL dud sound (never a meme clip).
 */
class Sfx(private val ctx: Context) {
    enum class Mode(val label: String) { MEME("Meme"), NORMAL("Normal"), OFF("Off") }

    private val prefs = ctx.getSharedPreferences("funnyludo", Context.MODE_PRIVATE)
    private val pool: SoundPool
    private val ids = HashMap<String, Int>()
    private val clips = ArrayList<MediaPlayer>()

    var mode: Mode = try {
        Mode.valueOf(prefs.getString("sound_mode", null) ?: if (prefs.getBoolean("sound", true)) "MEME" else "OFF")
    } catch (_: Exception) {
        Mode.MEME
    }
        set(value) {
            field = value
            prefs.edit().putString("sound_mode", value.name).apply()
            if (value == Mode.OFF) stopClips()
        }

    val enabled: Boolean get() = mode != Mode.OFF

    fun cycle() {
        mode = Mode.values()[(mode.ordinal + 1) % Mode.values().size]
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        ids["roll"] = pool.load(ctx, R.raw.snd_roll, 1)
        ids["step"] = pool.load(ctx, R.raw.snd_step, 1)
        ids["out"] = pool.load(ctx, R.raw.snd_out, 1)
        ids["n_cut"] = pool.load(ctx, R.raw.snd_n_cut, 1)
        ids["n_six"] = pool.load(ctx, R.raw.snd_n_six, 1)
        ids["n_safe"] = pool.load(ctx, R.raw.snd_n_safe, 1)
        ids["n_home"] = pool.load(ctx, R.raw.snd_n_home, 1)
        ids["n_win"] = pool.load(ctx, R.raw.snd_n_win, 1)
        ids["n_nomove"] = pool.load(ctx, R.raw.snd_n_nomove, 1)
        ids["spin"] = pool.load(ctx, R.raw.snd_spin, 1)
    }

    fun play(name: String) {
        if (!enabled) return
        ids[name]?.let { pool.play(it, 1f, 1f, 1, 0, 1f) }
    }

    private fun stopClips() {
        for (mp in clips.toList()) {
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
        }
        clips.clear()
    }

    /** meme clips never pile up: a new one replaces the previous one */
    private fun clip(res: Int) {
        if (!enabled) return
        stopClips()
        try {
            val mp = MediaPlayer.create(ctx, res) ?: return
            mp.setOnCompletionListener {
                clips.remove(it)
                it.release()
            }
            mp.setOnErrorListener { m, _, _ ->
                clips.remove(m)
                m.release()
                true
            }
            clips.add(mp)
            mp.start()
        } catch (_: Exception) {
        }
    }

    private fun event(normal: String, memes: List<Int>) {
        when (mode) {
            Mode.OFF -> {}
            Mode.NORMAL -> {
                stopClips()
                play(normal)
            }
            Mode.MEME -> clip(memes.random())
        }
    }

    fun cut() = event("n_cut", listOf(R.raw.snd_cut1, R.raw.snd_cut2, R.raw.snd_cut3))
    fun six() = event("n_six", listOf(R.raw.snd_six1, R.raw.snd_six2))
    fun safe() = event("n_safe", listOf(R.raw.snd_safe1, R.raw.snd_safe2))
    fun home() = event("n_home", listOf(R.raw.snd_home1, R.raw.snd_home2))
    fun win() = event("n_win", listOf(R.raw.snd_win))

    /** no legal move / three sixes: always the plain sound */
    fun noMove() = play("n_nomove")

    fun release() {
        stopClips()
        pool.release()
    }
}
