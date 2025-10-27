package com.android.imageprocesser

import androidx.compose.runtime.*
import kotlinx.coroutines.*

internal data class ImageContext(
    val width: Int,
    val height: Int
)

internal val LocalImageContext = compositionLocalOf<ImageContext?> { null }

@Composable
public fun ImageProcessingContent(
    inputData: ByteArray?,
    width: Int,
    height: Int,
    content: @Composable () -> Unit
): ByteArray? {
    var outputData by remember { mutableStateOf<ByteArray?>(null) }
    val imageProcessing = remember { ImageProcessing() }
    val imageContext = remember(width, height) { ImageContext(width, height) }

    imageProcessing.setContent {
        CompositionLocalProvider(LocalImageContext provides imageContext) {
            content()
        }
    }

    // ★ 再構成が完了したら処理を実行
    SideEffect {
        if (inputData != null) {
            println("======== SideEffect: Processing image ========")
            outputData = imageProcessing.rootNode.process(inputData)
        } else {
            outputData = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            imageProcessing.dispose()
        }
    }

    return outputData
}

private class ImageProcessing {
    val clock = BroadcastFrameClock()
    val coroutineScope = CoroutineScope(clock + Dispatchers.Main)
    val rootNode = ProcessorNode()
    private val applier = ImageApplier(rootNode)
    private val recomposer = Recomposer(clock)
    private val composition = Composition(applier, recomposer)

    init {
        coroutineScope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
        coroutineScope.launch {
            while (true) {
                clock.sendFrame(System.nanoTime())
                delay(1)
            }
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        composition.setContent(content)
    }

    fun dispose() {
        composition.dispose()
        recomposer.close()
        coroutineScope.cancel()
    }
}