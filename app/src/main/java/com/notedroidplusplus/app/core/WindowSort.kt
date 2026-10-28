package com.notedroidplusplus.app.core

/**
 * Sort keys for the Notepad++ Window > Sort By menu. Pure logic; the tab
 * list comparison lives here so ordering is unit-testable.
 */
object WindowSort {

    enum class Field { NAME, TYPE, LENGTH, MODIFIED }

    data class DocInfo(
        val name: String,
        val typeOrdinal: Int,
        val length: Int,
        val modifiedAt: Long
    )

    fun <T> sorted(docs: List<T>, info: (T) -> DocInfo, field: Field, descending: Boolean): List<T> {
        val cmp = Comparator<T> { a, b ->
            val da = info(a)
            val db = info(b)
            compareValues(
                when (field) {
                    Field.NAME -> da.name.lowercase()
                    Field.TYPE -> da.typeOrdinal
                    Field.LENGTH -> da.length
                    Field.MODIFIED -> da.modifiedAt
                },
                when (field) {
                    Field.NAME -> db.name.lowercase()
                    Field.TYPE -> db.typeOrdinal
                    Field.LENGTH -> db.length
                    Field.MODIFIED -> db.modifiedAt
                }
            )
        }
        return if (descending) docs.sortedWith(cmp.reversed()) else docs.sortedWith(cmp)
    }
}
