package com.notedroidplusplus.app.core

/**
 * Pure helpers for Notepad++ tab-bar semantics: pin grouping and the
 * extended close-menu filters. Indexes refer to positions in the tab list.
 */
object TabBar {

    /** Moves the newly toggled [index] so pinned tabs form a leading group. */
    fun pinnedFirstOrder(count: Int, pinned: Set<Int>): List<Int> {
        val pinnedSorted = pinned.sorted()
        val rest = (0 until count).filter { it !in pinned }
        return pinnedSorted + rest
    }

    /** Close All BUT Pinned: every non-pinned index, reversed for safe removal. */
    fun closeAllButPinned(count: Int, pinned: Set<Int>): List<Int> =
        (0 until count).filter { it !in pinned }.reversed()

    /** Close All BUT This: every index except [keep], reversed for safe removal. */
    fun closeAllButThis(count: Int, keep: Int): List<Int> =
        (0 until count).filter { it != keep }.reversed()

    /** Close All to the Left of [anchor] (exclusive), reversed. */
    fun closeAllToLeft(anchor: Int): List<Int> = (0 until anchor).toList().reversed()

    /** Close All to the Right of [anchor] (exclusive), reversed. */
    fun closeAllToRight(count: Int, anchor: Int): List<Int> =
        ((anchor + 1) until count).toList().reversed()

    /** Elides a tab label to [maxChars] with an ellipsis; <=0 disables eliding. */
    private const val ELLIPSIS = "\u2026"

    fun elideLabel(label: String, maxChars: Int): String =
        if (maxChars in 1 until label.length) label.take(maxChars - 1) + ELLIPSIS else label
}
