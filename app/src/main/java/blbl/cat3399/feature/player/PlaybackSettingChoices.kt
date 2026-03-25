package blbl.cat3399.feature.player

import blbl.cat3399.feature.player.danmaku.DanmakuFontWeight
import blbl.cat3399.feature.player.danmaku.DanmakuLaneDensity
import kotlin.math.abs

internal object PlaybackSettingChoices {
    val resolutionQns: List<Int> = listOf(16, 32, 64, 74, 80, 100, 112, 116, 120, 125, 126, 127, 129)
    val audioTrackIds: List<Int> = listOf(30251, 30250, 30280, 30232, 30216)
    val playbackSpeeds: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val extendedPlaybackSpeeds: List<Float> = playbackSpeeds + listOf(3.0f, 4.0f)
    val subtitleTextSizes: List<Int> = (10..60 step 2).toList()
    val subtitleBottomPaddingPercents: List<Int> = (0..30 step 2).toList()

    val subtitleBackgroundOpacities: List<Float> by lazy {
        val options = (20 downTo 0).map { it / 20f }.toMutableList()
        val defaultOpacity = 34f / 255f
        if (options.none { abs(it - defaultOpacity) < 0.005f }) options.add(defaultOpacity)
        options.distinct().sortedDescending()
    }

    val danmakuOpacities: List<Float> = (20 downTo 1).map { it / 20f }
    val danmakuTextSizes: List<Int> = subtitleTextSizes
    val danmakuAreas: List<Pair<Float, String>> =
        listOf(
            (1f / 6f) to "1/6",
            (1f / 5f) to "1/5",
            0.25f to "1/4",
            (1f / 3f) to "1/3",
            (2f / 5f) to "2/5",
            0.50f to "1/2",
            (3f / 5f) to "3/5",
            (2f / 3f) to "2/3",
            0.75f to "3/4",
            (4f / 5f) to "4/5",
            1.00f to "不限",
        )
    val danmakuStrokeWidths: List<Int> = listOf(0, 2, 4, 6)
    val danmakuFontWeights: List<DanmakuFontWeight> = listOf(DanmakuFontWeight.Normal, DanmakuFontWeight.Bold)
    val danmakuLaneDensities: List<DanmakuLaneDensity> =
        listOf(DanmakuLaneDensity.Sparse, DanmakuLaneDensity.Standard, DanmakuLaneDensity.Dense)
    val danmakuSpeeds: List<Int> = (1..10).toList()
    val aiShieldLevels: List<Int> = (1..10).toList()
}
