package com.android.imageprocesser

import androidx.compose.runtime.Composable

@Composable
public fun Mirror() {
    val context = LocalImageContext.current ?: error("ImageContext not provided")

    ImageProcesserBlock(
        content = {},
        imageProcessor = MirrorProcessor(
            width = { context.width },
            height = { context.height }
        )
    )
}

internal class MirrorProcessor(
    private val width: () -> Int,
    private val height: () -> Int
) : ImageProcessor {
    override fun process(
        imageData: ByteArray,
        children: List<ImageProcessorNode>
    ): ByteArray {
        val w = width()
        val h = height()
        val bytesPerPixel = 4

        require(imageData.size == w * h * bytesPerPixel) {
            "Input size mismatch: expected ${w * h * bytesPerPixel} bytes, got ${imageData.size}"
        }

        val outputData = ByteArray(imageData.size)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val originalIndex = (y * w + x) * bytesPerPixel
                val mirroredX = w - 1 - x
                val mirroredIndex = (y * w + mirroredX) * bytesPerPixel

                // ARGBをコピー
                for (i in 0 until bytesPerPixel) {
                    outputData[mirroredIndex + i] = imageData[originalIndex + i]
                }
            }
        }

        return outputData
    }
}