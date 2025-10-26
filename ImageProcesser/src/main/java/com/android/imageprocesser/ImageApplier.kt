package com.android.imageprocesser

import androidx.compose.runtime.AbstractApplier

internal class ImageApplier(root: MyNode) : AbstractApplier<MyNode>(root) {
    override fun insertBottomUp(index: Int, instance: MyNode) {
        current.children.add(index, instance)
    }

    override fun insertTopDown(index: Int, instance: MyNode) {}

    override fun move(from: Int, to: Int, count: Int) {
        current.children.move(from, to, count)
    }

    override fun onClear() {}

    override fun remove(index: Int, count: Int) {
        current.children.remove(index, count)
    }
}