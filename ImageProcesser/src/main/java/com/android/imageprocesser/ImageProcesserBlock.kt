package com.android.imageprocesser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableComposeNode

@Composable
internal fun ImageProcesserBlock(
    content: @Composable () -> Unit,
    imageProcessor: ImageProcessor,
) {
    ReusableComposeNode<ProcessorNode, ImageApplier>(
        factory = ::ProcessorNode,
        update = {
            set(imageProcessor) { setProcessor(imageProcessor) }
        },
        content = content,
    )
}