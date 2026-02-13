package blbl.cat3399.feature.player

import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.api.BiliApiException
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.ui.SingleChoiceDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import blbl.cat3399.core.model.VideoCard

internal fun PlayerActivity.applyActionButtonsVisibility() {
    val enabled = BiliClient.prefs.playerActionButtons.toSet()
    binding.btnLike.visibility = if (enabled.contains(AppPrefs.PLAYER_ACTION_BTN_LIKE)) View.VISIBLE else View.GONE
    binding.btnCoin.visibility = if (enabled.contains(AppPrefs.PLAYER_ACTION_BTN_COIN)) View.VISIBLE else View.GONE
    binding.btnFav.visibility = if (enabled.contains(AppPrefs.PLAYER_ACTION_BTN_FAV)) View.VISIBLE else View.GONE
    updateActionButtonsUi()
}

internal fun PlayerActivity.updateActionButtonsUi() {
    updateLikeButtonUi()
    updateCoinButtonUi()
    updateFavButtonUi()
}

internal fun PlayerActivity.updateLikeButtonUi() {
    val active = actionLiked
    val colorRes = if (active) R.color.blbl_blue else R.color.blbl_text
    binding.btnLike.imageTintList = ContextCompat.getColorStateList(this, colorRes)
    binding.btnLike.isEnabled = true
    binding.btnLike.alpha = 1.0f
}

internal fun PlayerActivity.updateCoinButtonUi() {
    val active = actionCoinCount > 0
    val colorRes = if (active) R.color.blbl_blue else R.color.blbl_text
    binding.btnCoin.imageTintList = ContextCompat.getColorStateList(this, colorRes)
    binding.btnCoin.isEnabled = true
    binding.btnCoin.alpha = 1.0f
}

internal fun PlayerActivity.updateFavButtonUi() {
    val active = actionFavored
    val colorRes = if (active) R.color.blbl_blue else R.color.blbl_text
    binding.btnFav.imageTintList = ContextCompat.getColorStateList(this, colorRes)
    binding.btnFav.isEnabled = true
    binding.btnFav.alpha = 1.0f
}

internal fun PlayerActivity.onLikeButtonClicked() {
    if (likeActionJob?.isActive == true) return
    val requestBvid = currentBvid.trim().takeIf { it.isNotBlank() } ?: return
    val targetLike = !actionLiked
    setControlsVisible(true)

    likeActionJob =
        lifecycleScope.launch {
            try {
                updateLikeButtonUi()
                BiliApi.archiveLike(bvid = requestBvid, aid = currentAid, like = targetLike)
                if (currentBvid != requestBvid) return@launch
                actionLiked = targetLike
                Toast.makeText(this@onLikeButtonClicked, if (targetLike) "点赞成功" else "已取消赞", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                val e = t as? BiliApiException
                if (targetLike && e?.apiCode == 65006) {
                    if (currentBvid != requestBvid) return@launch
                    actionLiked = true
                    Toast.makeText(this@onLikeButtonClicked, "已点赞", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                    Toast.makeText(this@onLikeButtonClicked, msg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                likeActionJob = null
                updateLikeButtonUi()
            }
        }
}

internal fun PlayerActivity.onCoinButtonClicked() {
    if (coinActionJob?.isActive == true) return
    if (actionCoinCount >= 2) return
    val requestBvid = currentBvid.trim().takeIf { it.isNotBlank() } ?: return
    setControlsVisible(true)

    coinActionJob =
        lifecycleScope.launch {
            try {
                updateCoinButtonUi()
                BiliApi.coinAdd(bvid = requestBvid, aid = currentAid, multiply = 1, selectLike = false)
                if (currentBvid != requestBvid) return@launch
                actionCoinCount = (actionCoinCount + 1).coerceAtMost(2)
                Toast.makeText(this@onCoinButtonClicked, "投币成功", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                val e = t as? BiliApiException
                if (e?.apiCode == 34005) {
                    if (currentBvid != requestBvid) return@launch
                    actionCoinCount = 2
                    Toast.makeText(this@onCoinButtonClicked, "已达到投币上限", Toast.LENGTH_SHORT).show()
                } else {
                    val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                    Toast.makeText(this@onCoinButtonClicked, msg, Toast.LENGTH_SHORT).show()
                }
            } finally {
                coinActionJob = null
                updateCoinButtonUi()
            }
        }
}

internal fun PlayerActivity.onFavButtonClicked() {
    if (favDialogJob?.isActive == true || favApplyJob?.isActive == true) return
    val selfMid = BiliClient.cookies.getCookieValue("DedeUserID")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
    if (selfMid == null) {
        Toast.makeText(this, "请先登录后再收藏", Toast.LENGTH_SHORT).show()
        return
    }
    val aid = currentAid?.takeIf { it > 0L }
    if (aid == null) {
        Toast.makeText(this, "未获取到 aid，暂不支持收藏", Toast.LENGTH_SHORT).show()
        return
    }

    val requestBvid = currentBvid
    val requestAid = aid
    setControlsVisible(true)

    favDialogJob =
        lifecycleScope.launch {
            try {
                updateFavButtonUi()
                val folders =
                    withContext(Dispatchers.IO) {
                        BiliApi.favFoldersWithState(upMid = selfMid, rid = requestAid)
                    }
                if (currentBvid != requestBvid) return@launch
                if (folders.isEmpty()) {
                    Toast.makeText(this@onFavButtonClicked, "未获取到收藏夹", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val initial = folders.filter { it.favState }.map { it.mediaId }.toSet()

                actionFavored = initial.isNotEmpty()
                updateFavButtonUi()

                val labels =
                    folders.map { folder ->
                        if (folder.favState) "${folder.title}（已收藏）" else folder.title
                    }
                SingleChoiceDialog.show(
                    context = this@onFavButtonClicked,
                    title = "选择收藏夹",
                    items = labels,
                    checkedIndex = 0,
                    negativeText = "取消",
                    onNegative = { binding.btnFav.post { binding.btnFav.requestFocus() } },
                ) { index, _ ->
                    val picked = folders.getOrNull(index)
                    if (picked == null) {
                        binding.btnFav.post { binding.btnFav.requestFocus() }
                        return@show
                    }

                    val nextSelected = initial.toMutableSet()
                    if (nextSelected.contains(picked.mediaId)) nextSelected.remove(picked.mediaId) else nextSelected.add(picked.mediaId)
                    val add = (nextSelected - initial).toList()
                    val del = (initial - nextSelected).toList()
                    if (add.isNotEmpty() || del.isNotEmpty()) {
                        applyFavSelection(rid = requestAid, add = add, del = del, selected = nextSelected.toSet())
                    }
                    binding.btnFav.post { binding.btnFav.requestFocus() }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                val e = t as? BiliApiException
                val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "加载收藏夹失败")
                Toast.makeText(this@onFavButtonClicked, msg, Toast.LENGTH_SHORT).show()
            } finally {
                favDialogJob = null
                updateFavButtonUi()
            }
        }
}

internal fun PlayerActivity.applyFavSelection(
    rid: Long,
    add: List<Long>,
    del: List<Long>,
    selected: Set<Long>,
) {
    if (favApplyJob?.isActive == true) return
    favApplyJob =
        lifecycleScope.launch {
            try {
                updateFavButtonUi()
                BiliApi.favResourceDeal(rid = rid, addMediaIds = add, delMediaIds = del)
                actionFavored = selected.isNotEmpty()
                updateFavButtonUi()
                Toast.makeText(this@applyFavSelection, "收藏已更新", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                val e = t as? BiliApiException
                val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "操作失败")
                Toast.makeText(this@applyFavSelection, msg, Toast.LENGTH_SHORT).show()
            } finally {
                favApplyJob = null
                updateFavButtonUi()
            }
        }
}

internal fun PlayerActivity.updatePlaylistControls() {
    val hasPlaylist = playlistItems.isNotEmpty() && playlistIndex in playlistItems.indices
    val canSwitch = playlistItems.size >= 2 && playlistIndex in playlistItems.indices

    run {
        val alpha = if (canSwitch) 1.0f else 0.35f
        binding.btnPrev.isEnabled = canSwitch
        binding.btnNext.isEnabled = canSwitch
        binding.btnPrev.alpha = alpha
        binding.btnNext.alpha = alpha
    }

    binding.btnPlaylist.visibility = if (hasPlaylist) android.view.View.VISIBLE else android.view.View.GONE
    binding.btnPlaylist.isEnabled = hasPlaylist
    binding.btnPlaylist.alpha = if (hasPlaylist) 1.0f else 0.35f
}

internal fun PlayerActivity.updateUpButton() {
    val enabled = currentUpMid > 0L
    val alpha = if (enabled) 1.0f else 0.35f
    binding.btnUp.isEnabled = enabled
    binding.btnUp.alpha = alpha
}

internal fun PlayerActivity.showPlaylistDialog() {
    val list = playlistItems
    if (list.isEmpty() || playlistIndex !in list.indices) {
        Toast.makeText(this, "暂无播放列表", Toast.LENGTH_SHORT).show()
        return
    }

    val title =
        playlistUgcSeasonTitle?.let { "合集：$it" }
            ?: if (isMultiPagePlaylist(list, currentBvid)) "分P" else "播放列表"
    val labels =
        list.mapIndexed { index, item ->
            item.title?.trim()?.takeIf { it.isNotBlank() }
                ?: "视频 ${index + 1}"
        }

    SingleChoiceDialog.show(
        context = this,
        title = title,
        items = labels,
        checkedIndex = playlistIndex,
        negativeText = "关闭",
    ) { which, _ ->
        if (which != playlistIndex) playPlaylistIndex(which)
    }
}

internal fun PlayerActivity.showRecommendDialog() {
    val requestBvid = currentBvid.trim()
    if (requestBvid.isBlank()) {
        Toast.makeText(this, "缺少 bvid", Toast.LENGTH_SHORT).show()
        return
    }

    val cached = relatedVideosCache?.takeIf { it.bvid == requestBvid }?.items.orEmpty()
    if (cached.isNotEmpty()) {
        showRecommendDialog(items = cached)
        return
    }

    if (relatedVideosFetchJob?.isActive == true) {
        Toast.makeText(this, "推荐视频加载中…", Toast.LENGTH_SHORT).show()
        return
    }

    val token = ++relatedVideosFetchToken
    relatedVideosFetchJob =
        lifecycleScope.launch {
            try {
                val list =
                    withContext(Dispatchers.IO) {
                        BiliApi.archiveRelated(bvid = requestBvid, aid = currentAid)
                    }
                if (token != relatedVideosFetchToken) return@launch
                if (currentBvid.trim() != requestBvid) return@launch

                relatedVideosCache = PlayerActivity.RelatedVideosCache(bvid = requestBvid, items = list)
                if (list.isEmpty()) {
                    Toast.makeText(this@showRecommendDialog, "暂无推荐视频", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                showRecommendDialog(items = list)
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                val e = t as? BiliApiException
                val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "加载推荐视频失败")
                Toast.makeText(this@showRecommendDialog, msg, Toast.LENGTH_SHORT).show()
            } finally {
                if (token == relatedVideosFetchToken) relatedVideosFetchJob = null
            }
        }
}

internal fun PlayerActivity.showRecommendDialog(items: List<VideoCard>) {
    val labels =
        items.mapIndexed { index, item ->
            item.title.trim().takeIf { it.isNotBlank() } ?: "视频 ${index + 1}"
        }
    SingleChoiceDialog.show(
        context = this,
        title = "推荐视频",
        items = labels,
        checkedIndex = 0,
        negativeText = "关闭",
        onNegative = { binding.btnRecommend.post { binding.btnRecommend.requestFocus() } },
    ) { which, _ ->
        val picked = items.getOrNull(which) ?: return@show
        val bvid = picked.bvid.trim()
        if (bvid.isBlank()) return@show
        startPlayback(
            bvid = bvid,
            cidExtra = picked.cid?.takeIf { it > 0 },
            epIdExtra = null,
            aidExtra = null,
            initialTitle = picked.title.takeIf { it.isNotBlank() },
        )
        setControlsVisible(true)
        binding.btnRecommend.post { binding.btnRecommend.requestFocus() }
    }
}

internal fun PlayerActivity.pickRecommendedVideo(items: List<VideoCard>, excludeBvid: String): VideoCard? {
    val safeExclude = excludeBvid.trim()
    return items.firstOrNull { it.bvid.isNotBlank() && it.bvid != safeExclude }
        ?: items.firstOrNull { it.bvid.isNotBlank() }
}

internal fun PlayerActivity.playRecommendedNext(userInitiated: Boolean) {
    val requestBvid = currentBvid.trim()
    if (requestBvid.isBlank()) {
        playNext(userInitiated = userInitiated)
        return
    }

    val cached = relatedVideosCache?.takeIf { it.bvid == requestBvid }?.items.orEmpty()
    val cachedPicked = pickRecommendedVideo(cached, excludeBvid = requestBvid)
    if (cachedPicked != null) {
        startPlayback(
            bvid = cachedPicked.bvid,
            cidExtra = cachedPicked.cid?.takeIf { it > 0 },
            epIdExtra = null,
            aidExtra = null,
            initialTitle = cachedPicked.title.takeIf { it.isNotBlank() },
        )
        return
    }

    val activeJob = relatedVideosFetchJob
    if (activeJob?.isActive == true) {
        lifecycleScope.launch {
            activeJob.join()
            if (currentBvid.trim() != requestBvid) return@launch

            val refreshed = relatedVideosCache?.takeIf { it.bvid == requestBvid }?.items.orEmpty()
            val picked = pickRecommendedVideo(refreshed, excludeBvid = requestBvid)
            if (picked != null) {
                startPlayback(
                    bvid = picked.bvid,
                    cidExtra = picked.cid?.takeIf { it > 0 },
                    epIdExtra = null,
                    aidExtra = null,
                    initialTitle = picked.title.takeIf { it.isNotBlank() },
                )
            } else {
                if (userInitiated) Toast.makeText(this@playRecommendedNext, "暂无推荐视频", Toast.LENGTH_SHORT).show()
                playNext(userInitiated = userInitiated)
            }
        }
        return
    }

    val token = ++relatedVideosFetchToken
    relatedVideosFetchJob =
        lifecycleScope.launch {
            try {
                val list =
                    withContext(Dispatchers.IO) {
                        BiliApi.archiveRelated(bvid = requestBvid, aid = currentAid)
                    }
                if (token != relatedVideosFetchToken) return@launch
                if (currentBvid.trim() != requestBvid) return@launch

                relatedVideosCache = PlayerActivity.RelatedVideosCache(bvid = requestBvid, items = list)
                val picked = pickRecommendedVideo(list, excludeBvid = requestBvid)
                if (picked == null) {
                    if (userInitiated) Toast.makeText(this@playRecommendedNext, "暂无推荐视频", Toast.LENGTH_SHORT).show()
                    playNext(userInitiated = userInitiated)
                    return@launch
                }

                startPlayback(
                    bvid = picked.bvid,
                    cidExtra = picked.cid?.takeIf { it > 0 },
                    epIdExtra = null,
                    aidExtra = null,
                    initialTitle = picked.title.takeIf { it.isNotBlank() },
                )
            } catch (t: Throwable) {
                if (t is CancellationException) return@launch
                if (userInitiated) {
                    val e = t as? BiliApiException
                    val msg = e?.apiMessage?.takeIf { it.isNotBlank() } ?: (t.message ?: "加载推荐视频失败")
                    Toast.makeText(this@playRecommendedNext, msg, Toast.LENGTH_SHORT).show()
                }
                playNext(userInitiated = userInitiated)
            } finally {
                if (token == relatedVideosFetchToken) relatedVideosFetchJob = null
            }
        }
}

internal fun PlayerActivity.playNextByPlaybackMode(userInitiated: Boolean) {
    when (resolvedPlaybackMode()) {
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND -> playRecommendedNext(userInitiated = userInitiated)
        else -> playNext(userInitiated = userInitiated)
    }
}

internal fun PlayerActivity.playNext(userInitiated: Boolean) {
    val list = playlistItems
    if (list.isEmpty() || playlistIndex !in list.indices) {
        if (userInitiated) Toast.makeText(this, "暂无下一个视频", Toast.LENGTH_SHORT).show()
        return
    }
    val next = playlistIndex + 1
    if (next !in list.indices) {
        if (userInitiated) Toast.makeText(this, "已是最后一个视频", Toast.LENGTH_SHORT).show()
        return
    }
    playPlaylistIndex(next)
}

internal fun PlayerActivity.playPrev(userInitiated: Boolean) {
    val list = playlistItems
    if (list.isEmpty() || playlistIndex !in list.indices) {
        if (userInitiated) Toast.makeText(this, "暂无上一个视频", Toast.LENGTH_SHORT).show()
        return
    }
    val prev = playlistIndex - 1
    if (prev !in list.indices) {
        if (userInitiated) Toast.makeText(this, "已是第一个视频", Toast.LENGTH_SHORT).show()
        return
    }
    playPlaylistIndex(prev)
}

internal fun PlayerActivity.playPlaylistIndex(index: Int) {
    val list = playlistItems
    val item = list.getOrNull(index) ?: return
    if (item.bvid.isBlank() && (item.aid ?: 0L) <= 0L) return

    // Avoid pointless reload when list has only one item.
    if (index == playlistIndex) {
        val exo = player ?: return
        exo.seekTo(0)
        exo.playWhenReady = true
        exo.play()
        return
    }

    playlistIndex = index.coerceIn(0, list.lastIndex)
    playlistToken?.let { PlayerPlaylistStore.updateIndex(it, playlistIndex) }
    updatePlaylistControls()
    startPlayback(
        bvid = item.bvid,
        cidExtra = item.cid?.takeIf { it > 0 },
        epIdExtra = item.epId?.takeIf { it > 0 },
        aidExtra = item.aid?.takeIf { it > 0 },
        initialTitle = item.title,
    )
}
