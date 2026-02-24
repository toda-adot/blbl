package blbl.cat3399.core.theme

import android.app.Activity
import blbl.cat3399.R
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.feature.live.LivePlayerActivity
import blbl.cat3399.feature.player.PlayerActivity

object ThemePresets {
    fun applyTo(activity: Activity) {
        // Player is frozen for this iteration: do not follow preset.
        if (activity is PlayerActivity || activity is LivePlayerActivity) {
            activity.setTheme(R.style.Theme_Blbl_Player_Fixed)
            return
        }

        val preset =
            runCatching { BiliClient.prefs.themePreset }
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: AppPrefs.THEME_PRESET_DEFAULT

        when (preset) {
            AppPrefs.THEME_PRESET_TV_PINK -> {
                activity.setTheme(R.style.Theme_Blbl_Base_PinkLight)
                activity.theme.applyStyle(R.style.ThemeOverlay_Blbl_Accent_TvPink, true)
            }

            else -> {
                activity.setTheme(R.style.Theme_Blbl_Base_Dark)
                activity.theme.applyStyle(R.style.ThemeOverlay_Blbl_Accent_Violet, true)
            }
        }
    }
}

