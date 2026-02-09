package blbl.cat3399.core.model

data class DanmakuShield(
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColor: Boolean = true,
    val allowSpecial: Boolean = true,
    val aiEnabled: Boolean = false,
    val aiLevel: Int = 3,
) {
    fun allow(danmaku: Danmaku): Boolean {
        val mode = danmaku.mode
        val typeAllowed = when (mode) {
            1, 2, 3, 6 -> allowScroll
            4 -> allowBottom
            5 -> allowTop
            7, 8, 9 -> false
            else -> allowSpecial
        }
        if (!typeAllowed) return false

        if (!allowColor) {
            val rgb = danmaku.color and 0xFFFFFF
            val isWhite = (rgb == 0) || (rgb == 0xFFFFFF)
            if (!isWhite) return false
        }

        if (aiEnabled) {
            val level = aiLevel.coerceIn(0, 10).let { if (it == 0) 3 else it }
            if (danmaku.weight < level) return false
        }

        return true
    }
}
