package com.infiltrate.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
actual fun LoopingVideoBackground(
    modifier: Modifier,
    videoName: String,
    videoExtension: String,
    fallbackDrawable: DrawableResource
) {
    // JVM fallback (used for local testing and JVM desktop dev)
    Image(
        painter = painterResource(fallbackDrawable),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.CenterEnd,
        modifier = modifier
    )
}
