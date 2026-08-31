package com.infiltrate.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.DrawableResource

@Composable
expect fun LoopingVideoBackground(
    modifier: Modifier = Modifier,
    videoName: String = "bg1080p",
    videoExtension: String = "mp4",
    fallbackDrawable: DrawableResource
)
