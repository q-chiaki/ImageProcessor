//package com.android.imageprocesser
//
//import androidx.compose.runtime.AbstractApplier
//import androidx.compose.runtime.Composable
//import androidx.compose.runtime.ComposeNode
//import androidx.compose.runtime.Composition
//import androidx.compose.runtime.CompositionContext
//import androidx.compose.runtime.getValue
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import androidx.compose.runtime.setValue
//
//// Provided we have a tree with a node base type like the following
//public abstract class Node {
//    val children = mutableListOf<Node>()
//}
//
//// We would implement an Applier class like the following, which would teach compose how to
//// manage a tree of Nodes.
//public class NodeApplier(root: Node) : AbstractApplier<Node>(root) {
//    override fun insertTopDown(index: Int, instance: Node) {
//        current.children.add(index, instance)
//    }
//
//    override fun insertBottomUp(index: Int, instance: Node) {
//        // Ignored as the tree is built top-down.
//    }
//
//    override fun remove(index: Int, count: Int) {
//        current.children.remove(index, count)
//    }
//
//    override fun move(from: Int, to: Int, count: Int) {
//        current.children.move(from, to, count)
//    }
//
//    override fun onClear() {
//        root.children.clear()
//    }
//}
//
//// A function like the following could be created to create a composition provided a root Node.
//public fun Node.setContent(parent: CompositionContext, content: @Composable () -> Unit): Composition {
//    return Composition(NodeApplier(this), parent).apply { setContent(content) }
//}
//
//// assuming we have Node sub-classes like "TextNode" and "GroupNode"
//public class TextNode : Node() {
//    public var text: String = ""
//    public var onClick: () -> Unit = {}
//}
//public class GroupNode : Node()
//
//// Composable equivalents could be created
//@Composable
//public fun Text(text: String, onClick: () -> Unit = {}) {
//    ComposeNode<TextNode, NodeApplier>(::TextNode) {
//        set(text) { this.text = it }
//        set(onClick) { this.onClick = it }
//    }
//}
//
//@Composable
//public fun Group(content: @Composable () -> Unit) {
//    ComposeNode<GroupNode, NodeApplier>(::GroupNode, {}, content)
//}
//
//// and then a sample tree could be composed:
//public fun runApp(root: GroupNode, parent: CompositionContext) {
//    root.setContent(parent) {
//        var count by remember { mutableStateOf(0) }
//        Group {
//            Text("Count: $count")
//            Text("Increment") { count++ }
//        }
//    }
//}