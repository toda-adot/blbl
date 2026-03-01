package blbl.cat3399.feature.video

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemVideoCardBinding

class VideoCardAdapter(
    private val onClick: (VideoCard, Int) -> Unit,
    private val onLongClick: ((VideoCard, Int) -> Boolean)? = null,
    private val fixedItemWidthDimenRes: Int? = null,
    private val fixedItemMarginDimenRes: Int? = null,
    private val stableIdKey: ((VideoCard) -> String)? = null,
    private val isSelected: ((VideoCard, Int) -> Boolean)? = null,
) : RecyclerView.Adapter<VideoCardAdapter.Vh>() {
    private val items = ArrayList<VideoCard>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<VideoCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<VideoCard>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    fun snapshot(): List<VideoCard> = items.toList()

    override fun getItemId(position: Int): Long {
        val item = items[position]
        val key = stableIdKey?.invoke(item) ?: item.stableKey()
        return key.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemVideoCardBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding, fixedItemWidthDimenRes, fixedItemMarginDimenRes)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], position, onClick, onLongClick, isSelected)

    override fun getItemCount(): Int = items.size

    class Vh(
        private val binding: ItemVideoCardBinding,
        private val fixedItemWidthDimenRes: Int?,
        private val fixedItemMarginDimenRes: Int?,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            applyFixedSizing()
        }

        fun bind(
            item: VideoCard,
            position: Int,
            onClick: (VideoCard, Int) -> Unit,
            onLongClick: ((VideoCard, Int) -> Boolean)?,
            isSelected: ((VideoCard, Int) -> Boolean)?,
        ) {
            applyFixedSizing()

            binding.root.isSelected = isSelected?.invoke(item, position) == true

            val coverLeftBottomText = item.coverLeftBottomText?.trim()
            val isEpisodeStyleCard = item.coverLeftBottomText != null
            binding.tvCoverLeftBottom.isVisible = coverLeftBottomText?.isNotBlank() == true
            binding.tvCoverLeftBottom.text = coverLeftBottomText.orEmpty()

            binding.tvTitle.text = item.title
            val subtitleText =
                item.pubDateText
                    ?: if (item.ownerName.isBlank()) "" else "UP ${item.ownerName}"
            binding.tvSubtitle.text = subtitleText
            val pubDateText = item.pubDate?.let { Format.pubDateText(it) }.orEmpty()
            binding.tvPubdate.text = pubDateText
            val showSubtitleRow = !isEpisodeStyleCard && (subtitleText.isNotBlank() || pubDateText.isNotBlank())
            binding.llSubtitle.isVisible = showSubtitleRow
            binding.tvSubtitle.isVisible = showSubtitleRow && subtitleText.isNotBlank()
            binding.tvPubdate.isVisible = showSubtitleRow && pubDateText.isNotBlank()

            val showDuration = !isEpisodeStyleCard && item.durationSec > 0
            binding.tvDuration.isVisible = showDuration
            if (showDuration) {
                binding.tvDuration.text = Format.duration(item.durationSec)
            }

            val viewCount = item.view?.takeIf { it > 0 }
            val danmakuCount = item.danmaku?.takeIf { it > 0 }
            val showStats = !isEpisodeStyleCard && (viewCount != null || danmakuCount != null)
            binding.llStats.isVisible = showStats
            binding.ivStatPlay.isVisible = viewCount != null
            binding.tvView.isVisible = viewCount != null
            binding.ivStatDanmaku.isVisible = danmakuCount != null
            binding.tvDanmaku.isVisible = danmakuCount != null
            viewCount?.let { binding.tvView.text = Format.count(it) }
            danmakuCount?.let { binding.tvDanmaku.text = Format.count(it) }

            val accessBadgeText = item.accessBadgeText?.trim()?.takeIf { it.isNotBlank() }
            binding.tvChargeBadge.isVisible = accessBadgeText != null || item.isChargingArc
            binding.tvChargeBadge.text =
                accessBadgeText
                    ?: if (item.isChargingArc) {
                        binding.root.context.getString(R.string.badge_charging)
                    } else {
                        ""
                    }
            ImageLoader.loadInto(binding.ivCover, ImageUrl.cover(item.coverUrl))

            applyOverlayTranslations()

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(item, pos)
            }

            binding.root.setOnLongClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnLongClickListener false
                onLongClick?.invoke(item, pos) ?: false
            }
        }

        private fun applyFixedSizing() {
            if (fixedItemWidthDimenRes != null) {
                val w =
                    binding.root.resources
                        .getDimensionPixelSize(fixedItemWidthDimenRes)
                        .coerceAtLeast(1)
                val lp = binding.root.layoutParams
                if (lp != null && lp.width != w) {
                    lp.width = w
                    binding.root.layoutParams = lp
                }
            }

            if (fixedItemMarginDimenRes != null) {
                val margin = binding.root.resources.getDimensionPixelSize(fixedItemMarginDimenRes).coerceAtLeast(0)
                (binding.root.layoutParams as? MarginLayoutParams)?.let { lp ->
                    if (lp.leftMargin != margin || lp.topMargin != margin || lp.rightMargin != margin || lp.bottomMargin != margin) {
                        lp.setMargins(margin, margin, margin, margin)
                        binding.root.layoutParams = lp
                    }
                }
            }
        }

        private fun applyOverlayTranslations() {
            val res = binding.root.resources
            val textMargin = res.getDimensionPixelSize(R.dimen.video_card_text_margin)
            val padH = res.getDimensionPixelSize(R.dimen.video_card_duration_padding_h)
            val padV = res.getDimensionPixelSize(R.dimen.video_card_duration_padding_v)

            // "Closer to the left edge by 50%": the visible inset is (margin + inner padding),
            // so shift left by 50% of that total inset (more noticeable than margin-only).
            val insetX = textMargin + padH
            val shiftX = -insetX * 0.5f
            binding.llStats.translationX = shiftX
            binding.tvDuration.translationX = shiftX

            // "Move down 20%": apply 20% of overlay height (and a tiny baseline from margin)
            // so the change is visible across different UI scales.
            fun applyShiftY() {
                val overlayH = maxOf(binding.llStats.height, binding.tvDuration.height)
                val shiftY = (textMargin + padV) * 0.2f + overlayH * 0.2f
                binding.llStats.translationY = shiftY
                binding.tvDuration.translationY = shiftY
            }

            if (binding.llStats.height > 0 || binding.tvDuration.height > 0) {
                applyShiftY()
            } else {
                binding.root.post { applyShiftY() }
            }
        }
    }
}
