package com.android.imageprocesser

public interface ImageProcessor {
    public fun process(imageData: ByteArray, children: List<ImageProcessorNode>): ByteArray
}