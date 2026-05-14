package com.itmo.loadtester.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadTestConfigTest {

    @Test
    fun `defaults match production sane values`() {
        val config = LoadTestConfig()
        assertEquals("http://localhost:9090", config.targetUrl)
        assertEquals(10_000, config.totalUsers)
        assertEquals(300, config.rampUpSeconds)
        assertEquals(1_000L, config.minActionDelayMs)
        assertEquals(5_000L, config.maxActionDelayMs)
        assertEquals(100_000.0, config.initialDeposit)
        assertFalse(config.autoStart)
        assertTrue(config.websocketEnabled)
    }
}
