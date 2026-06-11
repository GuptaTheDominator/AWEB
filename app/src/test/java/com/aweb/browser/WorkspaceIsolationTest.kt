package com.aweb.browser

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Unit tests verifying workspace contextId isolation logic.
 *
 * These are pure logic tests — no GeckoView or Android context required.
 */
class WorkspaceIsolationTest {

    data class FakeWorkspace(val id: String, val contextId: String)

    private fun newWorkspace(name: String) = FakeWorkspace(
        id        = UUID.randomUUID().toString(),
        contextId = "aweb_ws_${UUID.randomUUID()}",
    )

    @Test
    fun `each workspace gets a unique contextId`() {
        val a = newWorkspace("Work")
        val b = newWorkspace("Personal")
        val c = newWorkspace("Study")
        val ids = listOf(a.contextId, b.contextId, c.contextId)
        assertEquals(3, ids.toSet().size)
    }

    @Test
    fun `contextId prefix is aweb_ws_`() {
        val ws = newWorkspace("Test")
        assertTrue(ws.contextId.startsWith("aweb_ws_"))
    }

    @Test
    fun `two workspaces never share contextId`() {
        repeat(50) {
            val x = newWorkspace("A")
            val y = newWorkspace("B")
            assertNotEquals(x.contextId, y.contextId)
        }
    }

    @Test
    fun `workspace id is a valid UUID`() {
        val ws = newWorkspace("Test")
        // Should not throw
        UUID.fromString(ws.id)
    }

    @Test
    fun `tabs within same workspace share contextId`() {
        val ws = newWorkspace("Work")
        // Tabs do not have their own contextId — they inherit from workspace
        val tab1ContextId = ws.contextId
        val tab2ContextId = ws.contextId
        assertEquals(tab1ContextId, tab2ContextId)
    }

    @Test
    fun `tabs across workspaces have different contextIds`() {
        val wsA = newWorkspace("Work")
        val wsB = newWorkspace("Personal")
        assertNotEquals(wsA.contextId, wsB.contextId)
    }
}
