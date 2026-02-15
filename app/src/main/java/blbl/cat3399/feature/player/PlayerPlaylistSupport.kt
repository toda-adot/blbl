package blbl.cat3399.feature.player

import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.VideoCard
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal fun parseUgcSeasonPlaylistFromView(ugcSeason: JSONObject): List<PlayerPlaylistItem> {
    val sections = ugcSeason.optJSONArray("sections") ?: return emptyList()
    val out = ArrayList<PlayerPlaylistItem>(ugcSeason.optInt("ep_count").coerceAtLeast(0))
    for (i in 0 until sections.length()) {
        val section = sections.optJSONObject(i) ?: continue
        val eps = section.optJSONArray("episodes") ?: continue
        for (j in 0 until eps.length()) {
            val ep = eps.optJSONObject(j) ?: continue
            val arc = ep.optJSONObject("arc") ?: JSONObject()
            val bvid = ep.optString("bvid", "").trim().ifBlank { arc.optString("bvid", "").trim() }
            if (bvid.isBlank()) continue
            val cid = ep.optLong("cid").takeIf { it > 0 } ?: arc.optLong("cid").takeIf { it > 0 }
            val aid = ep.optLong("aid").takeIf { it > 0 } ?: arc.optLong("aid").takeIf { it > 0 }
            val title = ep.optString("title", "").trim().takeIf { it.isNotBlank() } ?: arc.optString("title", "").trim()
            out.add(
                PlayerPlaylistItem(
                    bvid = bvid,
                    cid = cid,
                    aid = aid,
                    title = title.takeIf { it.isNotBlank() },
                ),
            )
        }
    }
    return out
}

internal fun parseMultiPagePlaylistFromView(viewData: JSONObject, bvid: String, aid: Long?): List<PlayerPlaylistItem> {
    val pages = viewData.optJSONArray("pages") ?: return emptyList()
    if (pages.length() <= 1) return emptyList()
    val out = ArrayList<PlayerPlaylistItem>(pages.length())
    for (i in 0 until pages.length()) {
        val obj = pages.optJSONObject(i) ?: continue
        val cid = obj.optLong("cid").takeIf { it > 0 } ?: continue
        val page = obj.optInt("page").takeIf { it > 0 } ?: (i + 1)
        val part = obj.optString("part", "").trim()
        val title =
            if (part.isBlank()) {
                "P$page"
            } else {
                "P$page $part"
            }
        out.add(
            PlayerPlaylistItem(
                bvid = bvid,
                cid = cid,
                aid = aid,
                title = title,
            ),
        )
    }
    return out
}

internal fun parseUgcSeasonPlaylistFromArchivesList(json: JSONObject): List<PlayerPlaylistItem> {
    val archives = json.optJSONObject("data")?.optJSONArray("archives") ?: return emptyList()
    val out = ArrayList<PlayerPlaylistItem>(archives.length())
    for (i in 0 until archives.length()) {
        val obj = archives.optJSONObject(i) ?: continue
        val bvid = obj.optString("bvid", "").trim()
        if (bvid.isBlank()) continue
        val aid = obj.optLong("aid").takeIf { it > 0 }
        val title = obj.optString("title", "").trim().takeIf { it.isNotBlank() }
        out.add(
            PlayerPlaylistItem(
                bvid = bvid,
                aid = aid,
                title = title,
            ),
        )
    }
    return out
}

internal fun pickPlaylistIndexForCurrentMedia(list: List<PlayerPlaylistItem>, bvid: String, aid: Long?, cid: Long?): Int {
    val safeBvid = bvid.trim()
    if (cid != null && cid > 0) {
        val byCid = list.indexOfFirst { it.cid == cid }
        if (byCid >= 0) return byCid
    }
    if (aid != null && aid > 0) {
        val byAid = list.indexOfFirst { it.aid == aid }
        if (byAid >= 0) return byAid
    }
    if (safeBvid.isNotBlank()) {
        val byBvid = list.indexOfFirst { it.bvid == safeBvid }
        if (byBvid >= 0) return byBvid
    }
    return -1
}

internal fun isMultiPagePlaylist(list: List<PlayerPlaylistItem>, currentBvid: String): Boolean {
    if (list.size < 2) return false
    val bvid = currentBvid.trim().takeIf { it.isNotBlank() } ?: return false
    return list.all { it.bvid == bvid && (it.cid ?: 0L) > 0L }
}

data class PlayerPlaylist(
    val items: List<PlayerPlaylistItem>,
    val uiCards: List<VideoCard>,
    val source: String?,
    val createdAtMs: Long,
    var index: Int,
)

object PlayerPlaylistStore {
    private const val MAX_PLAYLISTS = 30

    private val store = ConcurrentHashMap<String, PlayerPlaylist>()
    private val order = ArrayDeque<String>()
    private val lock = Any()

    fun put(
        items: List<PlayerPlaylistItem>,
        index: Int,
        source: String? = null,
        uiCards: List<VideoCard> = emptyList(),
    ): String {
        val outItems = ArrayList<PlayerPlaylistItem>(items.size)
        val outCards = ArrayList<VideoCard>(items.size)
        val hasCards = uiCards.isNotEmpty()

        for (i in items.indices) {
            val item = items[i]
            if (item.bvid.isBlank()) continue
            outItems.add(item)
            if (hasCards) {
                val card = uiCards.getOrNull(i)
                if (card != null) {
                    outCards.add(card)
                } else {
                    val fallbackTitle =
                        item.title?.trim()?.takeIf { it.isNotBlank() }
                            ?: "视频 ${outItems.size}"
                    outCards.add(
                        VideoCard(
                            bvid = item.bvid,
                            cid = item.cid,
                            aid = item.aid,
                            epId = item.epId,
                            title = fallbackTitle,
                            coverUrl = "",
                            durationSec = 0,
                            ownerName = "",
                            ownerFace = null,
                            ownerMid = null,
                            view = null,
                            danmaku = null,
                            pubDate = null,
                            pubDateText = null,
                        ),
                    )
                }
            }
        }

        val safeIndex = index.coerceIn(0, (outItems.size - 1).coerceAtLeast(0))
        val token = UUID.randomUUID().toString()
        val playlist =
            PlayerPlaylist(
                items = outItems,
                uiCards = if (hasCards) outCards else emptyList(),
                source = source,
                createdAtMs = System.currentTimeMillis(),
                index = safeIndex,
            )
        store[token] = playlist
        synchronized(lock) {
            order.addLast(token)
            trimLocked()
        }
        AppLog.d(
            "PlayerPlaylistStore",
            "put size=${outItems.size} cards=${if (hasCards) outCards.size else 0} idx=$safeIndex source=${source.orEmpty()} token=${token.take(8)}",
        )
        return token
    }

    fun get(token: String): PlayerPlaylist? {
        if (token.isBlank()) return null
        return store[token]
    }

    fun updateIndex(token: String, index: Int) {
        if (token.isBlank()) return
        val p = store[token] ?: return
        p.index = index.coerceIn(0, (p.items.size - 1).coerceAtLeast(0))
    }

    fun remove(token: String) {
        if (token.isBlank()) return
        store.remove(token)
        synchronized(lock) {
            order.remove(token)
        }
    }

    private fun trimLocked() {
        while (order.size > MAX_PLAYLISTS) {
            val oldest = order.removeFirstOrNull() ?: break
            store.remove(oldest)
        }
    }
}
