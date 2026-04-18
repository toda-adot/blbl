package blbl.cat3399.feature.video

import android.content.Context
import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.feature.player.PlayerPlaylistItem
import blbl.cat3399.feature.player.PlayerPlaylistStore

fun Context.buildVideoDetailIntent(
    card: VideoCard,
    playlistToken: String? = null,
    playlistIndex: Int? = null,
): Intent {
    return Intent(this, VideoDetailActivity::class.java)
        .putExtra(VideoDetailActivity.EXTRA_BVID, card.bvid)
        .putExtra(VideoDetailActivity.EXTRA_CID, card.cid ?: -1L)
        .apply { card.aid?.let { putExtra(VideoDetailActivity.EXTRA_AID, it) } }
        .putExtra(VideoDetailActivity.EXTRA_TITLE, card.title)
        .putExtra(VideoDetailActivity.EXTRA_COVER_URL, card.coverUrl)
        .apply {
            card.ownerName.takeIf { it.isNotBlank() }?.let { putExtra(VideoDetailActivity.EXTRA_OWNER_NAME, it) }
            card.ownerFace?.takeIf { it.isNotBlank() }?.let { putExtra(VideoDetailActivity.EXTRA_OWNER_AVATAR, it) }
            card.ownerMid?.takeIf { it > 0L }?.let { putExtra(VideoDetailActivity.EXTRA_OWNER_MID, it) }
        }
        .apply { playlistToken?.let { putExtra(VideoDetailActivity.EXTRA_PLAYLIST_TOKEN, it) } }
        .apply { playlistIndex?.let { putExtra(VideoDetailActivity.EXTRA_PLAYLIST_INDEX, it) } }
}

fun defaultVideoCardPlaylistItem(card: VideoCard): PlayerPlaylistItem =
    PlayerPlaylistItem(
        bvid = card.bvid,
        cid = card.cid,
        title = card.title,
    )

fun historyVideoCardPlaylistItem(card: VideoCard): PlayerPlaylistItem =
    PlayerPlaylistItem(
        bvid = card.bvid,
        cid = card.cid,
        epId = card.epId,
        aid = card.aid,
        title = card.title,
        seasonId = card.seasonId,
    )

fun List<VideoCard>.buildVideoCardPlaylistToken(
    index: Int,
    source: String,
    playlistItemFactory: (VideoCard) -> PlayerPlaylistItem = ::defaultVideoCardPlaylistItem,
): String? {
    if (isEmpty()) return null
    val safeIndex = index.coerceIn(0, lastIndex)
    return PlayerPlaylistStore.put(
        items = map(playlistItemFactory),
        index = safeIndex,
        source = source,
        uiCards = this,
    )
}

fun Context.openVideoDetailFromCards(
    cards: List<VideoCard>,
    position: Int,
    source: String,
    playlistItemFactory: (VideoCard) -> PlayerPlaylistItem = ::defaultVideoCardPlaylistItem,
) {
    if (cards.isEmpty()) return
    val safePosition = position.coerceIn(0, cards.lastIndex)
    val token =
        cards.buildVideoCardPlaylistToken(
            index = safePosition,
            source = source,
            playlistItemFactory = playlistItemFactory,
        ) ?: return
    startActivity(buildVideoDetailIntent(cards[safePosition], playlistToken = token, playlistIndex = safePosition))
}

fun RecyclerView.removeVideoCardAndRestoreFocus(
    adapter: VideoCardAdapter,
    stableKey: String,
    isAlive: () -> Boolean,
    onEmpty: (() -> Unit)? = null,
): Boolean {
    val removedIndex = adapter.removeByStableKey(stableKey)
    if (removedIndex < 0) return false

    postIfAlive(isAlive = isAlive) {
        val itemCount = adapter.itemCount
        if (itemCount <= 0) {
            onEmpty?.invoke()
            requestFocus()
            return@postIfAlive
        }

        requestFocusAdapterPositionReliable(
            position = removedIndex.coerceIn(0, itemCount - 1),
            smoothScroll = false,
            isAlive = isAlive,
            onFocused = {},
        )
    }
    return true
}
