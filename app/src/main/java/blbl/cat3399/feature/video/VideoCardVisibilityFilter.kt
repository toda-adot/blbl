package blbl.cat3399.feature.video

import blbl.cat3399.core.model.VideoCard

object VideoCardVisibilityFilter {
    fun filterVisible(cards: List<VideoCard>): List<VideoCard> = cards

    fun filterVisibleFresh(
        cards: List<VideoCard>,
        loadedStableKeys: Set<String>,
    ): List<VideoCard> {
        val seen = HashSet<String>(cards.size)
        return cards.filter { card ->
            val stableKey = card.stableKey()
            if (loadedStableKeys.contains(stableKey)) return@filter false
            if (!seen.add(stableKey)) return@filter false
            true
        }
    }
}
