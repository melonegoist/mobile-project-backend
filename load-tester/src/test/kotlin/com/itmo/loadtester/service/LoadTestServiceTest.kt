package com.itmo.loadtester.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.itmo.loadtester.config.LoadTestConfig
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoadTestServiceTest {

    private val mapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private fun newService(): Pair<LoadTestService, MetricsCollector> {
        val config = LoadTestConfig(
            targetUrl = "http://127.0.0.1:1",   // unreachable so virtual users immediately error
            totalUsers = 2,
            rampUpSeconds = 1,
            minActionDelayMs = 10,
            maxActionDelayMs = 50,
            initialDeposit = 1_000.0,
            autoStart = false,
            websocketEnabled = false
        )
        val collector = spyk(MetricsCollector())
        val service = LoadTestService(config, collector, mapper)
        return service to collector
    }

    @Test
    fun `start switches running state to true`() {
        val (service, _) = newService()
        try {
            service.start()
            assertTrue(service.status().running)
        } finally {
            service.stop()
        }
    }

    @Test
    fun `stop returns to non-running state`() {
        val (service, _) = newService()
        service.start()
        // Wait briefly so the ramp-up scheduler has a chance to start.
        Thread.sleep(200)
        service.stop()
        assertFalse(service.status().running)
        assertEquals(0, service.status().activeUsers)
    }

    @Test
    fun `metricsCollector reset is called on start`() {
        val (service, collector) = newService()
        try {
            service.start()
            verify(timeout = 1000) { collector.reset() }
        } finally {
            service.stop()
        }
    }

    @Test
    fun `concurrent start is a no-op`() {
        val (service, _) = newService()
        try {
            service.start()
            service.start() // second call must be ignored — status stays running
            assertTrue(service.status().running)
        } finally {
            service.stop()
        }
    }
}
