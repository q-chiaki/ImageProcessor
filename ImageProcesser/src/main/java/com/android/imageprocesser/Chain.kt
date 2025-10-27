package com.android.imageprocesser

internal class PipelineProcessor : ImageProcessor {
    override fun process(imageData: ByteArray, children: List<ImageProcessorNode>): ByteArray {
        var signal = imageData
        for (child in children) {
            signal = child.process(signal)
        }
        return signal
    }
}