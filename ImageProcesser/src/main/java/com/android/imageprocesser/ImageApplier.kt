package com.android.imageprocesser

import androidx.compose.runtime.AbstractApplier

internal class ImageApplier(root: ProcessorNode) : AbstractApplier<ProcessorNode>(root) {
    override fun insertBottomUp(index: Int, instance: ProcessorNode) {
        current.children.add(index, instance)
    }

    override fun insertTopDown(index: Int, instance: ProcessorNode) {}

    override fun move(from: Int, to: Int, count: Int) {
        current.children.move(from, to, count)
    }

    override fun onClear() {}

    override fun remove(index: Int, count: Int) {
        current.children.remove(index, count)
    }
}