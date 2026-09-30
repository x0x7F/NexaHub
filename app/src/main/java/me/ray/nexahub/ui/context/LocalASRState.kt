package me.ray.nexahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import me.ray.nexahub.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

