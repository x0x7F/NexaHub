package me.ray.nexahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.ray.nexahub.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
