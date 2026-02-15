package blbl.cat3399.feature.player

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.ui.FocusTreeUtils
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.feature.video.VideoCardAdapter

internal fun PlayerActivity.isBottomCardPanelVisible(): Boolean = binding.recommendPanel.visibility == View.VISIBLE

// Backwards-compatible alias (older call sites).
internal fun PlayerActivity.isRecommendPanelVisible(): Boolean = isBottomCardPanelVisible()

internal fun PlayerActivity.initBottomCardPanel() {
    binding.recommendScrim.setOnClickListener { hideBottomCardPanel(restoreFocus = true) }
    binding.recommendPanel.setOnClickListener { hideBottomCardPanel(restoreFocus = true) }

    binding.recyclerRecommend.layoutManager =
        LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    binding.recyclerRecommend.itemAnimator = null
    binding.recyclerRecommend.adapter =
        VideoCardAdapter(
            onClick = { card, pos ->
                when (bottomCardPanelMode) {
                    PlayerActivity.BottomCardPanelMode.Recommend -> playBottomPanelRecommendCard(card)
                    PlayerActivity.BottomCardPanelMode.Playlist -> playBottomPanelPlaylistIndex(pos)
                }
            },
            onLongClick = null,
            fixedItemWidthTvDimen = R.dimen.player_recommend_card_width_tv,
            fixedItemMarginTvDimen = R.dimen.player_recommend_card_margin_tv,
            stableIdKey = { card ->
                buildString {
                    append(card.bvid)
                    append('|')
                    append(card.cid ?: -1L)
                    append('|')
                    append(card.aid ?: -1L)
                    append('|')
                    append(card.epId ?: -1L)
                    append('|')
                    append(card.title)
                }
            },
        )

    binding.recyclerRecommend.addOnChildAttachStateChangeListener(
        object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.setOnKeyListener { v, keyCode, event ->
                    if (!isBottomCardPanelVisible()) return@setOnKeyListener false
                    if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                    when (keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            hideBottomCardPanel(restoreFocus = true)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            // Keep focus from escaping to the player view / other controls.
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val holder = binding.recyclerRecommend.findContainingViewHolder(v)
                            val pos =
                                holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
                                    ?: run {
                                        // Be conservative: never allow LEFT to bubble while the panel is visible.
                                        return@setOnKeyListener true
                                    }

                            if (pos <= 0) return@setOnKeyListener true
                            focusBottomPanelPosition(pos - 1)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val last = (binding.recyclerRecommend.adapter?.itemCount ?: 0) - 1
                            val holder = binding.recyclerRecommend.findContainingViewHolder(v)
                            val pos =
                                holder?.bindingAdapterPosition?.takeIf { it != RecyclerView.NO_POSITION }
                                    ?: run {
                                        // Be conservative: never allow RIGHT to bubble while the panel is visible.
                                        return@setOnKeyListener true
                                    }

                            if (pos >= last) return@setOnKeyListener true
                            focusBottomPanelPosition(pos + 1)
                            true
                        }
                        else -> false
                    }
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                view.setOnKeyListener(null)
            }
        },
    )
}

// Backwards-compatible alias (older call sites).
internal fun PlayerActivity.initRecommendPanel() = initBottomCardPanel()

internal fun PlayerActivity.showRecommendPanel(items: List<VideoCard>) {
    showBottomCardPanel(
        mode = PlayerActivity.BottomCardPanelMode.Recommend,
        items = items,
        focusIndex = 0,
        restoreFocusView = binding.btnRecommend,
    )
}

internal fun PlayerActivity.showPlaylistPanel() {
    val list = playlistItems
    if (list.isEmpty() || playlistIndex !in list.indices) return
    val cards = resolvePlaylistUiCardsForBottomPanel()
    val focusIndex = playlistIndex.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    showBottomCardPanel(
        mode = PlayerActivity.BottomCardPanelMode.Playlist,
        items = cards,
        focusIndex = focusIndex,
        restoreFocusView = binding.btnPlaylist,
    )
}

internal fun PlayerActivity.showBottomCardPanel(
    mode: PlayerActivity.BottomCardPanelMode,
    items: List<VideoCard>,
    focusIndex: Int,
    restoreFocusView: View,
) {
    if (items.isEmpty()) return
    setControlsVisible(true)
    bottomCardPanelMode = mode
    bottomCardPanelRestoreFocus = java.lang.ref.WeakReference(restoreFocusView)
    (binding.recyclerRecommend.adapter as? VideoCardAdapter)?.submit(items)
    binding.recommendScrim.visibility = View.VISIBLE
    binding.recommendPanel.visibility = View.VISIBLE
    binding.recyclerRecommend.scrollToPosition(focusIndex)
    binding.recyclerRecommend.post { focusBottomPanelPosition(focusIndex) }
}

internal fun PlayerActivity.hideBottomCardPanel(restoreFocus: Boolean) {
    if (!isBottomCardPanelVisible() && binding.recommendScrim.visibility != View.VISIBLE) return
    binding.recommendScrim.visibility = View.GONE
    binding.recommendPanel.visibility = View.GONE
    (binding.recyclerRecommend.adapter as? VideoCardAdapter)?.submit(emptyList())
    if (!restoreFocus) return

    setControlsVisible(true)
    val target = bottomCardPanelRestoreFocus?.get()
    if (target != null) {
        target.post { target.requestFocus() }
        return
    }
    // Conservative fallbacks.
    binding.btnPlaylist.post {
        if (binding.btnPlaylist.isShown && binding.btnPlaylist.isEnabled) {
            binding.btnPlaylist.requestFocus()
        } else {
            binding.btnRecommend.requestFocus()
        }
    }
}

// Backwards-compatible alias (older call sites).
internal fun PlayerActivity.hideRecommendPanel(restoreFocus: Boolean) = hideBottomCardPanel(restoreFocus = restoreFocus)

private fun PlayerActivity.playBottomPanelRecommendCard(card: VideoCard) {
    val bvid = card.bvid.trim()
    if (bvid.isBlank()) return
    hideBottomCardPanel(restoreFocus = false)
    startPlayback(
        bvid = bvid,
        cidExtra = card.cid?.takeIf { it > 0 },
        epIdExtra = null,
        aidExtra = null,
        initialTitle = card.title.takeIf { it.isNotBlank() },
    )
    setControlsVisible(true)
    binding.btnRecommend.post { binding.btnRecommend.requestFocus() }
}

private fun PlayerActivity.playBottomPanelPlaylistIndex(index: Int) {
    val list = playlistItems
    if (list.isEmpty() || index !in list.indices) return
    hideBottomCardPanel(restoreFocus = false)
    playPlaylistIndex(index)
    setControlsVisible(true)
    binding.btnPlaylist.post { binding.btnPlaylist.requestFocus() }
}

internal fun PlayerActivity.ensureBottomCardPanelFocus() {
    if (!isBottomCardPanelVisible()) return
    val focused = currentFocus
    val inPanel = focused != null && FocusTreeUtils.isDescendantOf(focused, binding.recommendPanel)
    if (inPanel) return
    val defaultPos =
        when (bottomCardPanelMode) {
            PlayerActivity.BottomCardPanelMode.Recommend -> 0
            PlayerActivity.BottomCardPanelMode.Playlist -> playlistIndex.coerceAtLeast(0)
        }
    binding.recyclerRecommend.post { focusBottomPanelPosition(defaultPos) }
}

// Backwards-compatible alias (older call sites).
internal fun PlayerActivity.ensureRecommendPanelFocus() = ensureBottomCardPanelFocus()

private fun PlayerActivity.resolvePlaylistUiCardsForBottomPanel(): List<VideoCard> {
    val list = playlistItems
    if (list.isEmpty()) return emptyList()
    val cards = playlistUiCards
    if (cards.isNotEmpty() && cards.size == list.size) return cards
    return list.mapIndexed { index, item ->
        val title =
            item.title?.trim()?.takeIf { it.isNotBlank() }
                ?: "视频 ${index + 1}"
        VideoCard(
            bvid = item.bvid,
            cid = item.cid,
            aid = item.aid,
            epId = item.epId,
            title = title,
            coverUrl = "",
            durationSec = 0,
            ownerName = "",
            ownerFace = null,
            ownerMid = null,
            view = null,
            danmaku = null,
            pubDate = null,
            pubDateText = null,
        )
    }
}

private fun PlayerActivity.focusBottomPanelPosition(position: Int) {
    val adapter = binding.recyclerRecommend.adapter ?: return
    val count = adapter.itemCount
    if (position !in 0 until count) return

    binding.recyclerRecommend.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        ?: run {
            binding.recyclerRecommend.scrollToPosition(position)
            binding.recyclerRecommend.postIfAlive(
                isAlive = { isBottomCardPanelVisible() && binding.recyclerRecommend.isAttachedToWindow },
            ) {
                binding.recyclerRecommend.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
            }
        }
}
