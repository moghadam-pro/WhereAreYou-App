package com.whereareyou.core.rules

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallLogDeduplicatorTest {

    @Test
    fun `first sighting of an id is new`() {
        val dedup = CallLogDeduplicator()
        assertTrue(dedup.recordIfNew("call-1"))
    }

    @Test
    fun `repeated id is not new`() {
        val dedup = CallLogDeduplicator()
        dedup.recordIfNew("call-1")
        assertFalse(dedup.recordIfNew("call-1"))
    }

    @Test
    fun `capacity eviction still allows new ids to be recorded`() {
        val dedup = CallLogDeduplicator(capacity = 2)
        assertTrue(dedup.recordIfNew("call-1"))
        assertTrue(dedup.recordIfNew("call-2"))
        assertTrue(dedup.recordIfNew("call-3"))
    }
}
