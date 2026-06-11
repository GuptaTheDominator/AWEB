package com.aweb.browser

import com.aweb.browser.lifecycle.MemoryPolicy
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [MemoryPolicy] preset correctness.
 * Run with: ./gradlew test
 */
class MemoryPolicyTest {

    @Test
    fun `CONSERVATIVE preset has zero recent live tabs`() {
        val policy = MemoryPolicy.CONSERVATIVE
        assertEquals(0, policy.maxRecentLive)
    }

    @Test
    fun `CONSERVATIVE preset caps keep alive at 2`() {
        val policy = MemoryPolicy.CONSERVATIVE
        assertEquals(2, policy.maxKeepAlive)
    }

    @Test
    fun `BALANCED preset has 2 recent live tabs`() {
        val policy = MemoryPolicy.BALANCED
        assertEquals(2, policy.maxRecentLive)
        assertEquals(3, policy.maxKeepAlive)
    }

    @Test
    fun `PERFORMANCE preset has 5 recent live tabs`() {
        val policy = MemoryPolicy.PERFORMANCE
        assertEquals(5, policy.maxRecentLive)
        assertEquals(5, policy.maxKeepAlive)
    }

    @Test
    fun `fromKey returns BALANCED for unknown key`() {
        val policy = MemoryPolicy.fromKey("invalid_key")
        assertEquals(MemoryPolicy.BALANCED.maxRecentLive, policy.maxRecentLive)
    }

    @Test
    fun `fromKey applies custom maxKeepAlive override`() {
        val policy = MemoryPolicy.fromKey("conservative", maxKeepAlive = 5)
        assertEquals(0, policy.maxRecentLive)
        assertEquals(5, policy.maxKeepAlive)
    }

    @Test
    fun `PERFORMANCE has more live slots than BALANCED`() {
        assertTrue(MemoryPolicy.PERFORMANCE.maxRecentLive > MemoryPolicy.BALANCED.maxRecentLive)
    }

    @Test
    fun `BALANCED has more live slots than CONSERVATIVE`() {
        assertTrue(MemoryPolicy.BALANCED.maxRecentLive > MemoryPolicy.CONSERVATIVE.maxRecentLive)
    }
}
