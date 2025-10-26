package com.android.imageprocesser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReusableComposeNode

@Composable
public fun Block(imageProcessor: ImageProcessor) {
    ReusableComposeNode<MyNode, ImageApplier>(
        factory = ::MyNode,
        update = {
            set(imageProcessor) { setProcessor(imageProcessor) }
        }
    )
}

@Composable
public fun Block(
    content: @Composable () -> Unit,
    imageProcessor: ImageProcessor,
) {
    ReusableComposeNode<MyNode, ImageApplier>(
        factory = ::MyNode,
        update = {
            set(imageProcessor) { setProcessor(imageProcessor) }
        },
        content = content,
    )
}