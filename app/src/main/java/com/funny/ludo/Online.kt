package com.funny.ludo

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Online friend rooms and the worldwide ranking, using the Firebase Realtime Database REST API
 * (no Firebase SDK needed). Works while Config.FIREBASE_DB_URL is set. See SETUP.md.
 */
class Online(private val profile: Profile) {
    val enabled get() = Config.FIREBASE_DB_URL.isNotEmpty()

    private val io = Executors.newCachedThreadPool()
    private val main = Handler(Looper.getMainLooper())
    private val base get() = Config.FIREBASE_DB_URL.trimEnd('/')

    class Member(val id: String, val name: String)
    class Lobby(val code: String, val host: String, val members: List<Member>, val started: Boolean, val max: Int, val tokens: Int, val fee: Int)

    // ------------------------------------------------------------------ http
    class Resp(val code: Int, val body: String?)

    private fun call(method: String, path: String, body: String?, headers: Map<String, String> = emptyMap()): Resp {
        return try {
            val con = URL("$base/$path").openConnection() as HttpURLConnection
            con.requestMethod = method
            con.connectTimeout = 8000
            con.readTimeout = 8000
            for ((k, v) in headers) con.setRequestProperty(k, v)
            if (body != null) {
                con.doOutput = true
                con.setRequestProperty("Content-Type", "application/json")
                con.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = con.responseCode
            val stream = if (code in 200..299) con.inputStream else con.errorStream
            Resp(code, stream?.bufferedReader()?.use { it.readText() })
        } catch (_: Exception) {
            Resp(-1, null)
        }
    }

    /** body of a successful call, or null on any failure */
    private fun request(method: String, path: String, body: String?): String? {
        val r = call(method, path, body)
        return if (r.code in 200..299) r.body else null
    }

    private fun bg(work: () -> Unit) {
        io.execute {
            try {
                work()
            } catch (_: Exception) {
            }
        }
    }

    private fun ui(f: () -> Unit) {
        main.post(f)
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ------------------------------------------------------------------ rooms
    private fun newCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun createRoom(max: Int, tokens: Int, fee: Int, cb: (String?) -> Unit) {
        bg {
            var code: String? = null
            repeat(5) {
                if (code != null) return@repeat
                val c = newCode()
                val existing = request("GET", "rooms/$c/host.json", null)
                if (existing != null && existing.trim() == "null") {
                    val room = JSONObject()
                    room.put("host", profile.guestId)
                    room.put("max", max)
                    room.put("tokens", tokens)
                    room.put("fee", fee)
                    room.put("started", false)
                    room.put("created", System.currentTimeMillis())
                    val me = JSONObject().put("n", profile.name).put("t", System.currentTimeMillis())
                    room.put("players", JSONObject().put(profile.guestId, me))
                    if (request("PUT", "rooms/$c.json", room.toString()) != null) code = c
                }
            }
            ui { cb(code) }
        }
    }

    /** err is null on success, otherwise a short message */
    fun joinRoom(code: String, cb: (String?) -> Unit) {
        bg {
            val c = code.trim().uppercase()
            val raw = request("GET", "rooms/$c.json", null)
            val err: String? = when {
                raw == null -> "Can't reach the server"
                raw.trim() == "null" -> "Room not found"
                else -> {
                    val j = JSONObject(raw)
                    val players = j.optJSONObject("players") ?: JSONObject()
                    when {
                        j.optBoolean("started") && !players.has(profile.guestId) -> "Game already started"
                        !players.has(profile.guestId) && profile.coins < j.optInt("fee", Config.ONLINE_ENTRY_COINS) ->
                            "This room needs " + j.optInt("fee", Config.ONLINE_ENTRY_COINS) + " coins"
                        !players.has(profile.guestId) && players.length() >= j.optInt("max", 4) -> "Room is full"
                        else -> {
                            val me = JSONObject().put("n", profile.name).put("t", System.currentTimeMillis())
                            if (players.has(profile.guestId)) null
                            else if (request("PUT", "rooms/$c/players/${profile.guestId}.json", me.toString()) != null) null
                            else "Couldn't join"
                        }
                    }
                }
            }
            ui { cb(err) }
        }
    }

    fun readLobby(code: String, cb: (Lobby?) -> Unit) {
        bg {
            val raw = request("GET", "rooms/$code.json", null)
            val lobby = try {
                if (raw == null || raw.trim() == "null") null else {
                    val j = JSONObject(raw)
                    val pl = j.optJSONObject("players") ?: JSONObject()
                    val list = ArrayList<Triple<String, String, Long>>()
                    for (k in pl.keys()) {
                        val m = pl.getJSONObject(k)
                        list.add(Triple(k, m.optString("n", "Player"), m.optLong("t")))
                    }
                    list.sortBy { it.third }
                    Lobby(code, j.optString("host"), list.map { Member(it.first, it.second) },
                        j.optBoolean("started"), j.optInt("max", 4), j.optInt("tokens", 4), j.optInt("fee", Config.ONLINE_ENTRY_COINS))
                }
            } catch (_: Exception) {
                null
            }
            ui { cb(lobby) }
        }
    }

    fun markStarted(code: String, order: List<String>) {
        bg {
            val o = JSONObject()
            order.forEachIndexed { i, id -> o.put(i.toString(), id) }
            request("PUT", "rooms/$code/order.json", o.toString())
            request("PUT", "rooms/$code/started.json", "true")
        }
    }

    fun readOrder(code: String, cb: (List<String>?) -> Unit) {
        bg {
            val raw = request("GET", "rooms/$code/order.json", null)
            val res = try {
                if (raw == null || raw.trim() == "null") null else {
                    val arr = ArrayList<String>()
                    if (raw.trim().startsWith("[")) {
                        val a = org.json.JSONArray(raw)
                        for (i in 0 until a.length()) arr.add(a.getString(i))
                    } else {
                        val o = JSONObject(raw)
                        for (i in 0 until o.length()) arr.add(o.getString(i.toString()))
                    }
                    arr
                }
            } catch (_: Exception) {
                null
            }
            ui { cb(res) }
        }
    }

    /** the host closes the whole room, everybody else just leaves it */
    fun leaveRoom(code: String) {
        bg {
            val host = request("GET", "rooms/$code/host.json", null)?.trim('"', ' ', '\n')
            if (host == profile.guestId) request("DELETE", "rooms/$code.json", null)
            else request("DELETE", "rooms/$code/players/${profile.guestId}.json", null)
        }
    }

    /** called when a game is over: removes the room data a little later (keeps the free database small) */
    fun deleteRoomLater(code: String) {
        bg {
            Thread.sleep(25000)
            request("DELETE", "rooms/$code.json", null)
        }
    }

    // ------------------------------------------------------------------ game events
    fun send(code: String, type: String, a: Int = 0, b: Int = 0) {
        val e = JSONObject().put("s", profile.guestId).put("t", type).put("a", a).put("b", b)
            .put("ts", System.currentTimeMillis())
        bg { request("POST", "rooms/$code/events.json", e.toString()) }
    }

    class Event(val type: String, val a: Int, val b: Int)

    /** polls for new events from other players. call stop() on the returned handle. */
    inner class Poller(private val code: String, private val onEvent: (Event) -> Unit) {
        @Volatile
        private var running = true
        private var lastKey: String? = null

        /** true while we are waiting for another player's move (poll often); false on our own turn (poll rarely) */
        @Volatile
        var fast = true

        fun start() {
            loop()
        }

        fun stop() {
            running = false
        }

        private fun loop() {
            if (!running) return
            bg {
                val q = if (lastKey == null) "orderBy=${enc("\"\$key\"")}"
                else "orderBy=${enc("\"\$key\"")}&startAt=${enc("\"$lastKey\"")}"
                val raw = request("GET", "rooms/$code/events.json?$q", null)
                val fresh = ArrayList<Pair<String, Event>>()
                try {
                    if (raw != null && raw.trim() != "null") {
                        val j = JSONObject(raw)
                        val keys = j.keys().asSequence().toList().sorted()
                        for (k in keys) {
                            if (k == lastKey) continue
                            val e = j.getJSONObject(k)
                            if (e.optString("s") == profile.guestId) {
                                lastKey = k
                                continue
                            }
                            fresh.add(k to Event(e.optString("t"), e.optInt("a"), e.optInt("b")))
                            lastKey = k
                        }
                    }
                } catch (_: Exception) {
                }
                ui {
                    if (running) {
                        for ((_, e) in fresh) onEvent(e)
                        main.postDelayed({ loop() }, if (fast) 600L else 2500L)
                    }
                }
            }
        }
    }

    fun poller(code: String, onEvent: (Event) -> Unit) = Poller(code, onEvent)


    // ------------------------------------------------------------------ usernames
    fun validUsername(n: String) = n.length in Config.USERNAME_MIN..Config.USERNAME_MAX && n.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }

    /** claims a unique username. cb(null) on success, otherwise a message. */
    fun claimUsername(name: String, cb: (String?) -> Unit) {
        bg {
            val key = name.lowercase()
            val me = "\"" + profile.guestId + "\""
            val cur = request("GET", "usernames/$key.json", null)
            var err: String? = null
            if (cur == null) err = "Can't reach the server"
            else if (cur.trim() == me) {
                // already mine
            } else if (cur.trim() != "null") err = "That username is already taken"
            else {
                val r = call("PUT", "usernames/$key.json", me, mapOf("if-match" to "null_etag"))
                err = when (r.code) {
                    in 200..299 -> null
                    412 -> "That username is already taken"
                    else -> "Can't reach the server"
                }
            }
            if (err == null) {
                val old = profile.username
                if (old != null && old.lowercase() != key) {
                    val o = request("GET", "usernames/${old.lowercase()}.json", null)
                    if (o != null && o.trim() == me) request("DELETE", "usernames/${old.lowercase()}.json", null)
                }
            }
            ui {
                if (err == null) profile.username = name
                cb(err)
            }
        }
    }

    // ------------------------------------------------------------------ profile sync (for your Firebase console)
    private var lastSync = 0L

    fun syncProfile(force: Boolean = false) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSync < 15000) return
        lastSync = now
        val j = JSONObject()
            .put("name", profile.name)
            .put("username", profile.username ?: "")
            .put("coins", profile.coins)
            .put("diamonds", profile.diamonds)
            .put("wins", profile.stat("wins"))
            .put("games", profile.stat("games"))
            .put("signedIn", profile.signedIn)
            .put("playerId", profile.playerId ?: "")
            .put("lastSeen", JSONObject().put(".sv", "timestamp"))
        bg { request("PUT", "users/${profile.guestId}.json", j.toString()) }
    }

    /** records a purchase so you can compare with Play Console */
    fun logPurchase(productId: String) {
        if (!enabled) return
        val j = JSONObject().put("product", productId).put("ts", JSONObject().put(".sv", "timestamp"))
        bg { request("POST", "purchases/${profile.guestId}.json", j.toString()) }
    }

    // ------------------------------------------------------------------ random worldwide match
    inner class Match(private val n: Int, private val fee: Int, private val onRoom: (String) -> Unit, private val onFail: (String) -> Unit) {
        @Volatile
        private var running = true
        private var created = false
        private var started = 0L
        private val qk get() = n.toString() + "_" + fee
        private val path get() = "queue/$qk/${profile.guestId}"

        fun start() {
            started = System.currentTimeMillis()
            bg {
                val j = JSONObject().put("name", profile.name).put("room", "").put("ts", JSONObject().put(".sv", "timestamp"))
                request("PUT", "$path.json", j.toString())
                ui { loop() }
            }
        }

        fun cancel() {
            running = false
            bg { request("DELETE", "$path.json", null) }
        }

        fun finish() {
            running = false
            bg { request("DELETE", "$path.json", null) }
        }

        private fun loop() {
            if (!running) return
            if (System.currentTimeMillis() - started > 45000) {
                cancel()
                onFail("No players found right now. Try again in a moment.")
                return
            }
            bg {
                request("PUT", "$path/ts.json", "{\".sv\":\"timestamp\"}")
                val raw = request("GET", "queue/$qk.json", null)
                var room: String? = null
                var iAmHost = false
                var enough = false
                try {
                    if (raw != null && raw.trim() != "null") {
                        val j = JSONObject(raw)
                        class E(val id: String, val ts: Long, val room: String)
                        val all = ArrayList<E>()
                        for (k in j.keys()) {
                            val e = j.getJSONObject(k)
                            all.add(E(k, e.optLong("ts"), e.optString("room", "")))
                        }
                        val maxTs = all.maxOfOrNull { it.ts } ?: 0L
                        val live = all.filter { it.ts >= maxTs - 12000 }.sortedWith(compareBy({ it.ts }, { it.id }))
                        val group = live.take(n)
                        if (group.any { it.id == profile.guestId }) {
                            enough = group.size >= n
                            val host = group.first()
                            iAmHost = host.id == profile.guestId
                            if (!iAmHost && host.room.isNotEmpty()) room = host.room
                        }
                    }
                } catch (_: Exception) {
                }
                ui {
                    if (!running) return@ui
                    if (iAmHost && enough && !created) {
                        created = true
                        createRoom(n, 2, fee) { code ->
                            if (code == null) {
                                created = false
                            } else {
                                bg { request("PUT", "$path/room.json", "\"$code\"") }
                                running = false
                                onRoom(code)
                            }
                        }
                    } else if (room != null) {
                        running = false
                        val code = room
                        joinRoom(code) { err ->
                            finish()
                            if (err == null) onRoom(code) else onFail(err)
                        }
                    }
                    if (running) main.postDelayed({ loop() }, 1500)
                }
            }
        }
    }

    fun match(n: Int, fee: Int, onRoom: (String) -> Unit, onFail: (String) -> Unit) = Match(n, fee, onRoom, onFail)

    // ------------------------------------------------------------------ worldwide ranking
    class Rank(val name: String, val wins: Int, val me: Boolean)

    fun submitWins(wins: Int) {
        if (!enabled) return
        val j = JSONObject().put("name", profile.name).put("wins", wins)
        bg { request("PUT", "leaderboard/${profile.guestId}.json", j.toString()) }
    }

    fun topPlayers(cb: (List<Rank>?) -> Unit) {
        bg {
            val raw = request("GET", "leaderboard.json?orderBy=${enc("\"wins\"")}&limitToLast=50", null)
            val res = try {
                if (raw == null) null else if (raw.trim() == "null") emptyList() else {
                    val j = JSONObject(raw)
                    val l = ArrayList<Rank>()
                    for (k in j.keys()) {
                        val e = j.getJSONObject(k)
                        l.add(Rank(e.optString("name", "Player"), e.optInt("wins"), k == profile.guestId))
                    }
                    l.sortByDescending { it.wins }
                    l
                }
            } catch (_: Exception) {
                null
            }
            ui { cb(res) }
        }
    }
}
