package com.funny.ludo

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/** AdMob: small banner while playing, full-screen ad when a game ends, rewarded ads for coins / diamonds / themes. */
class Ads(private val act: Activity) {
    private var banner: AdView? = null
    private var inter: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var rewardedLoading = false
    private var interLoading = false
    private var appOpen: AppOpenAd? = null
    private var appOpenLoading = false
    private var appOpenLoadedAt = 0L
    private var ready = false
    private var coldUntil = 0L

    /** asked before the app-open ad is shown right after launch */
    var canShowAppOpen: (() -> Boolean)? = null

    /** true while one of our own full-screen ads is on screen */
    var showingFullScreen = false
        private set

    // debug builds use Google's test units, release builds use the real ones from Config
    private val debug = !Config.LIVE_ADS_IN_DEBUG && (act.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    private val bannerId get() = if (debug) Config.TEST_BANNER else Config.ADMOB_BANNER
    private val interId get() = if (debug) Config.TEST_INTERSTITIAL else Config.ADMOB_INTERSTITIAL
    private val rewardedId get() = if (debug) Config.TEST_REWARDED else Config.ADMOB_REWARDED
    private val appOpenId get() = if (debug) Config.TEST_APP_OPEN else Config.ADMOB_APP_OPEN

    fun init(container: FrameLayout) {
        if (!Config.ADS_ENABLED) return
        try {
            MobileAds.initialize(act) {}
        } catch (_: Throwable) {
            return
        }
        val v = AdView(act)
        v.setAdSize(AdSize.BANNER)
        v.adUnitId = bannerId
        v.visibility = View.GONE
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        container.addView(v, lp)
        banner = v
        v.loadAd(AdRequest.Builder().build())
        ready = true
        coldUntil = System.currentTimeMillis() + 10000
        loadInterstitial()
        loadRewarded()
        loadAppOpen()
    }

    // ------------------------------------------------------------------ app open ad
    private fun loadAppOpen() {
        if (!ready || appOpenLoading || appOpen != null || appOpenId.isEmpty()) return
        appOpenLoading = true
        AppOpenAd.load(act, appOpenId, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad
                appOpenLoadedAt = System.currentTimeMillis()
                appOpenLoading = false
                // app just opened: show it once, as soon as it is ready (the viewer can skip it)
                if (System.currentTimeMillis() < coldUntil && canShowAppOpen?.invoke() == true) {
                    coldUntil = 0L
                    showAppOpen()
                }
            }

            override fun onAdFailedToLoad(e: LoadAdError) {
                appOpen = null
                appOpenLoading = false
            }
        })
    }

    /** shows the app-open ad if one is loaded and still fresh (they expire after 4 hours). Returns true if shown. */
    fun showAppOpen(): Boolean {
        val ad = appOpen
        if (ad == null || showingFullScreen || System.currentTimeMillis() - appOpenLoadedAt > 4L * 3600 * 1000) {
            if (ad != null && !showingFullScreen) appOpen = null
            loadAppOpen()
            return false
        }
        appOpen = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                loadAppOpen()
            }

            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                showingFullScreen = false
                loadAppOpen()
            }
        }
        return try {
            showingFullScreen = true
            ad.show(act)
            true
        } catch (_: Throwable) {
            showingFullScreen = false
            false
        }
    }

    fun setTopMargin(px: Int) {
        val v = banner ?: return
        val lp = v.layoutParams as? FrameLayout.LayoutParams ?: return
        if (lp.topMargin != px) {
            lp.topMargin = px
            v.layoutParams = lp
        }
    }

    fun showBanner(show: Boolean) {
        banner?.visibility = if (show) View.VISIBLE else View.GONE
    }

    /** banner height in px (50dp) so the game can leave room for it */
    fun bannerHeightPx(): Int = if (banner == null) 0 else (50 * act.resources.displayMetrics.density).toInt()

    private fun loadInterstitial() {
        if (!ready || interLoading || inter != null || interId.isEmpty()) return
        interLoading = true
        InterstitialAd.load(act, interId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                inter = ad
                interLoading = false
            }

            override fun onAdFailedToLoad(e: LoadAdError) {
                inter = null
                interLoading = false
            }
        })
    }

    /** full-screen ad at the end of a game (win or lose). `done` runs when it is closed or if no ad is ready. */
    fun showInterstitial(done: () -> Unit) {
        val ad = inter
        if (ad == null) {
            loadInterstitial()
            done()
            return
        }
        inter = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                loadInterstitial()
                done()
            }

            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                showingFullScreen = false
                loadInterstitial()
                done()
            }
        }
        try {
            showingFullScreen = true
            ad.show(act)
        } catch (_: Throwable) {
            showingFullScreen = false
            done()
        }
    }

    private fun loadRewarded() {
        if (!ready || rewardedLoading || rewarded != null) return
        rewardedLoading = true
        RewardedAd.load(act, rewardedId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                rewardedLoading = false
            }

            override fun onAdFailedToLoad(e: LoadAdError) {
                rewarded = null
                rewardedLoading = false
            }
        })
    }

    val rewardedReady: Boolean get() = rewarded != null

    /** shows a rewarded video. `result(true)` only when the viewer earned the reward. */
    fun showRewarded(result: (Boolean) -> Unit) {
        val ad = rewarded
        if (ad == null) {
            loadRewarded()
            result(false)
            return
        }
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showingFullScreen = false
                loadRewarded()
                result(earned)
            }

            override fun onAdFailedToShowFullScreenContent(e: com.google.android.gms.ads.AdError) {
                showingFullScreen = false
                loadRewarded()
                result(false)
            }
        }
        try {
            showingFullScreen = true
            ad.show(act) { earned = true }
        } catch (_: Throwable) {
            showingFullScreen = false
            result(false)
        }
    }

    fun pause() = banner?.pause()
    fun resume() = banner?.resume()
    fun destroy() = banner?.destroy()
}
