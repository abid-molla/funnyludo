package com.funny.ludo

class Ach(
    val key: String,
    val title: String,
    val desc: String,
    val stat: String,
    val target: Int,
    val coins: Int,
    val gems: Int
)

object Achievements {
    /** Progress only counts in ONLINE games (rooms and random matches), never in local or bot games. */
    val ALL = listOf(
        Ach("first_game", "Rookie", "Play your first online game", "games", 1, 100, 0),
        Ach("games_10", "Regular", "Play 10 online games", "games", 10, 200, 0),
        Ach("games_50", "Veteran", "Play 50 online games", "games", 50, 500, 1),
        Ach("first_win", "Winner!", "Win your first online game", "wins", 1, 200, 1),
        Ach("wins_5", "Hot Streak", "Win 5 online games", "wins", 5, 300, 1),
        Ach("wins_25", "Champion", "Win 25 online games", "wins", 25, 800, 3),
        Ach("wins_100", "Legend", "Win 100 online games", "wins", 100, 2500, 10),
        Ach("cut_1", "Gotcha!", "Cut an opponent's pawn online", "cuts", 1, 50, 0),
        Ach("cut_25", "Pawn Hunter", "Cut 25 pawns online", "cuts", 25, 300, 1),
        Ach("cut_100", "Pawn Slayer", "Cut 100 pawns online", "cuts", 100, 1000, 3),
        Ach("six_25", "Lucky Roller", "Roll 25 sixes online", "sixes", 25, 200, 0),
        Ach("six_100", "Six Machine", "Roll 100 sixes online", "sixes", 100, 600, 2),
        Ach("home_10", "Homecoming", "Bring 10 pawns home online", "homes", 10, 300, 1),
        Ach("home_50", "Home Run", "Bring 50 pawns home online", "homes", 50, 900, 2),
        Ach("big_table", "Big Table", "Win an online game with 3 or more players", "bigwins", 1, 500, 2),
        Ach("high_roller", "High Roller", "Win an online game with a stake of 1000+", "highwins", 1, 800, 3),
        Ach("collector", "Collector", "Own 4 themes", "themes", 4, 500, 2),
        Ach("rich", "Rich!", "Hold 10,000 coins", "maxcoins", 10000, 0, 5)
    )

    /** unlocks everything that is reached, pays the rewards, and returns the newly unlocked ones */
    fun check(p: Profile): List<Ach> {
        val out = ArrayList<Ach>()
        var again = true
        while (again) {
            again = false
            for (a in ALL) {
                if (!p.hasAch(a.key) && p.stat(a.stat) >= a.target) {
                    p.unlockAch(a.key)
                    if (a.coins > 0) p.addCoins(a.coins)
                    if (a.gems > 0) p.addDiamonds(a.gems)
                    out.add(a)
                    again = true        // rewards can unlock more (e.g. "Rich!")
                }
            }
        }
        return out
    }
}
