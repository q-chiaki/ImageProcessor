package com.android.imageprocesser

public interface ImageProcessorNode {
    public fun process(imageData: ByteArray): ByteArray
}

internal class ProcessorNode : ImageProcessorNode {
    val children: ArrayList<ProcessorNode> = ArrayList()
    private var processor: ImageProcessor = PipelineProcessor()

    override fun process(imageData: ByteArray): ByteArray {
        return processor.process(imageData, children)
    }

    fun getNextSamples(numSamples: Int): ByteArray {
        val emptyData = ByteArray(numSamples)
        val output = process(emptyData)
        return output
    }
    fun setProcessor(processor: ImageProcessor) {
        this.processor = processor
    }
}
