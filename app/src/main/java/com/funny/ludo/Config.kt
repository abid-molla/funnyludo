package com.funny.ludo

/**
 * Everything you need to set up for the online / money features lives here.
 * The defaults let the app build and run right away: ads use Google's public TEST ids,
 * and Play Games sign-in / online rooms stay switched off until you fill in your own ids.
 * See SETUP.md for the step-by-step guide.
 */
object Config {
    // ---------------------------------------------------------------- AdMob
    const val ADS_ENABLED = true

    // Your real AdMob ad units (the AdMob *app id* is in res/values/strings.xml -> admob_app_id).
    // Debug builds (the APK from GitHub Actions) automatically show Google's TEST ads instead,
    // so you can never click your own live ads by accident. Signed release builds use these.
    const val ADMOB_BANNER = "ca-app-pub-2144821349143484/4271747374"
    const val ADMOB_REWARDED = "ca-app-pub-2144821349143484/7636936451"
    const val ADMOB_APP_OPEN = "ca-app-pub-2144821349143484/5338076178"

    // Full-screen ad at the end of a game. No ad unit created yet: make an "Interstitial" unit in AdMob
    // and paste its id here. While this is "" release builds simply skip that ad.
    const val ADMOB_INTERSTITIAL = ""

    // Set to true ONLY to check that your real ads load in a debug build. Never tap them, and set it back to
    // false afterwards: clicking your own live ads can get the AdMob account suspended.
    const val LIVE_ADS_IN_DEBUG = false

    // Google's public test units, used in debug builds
    const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"

    /** an app-open ad is shown when the player comes back after at least this long away */
    const val APP_OPEN_MIN_AWAY_MS = 30_000L

    // ---------------------------------------------------------------- Google Play Games
    // Set to true after you create the game in Play Console and put its project id in strings.xml.
    const val PLAY_GAMES_ENABLED = false

    // Leaderboard id from Play Console (score = total wins). Leave "" to disable the leaderboard.
    const val LEADERBOARD_WINS_ID = ""

    // Optional: map the in-game achievement key -> Play Games achievement id (see Achievements.kt for keys).
    // Example: "first_win" to "CgkI....AQ"
    val ACHIEVEMENT_IDS: Map<String, String> = mapOf()

    // ---------------------------------------------------------------- Google Play Billing (real money)
    const val BILLING_ENABLED = true

    class Pack(val id: String, val coins: Int, val diamonds: Int, val title: String)

    /** Create these as managed (consumable) in-app products in Play Console with the same ids. */
    val PACKS = listOf(
        Pack("coins_500", 500, 0, "500 Coins"),
        Pack("coins_2000", 2000, 0, "2,000 Coins"),
        Pack("coins_5500", 5500, 0, "5,500 Coins"),
        Pack("coins_12000", 12000, 0, "12,000 Coins"),
        Pack("coins_30000", 30000, 0, "30,000 Coins"),
        Pack("gems_10", 0, 10, "10 Diamonds"),
        Pack("gems_40", 0, 40, "40 Diamonds"),
        Pack("gems_120", 0, 120, "120 Diamonds"),
        Pack("bundle_starter", 3000, 5, "Starter Bundle: 3,000 coins + 5 diamonds"),
        Pack("bundle_pro", 15000, 30, "Pro Bundle: 15,000 coins + 30 diamonds")
    )

    // ---------------------------------------------------------------- Online rooms (Firebase Realtime Database)
    // Your database URL, for example "https://my-project-default-rtdb.firebaseio.com". Leave "" to disable.
    const val FIREBASE_DB_URL = ""

    // ---------------------------------------------------------------- Online rules
    /** coins every player pays when an online game starts (rooms and random matches). No prize pool. */
    const val ONLINE_ENTRY_COINS = 100

    /** stakes a player can pick for a room or a random match. Everyone pays it; the winner collects the pot. */
    val ONLINE_STAKES = listOf(100, 250, 500, 1000, 2500, 5000)
    const val USERNAME_MIN = 3
    const val USERNAME_MAX = 16

    // ---------------------------------------------------------------- Spin wheel
    const val SPIN_COOLDOWN_MS = 4L * 60 * 60 * 1000

    /** the wheel: label, coins, diamonds, weight. Odds shown to the player = weight / total. */
    class Prize(val label: String, val coins: Int, val diamonds: Int, val weight: Int)

    val SPIN_PRIZES = listOf(
        Prize("50", 50, 0, 24),
        Prize("100", 100, 0, 22),
        Prize("1 gem", 0, 1, 12),
        Prize("200", 200, 0, 15),
        Prize("75", 75, 0, 15),
        Prize("500", 500, 0, 6),
        Prize("2 gems", 0, 2, 5),
        Prize("1000", 1000, 0, 1)
    )

    // ---------------------------------------------------------------- Economy
    const val WELCOME_BONUS_COINS = 2000
    const val WELCOME_BONUS_DIAMONDS = 2
    const val THEME_PRICE_COINS = 2500
    const val THEME_ADS_NEEDED = 2
    const val AD_REWARD_COINS = 150
    const val AD_REWARD_DIAMONDS = 1
    const val DIAMOND_COST_IN_COINS = 500
    const val UNDO_COST_DIAMONDS = 1
    const val WIN_REWARD_COINS = 100
    const val PLAY_REWARD_COINS = 20
}
