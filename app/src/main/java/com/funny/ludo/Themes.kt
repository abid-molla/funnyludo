package com.funny.ludo

/** A board look. Normal and Jungle are free, the other six are unlocked with ads or coins. */
class Theme(
    val id: Int,
    val name: String,
    val free: Boolean,
    val back: IntArray,       // backdrop: centre, middle, edge
    val frame: IntArray,      // board frame gradient (light to dark)
    val surfTop: Int,
    val surfBottom: Int,
    val cell: Int,            // plain track cell
    val cellEdge: Int,
    val star: IntArray,       // star top, star bottom
    val deco: Int,            // backdrop decoration: 0 sparkles, 1 leaves, 2 nuggets, 3 clouds, 4 stars, 5 ghosts, 6 hearts, 7 blocks
    val seat: IntArray        // the six player colours in this theme (red, green, yellow, blue, purple, orange families)
) {
    /** pawn shape: 0 classic, 1 leaf sprout, 2 crystal, 3 balloon, 4 rocket, 5 ghost, 6 heart, 7 toy block */
    val pawn: Int get() = id
}

object Themes {
    private fun c(v: Long) = v.toInt()

    val ALL: List<Theme> = listOf(
        Theme(0, "Normal", true,
            intArrayOf(c(0xFF2F7BEA), c(0xFF123F9C), c(0xFF061640)),
            intArrayOf(c(0xFFFFFFFF), c(0xFFE6EEFF), c(0xFFB9CBF2)),
            c(0xFFFFFFFF), c(0xFFF4F7FF), c(0xFFFFFFFF), c(0x66607090), intArrayOf(c(0xFFFFE57F), c(0xFFFF9800)), 0,
            intArrayOf(c(0xFFE53935), c(0xFF2EAD4B), c(0xFFFFC928), c(0xFF1E88E5), c(0xFF8E24AA), c(0xFFFB7A1E))),
        Theme(1, "Jungle", true,
            intArrayOf(c(0xFF3FA34D), c(0xFF14572A), c(0xFF04200E)),
            intArrayOf(c(0xFFB7814A), c(0xFF7A4A1E), c(0xFF3D2209)),
            c(0xFFF4F9E4), c(0xFFD3E6B0), c(0xFFFCFFF2), c(0x50204010), intArrayOf(c(0xFFFFF176), c(0xFFFF8F00)), 1,
            intArrayOf(c(0xFFD9482B), c(0xFF2E8B3D), c(0xFFE3B023), c(0xFF1F8FA8), c(0xFF7B4FA3), c(0xFFD9781E))),
        Theme(2, "Mining", false,
            intArrayOf(c(0xFF6D5A45), c(0xFF33291F), c(0xFF0D0A07)),
            intArrayOf(c(0xFF9E9E9E), c(0xFF5F5F5F), c(0xFF2B2B2B)),
            c(0xFFE9DFCF), c(0xFFC7B594), c(0xFFF7F0E2), c(0x60402810), intArrayOf(c(0xFFFFF59D), c(0xFFFFB300)), 2,
            intArrayOf(c(0xFFC2452D), c(0xFF4E9A56), c(0xFFE6B422), c(0xFF3C7FB8), c(0xFF8457A8), c(0xFFCF7A2A))),
        Theme(3, "Sky", false,
            intArrayOf(c(0xFF8FD3FF), c(0xFF4A9BE8), c(0xFF1C56B0)),
            intArrayOf(c(0xFFFFFFFF), c(0xFFCFE8FF), c(0xFF8DBDEB)),
            c(0xFFFFFFFF), c(0xFFDFF1FF), c(0xFFFFFFFF), c(0x403070B0), intArrayOf(c(0xFFFFFDE7), c(0xFFFFC107)), 3,
            intArrayOf(c(0xFFFF6B6B), c(0xFF4CD08A), c(0xFFFFD84D), c(0xFF4AA8FF), c(0xFFB07CFF), c(0xFFFFA24A))),
        Theme(4, "Space", false,
            intArrayOf(c(0xFF2B1B6B), c(0xFF0E0833), c(0xFF01000A)),
            intArrayOf(c(0xFF00E5FF), c(0xFF5E35F5), c(0xFF2A0F8F)),
            c(0xFF231C55), c(0xFF120D33), c(0xFF3A3484), c(0xCC00E5FF), intArrayOf(c(0xFFFFFF00), c(0xFFFF9100)), 4,
            intArrayOf(c(0xFFFF3D71), c(0xFF00E096), c(0xFFFFE14D), c(0xFF2D9BFF), c(0xFFB44DFF), c(0xFFFF8A2B))),
        Theme(5, "Ghost", false,
            intArrayOf(c(0xFF4B5563), c(0xFF1F2530), c(0xFF07090D)),
            intArrayOf(c(0xFFB2FF59), c(0xFF4CAF50), c(0xFF1B5E20)),
            c(0xFF39404D), c(0xFF22272F), c(0xFFDDE3EA), c(0xAAB2FF59), intArrayOf(c(0xFFE8FFD0), c(0xFF76FF03)), 5,
            intArrayOf(c(0xFFD4587A), c(0xFF7BD64A), c(0xFFE8E06A), c(0xFF5AA7D6), c(0xFF9B7BD9), c(0xFFE39A4D))),
        Theme(6, "Love", false,
            intArrayOf(c(0xFFFF7FA8), c(0xFFC2185B), c(0xFF4A0626)),
            intArrayOf(c(0xFFFFE0EB), c(0xFFFF8FB1), c(0xFFC2185B)),
            c(0xFFFFF5F8), c(0xFFFFD6E4), c(0xFFFFFFFF), c(0x50C2185B), intArrayOf(c(0xFFFFFFFF), c(0xFFFF4081)), 6,
            intArrayOf(c(0xFFFF2E63), c(0xFF3CCB8A), c(0xFFFFC94D), c(0xFF5B8CFF), c(0xFFC25BFF), c(0xFFFF7A59))),
        Theme(7, "Toys", false,
            intArrayOf(c(0xFFFFD54F), c(0xFFFF8A3D), c(0xFFB23C17)),
            intArrayOf(c(0xFF4FC3F7), c(0xFF1E88E5), c(0xFF0D47A1)),
            c(0xFFFFFDE7), c(0xFFFFF0B3), c(0xFFFFFFFF), c(0x50804000), intArrayOf(c(0xFFFFFFFF), c(0xFFFF5252)), 7,
            intArrayOf(c(0xFFFF3B30), c(0xFF34C759), c(0xFFFFCC00), c(0xFF007AFF), c(0xFFAF52DE), c(0xFFFF9500)))
    )

    fun get(id: Int): Theme = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
