import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import kotlin.native.ObjCName
import platform.UIKit.UIViewController

// SPIKE / THROWAWAY - Compose<->KorGE view-switching cost spike only (see
// .junie/guidelines.md, "Compose/KorGE view switching spike"). First real Compose UI written
// anywhere in this repo - paywall-build previously only declared the compose.* dependencies
// without any actual Composable, so this is also the first real test that ComposeUIViewController
// links/runs on iOS at all under this project's pinned Kotlin 2.3.20 / Compose 1.12.0 combo.
//
// Deliberately trivial: one centered button. Not the real menu UI - do not extend this file for
// product code, write real screens fresh once the spike's answer is in.
@OptIn(kotlin.experimental.ExperimentalObjCName::class, kotlin.experimental.ExperimentalObjCRefinement::class)
@ObjCName(name = "SpikeComposeScreen", exact = true)
object SpikeComposeScreen {
    fun makeViewController(onStartLevel: () -> Unit): UIViewController =
        ComposeUIViewController {
            SpikeComposeContent(onStartLevel)
        }
}

@Composable
private fun SpikeComposeContent(onStartLevel: () -> Unit) {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = onStartLevel) {
                Text("Start Level")
            }
        }
    }
}
