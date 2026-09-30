package me.ray.nexahub.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import me.ray.nexahub.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
