package com.android.imageprocesser

import androidx.compose.runtime.Composable

@Composable
public fun Chain(content: @Composable () -> Unit) {
    Block(content = content, imageProcessor = ChainProcessor())
}

internal class ChainProcessor : ImageProcessor {
    override fun process(imageData: ByteArray, children: List<ImageProcessorNode>): ByteArray {
        var signal = imageData
        for (child in children) {
            signal = child.process(signal)
        }
        return signal
    }
}