# FunnyLudo v3 – setup guide

The game builds and runs **as is** (GitHub Actions → `FunnyLudo-apk`). Out of the box:

| Feature | Works without setup? |
|---|---|
| Quick Play, Classic, Friends, Bots, themes, coins, diamonds, undo, achievements | Yes |
| Meme / Normal / Off sound | Yes |
| Banner, full-screen and rewarded ads | Yes, but they show Google's **TEST ads** |
| Buying coins/diamonds with real money | Needs Play Console products |
| Google Play Games sign-in, Play leaderboard | Needs Play Console setup |
| Online rooms + in-app worldwide ranking | Needs a free Firebase database |

Everything you have to fill in lives in **`app/src/main/java/com/funny/ludo/Config.kt`** and **`app/src/main/res/values/strings.xml`**.

## 1. Ads (AdMob)
Your AdMob app id and the Banner, Rewarded and App Open ad units are already filled in (`strings.xml`, `Config.kt`).
- **Debug builds** (the APK from GitHub Actions) always show Google's test ads. **Signed release builds** show your real ads.
- **Still missing:** an *Interstitial* ad unit for the full-screen ad at the end of a game. Create one in AdMob and paste its id into `Config.ADMOB_INTERSTITIAL`. Until then release builds skip that ad.
- **App open ad:** shown when a player returns to the menu after 30+ seconds away (`Config.APP_OPEN_MIN_AWAY_MS`), never during a game.
- **app-ads.txt:** the file is in the project root. It must be published at `https://YOUR-DOMAIN/app-ads.txt`, where YOUR-DOMAIN is the developer website on your Play Store listing.
- Never click your own live ads.

## 2. Coins / diamonds for real money (Google Play Billing)
Needs the app uploaded to a Play Console track (internal testing is fine).
Create **managed (one-time) in-app products** with exactly these ids:
`coins_500, coins_2000, coins_5500, coins_12000, coins_30000, gems_10, gems_40, gems_120, bundle_starter, bundle_pro`
(edit the list in `Config.PACKS` to change ids or amounts). Prices come from Play automatically.
Coins/diamonds are credited on the device after Google confirms the purchase.

## 3. Google Play Games (sign-in, achievements, leaderboard)
1. Play Console → your app → **Play Games Services → Setup**. Create the game, link the Android app (package `com.funny.ludo`, SHA‑1 of your signing key).
2. Put the **numeric project id** in `strings.xml` → `game_services_project_id`.
3. Create a leaderboard (score = wins). Put its id in `Config.LEADERBOARD_WINS_ID`.
4. Optional: create achievements and map them in `Config.ACHIEVEMENT_IDS`, e.g. `"first_win" to "CgkI…"`. Keys are in `Achievements.kt`.
5. Set `Config.PLAY_GAMES_ENABLED = true`.

The in-app achievements list works for guests too; Play Games just mirrors it.

## 4. Online rooms + worldwide ranking (Firebase Realtime Database)
1. https://console.firebase.google.com → create a project → **Build → Realtime Database → Create database**.
2. Copy the URL (e.g. `https://my-game-default-rtdb.firebaseio.com`) into `Config.FIREBASE_DB_URL`.
3. Rules tab → paste, then Publish:
```json
{
  "rules": {
    "rooms":       { ".read": true, ".write": true },
    "queue":       { ".read": true, ".write": true },
    "leaderboard": { ".read": true, ".write": true, ".indexOn": ["wins"] },
    "usernames":   { ".read": true, "$n": { ".write": "!data.exists() || !newData.exists()" } },
    "users":       { ".read": false, ".write": true },
    "purchases":   { ".read": false, ".write": true }
  }
}
```
`users` and `purchases` can't be read by the app (players can't see each other's coins), but **you can browse them in the Firebase console** → Realtime Database → Data → `users`. Each player is stored by a random id with: name, username, coins, diamonds, wins, games, signedIn, lastSeen.
These rules are open for writing (there is no login system). A technical cheater could edit his own entry or the ranking. Firebase Anonymous Authentication + stricter rules is the next hardening step.

**Cost:** Firebase Spark (free) gives 1 GB stored, 10 GB/month downloaded and 100 simultaneous connections, no card needed. The app only downloads tiny event messages and polls less when it is not your turn, and finished rooms are deleted. If you ever pass the free limits, switch to Blaze (about $1 per extra GB downloaded). AdMob is free; Google Play keeps a share of coin-pack sales.

How a room works: the host taps **Play Online → Create room**, shares the 6‑letter code through the Android share sheet, friends tap **Join with code**. Every phone runs the same rules; only dice values and chosen moves are exchanged (about once per second). If someone leaves, the others get a **SKIP PLAYER** button after 25 s.

## 5. Build
Upload the project to your GitHub repo (replace everything), click **Commit changes**, open the **Actions** tab, wait for the green tick and download `FunnyLudo-apk`.

## 6. New in this version
- **Usernames:** unique, 3-16 letters/numbers/_ ; claimed on the server (first come, first served). Required before online play. Profile → Choose username.
- **Stake:** the room creator picks the coins to play (`Config.ONLINE_STAKES`, minimum 100). Every player pays it when the game starts and the winner collects the whole pot. Coins can never be cashed out. Declare "simulated gambling" honestly in the Play Console content-rating questionnaire if it asks about wagering virtual currency.
- **Achievements and ranking** only count online games (rooms and random matches).
- **Random worldwide match:** Play Online → Random players (2, 3 or 4). Matches are made through the `queue` node; if nobody is found in 45 s it says so.
- **Lucky spin:** a free spin every 4 hours (`Config.SPIN_COOLDOWN_MS`); watch a rewarded ad to spin again at any time. Prizes and odds are in `Config.SPIN_PRIZES` and shown on the wheel screen.
- **Moves:** all game rules run on the phones; the database only relays dice values and chosen moves.
