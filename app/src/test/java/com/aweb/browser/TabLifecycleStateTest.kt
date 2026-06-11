package com.aweb.browser

import com.aweb.browser.lifecycle.TabLifecycleState
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [TabLifecycleState] DB key round-trip.
 */
class TabLifecycleStateTest {

    @Test
    fun `all states have unique DB keys`() {
        val keys = TabLifecycleState.entries.map { it.dbKey }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `fromDb returns correct state for each key`() {
        TabLifecycleState.entries.forEach { state ->
            assertEquals(state, TabLifecycleState.fromDb(state.dbKey))
        }
    }

    @Test
    fun `fromDb returns UNLOADED for unknown key`() {
        assertEquals(TabLifecycleState.UNLOADED, TabLifecycleState.fromDb("garbage"))
    }

    @Test
    fun `ACTIVE dbKey is 'active'`() {
        assertEquals("active", TabLifecycleState.ACTIVE.dbKey)
    }

    @Test
    fun `KEEP_ALIVE dbKey is 'keep_alive'`() {
        assertEquals("keep_alive", TabLifecycleState.KEEP_ALIVE.dbKey)
    }
}
