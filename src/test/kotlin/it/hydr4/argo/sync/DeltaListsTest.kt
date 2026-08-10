package it.hydr4.argo.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class Row(val id: String?, val label: String)

private fun rowId(it: Row) = it.id

private fun isTombstone(it: Row) = it.label == "DELETE"

/** Delta-merge semantics ported from the reference `handleOperation`. */
class DeltaListsTest {
    @Test
    fun `new entries append preserving order`() {
        val merged =
            DeltaLists.apply(
                previous = listOf(Row("a", "old")),
                incoming = listOf(Row("b", "new1"), Row("c", "new2")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun `same identity updates in place keeping position`() {
        val merged =
            DeltaLists.apply(
                previous = listOf(Row("a", "old-a"), Row("b", "old-b")),
                incoming = listOf(Row("b", "fresh-b")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals("fresh-b", merged[1].label)
    }

    @Test
    fun `tombstones delete matching local records`() {
        val merged =
            DeltaLists.apply(
                previous = listOf(Row("a", "old-a"), Row("b", "old-b")),
                incoming = listOf(Row("a", "DELETE")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertEquals(listOf("b"), merged.map { it.id })
    }

    @Test
    fun `insert followed by tombstone of the same id resolves as deleted`() {
        val merged =
            DeltaLists.apply(
                previous = emptyList(),
                incoming = listOf(Row("x", "fresh"), Row("x", "DELETE")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `tombstone without matching local record is a no-op`() {
        val merged =
            DeltaLists.apply(
                previous = listOf(Row("a", "old-a")),
                incoming = listOf(Row("ghost", "DELETE")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun `null-identity entries append and are never deletable`() {
        val merged =
            DeltaLists.apply(
                previous = listOf(Row(null, "anon1")),
                incoming = listOf(Row(null, "anon2"), Row("a", "DELETE")),
                identityOf = ::rowId,
                isTombstone = ::isTombstone,
            )
        assertEquals(2, merged.size)
        assertEquals(listOf("anon1", "anon2"), merged.map { it.label })
    }
}
