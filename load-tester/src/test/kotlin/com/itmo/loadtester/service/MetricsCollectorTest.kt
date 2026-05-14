package com.itmo.loadtester.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MetricsCollectorTest {

    private lateinit var collector: MetricsCollector

    @BeforeEach
    fun setUp() {
        collector = MetricsCollector()
        collector.reset()
    }

    @Test
    fun `recordSuccess increments totals and tracks latency`() {
        collector.recordSuccess(100)
        collector.recordSuccess(200)
        collector.recordSuccess(50)

        val snapshot = collector.snapshot()
        assertEquals(3L, snapshot.totalRequests)
        assertEquals(3L, snapshot.successfulRequests)
        assertEquals(0L, snapshot.failedRequests)
        assertEquals(50L, snapshot.minLatencyMs)
        assertEquals(200L, snapshot.maxLatencyMs)
        assertEquals(0.0, snapshot.errorRate)
    }

    @Test
    fun `recordFailure raises error rate`() {
        repeat(3) { collector.recordSuccess(10) }
        collector.recordFailure(50)

        val snapshot = collector.snapshot()
        assertEquals(4L, snapshot.totalRequests)
        assertEquals(1L, snapshot.failedRequests)
        assertEquals(25.0, snapshot.errorRate)
    }

    @Test
    fun `percentiles approximate the right buckets`() {
        for (latency in 1L..100L) {
            collector.recordSuccess(latency)
        }
        val snapshot = collector.snapshot()
        // For 100 samples sorted, ceil(0.95*100)=95 → idx 94 → value 95
        assertEquals(95L, snapshot.p95LatencyMs)
        assertEquals(99L, snapshot.p99LatencyMs)
        assertEquals(1L, snapshot.minLatencyMs)
        assertEquals(100L, snapshot.maxLatencyMs)
    }

    @Test
    fun `snapshot on empty collector is safe`() {
        val snapshot = collector.snapshot()
        assertEquals(0L, snapshot.totalRequests)
        assertEquals(0L, snapshot.minLatencyMs)
        assertEquals(0L, snapshot.maxLatencyMs)
        assertEquals(0.0, snapshot.errorRate)
        assertEquals(0L, snapshot.p95LatencyMs)
        assertEquals(0L, snapshot.p99LatencyMs)
    }

    @Test
    fun `reset clears all counters and timestamps`() {
        repeat(5) { collector.recordSuccess(10) }
        collector.reset()

        val snapshot = collector.snapshot()
        assertEquals(0L, snapshot.totalRequests)
        assertEquals(0.0, snapshot.avgLatencyMs)
    }

    @Test
    fun `avgLatency is mean of recorded latencies`() {
        collector.recordSuccess(100)
        collector.recordSuccess(200)
        collector.recordSuccess(300)
        val snapshot = collector.snapshot()
        assertEquals(200.0, snapshot.avgLatencyMs)
    }

    @Test
    fun `requestsPerSecond is non-negative`() {
        collector.recordSuccess(10)
        assertTrue(collector.snapshot().requestsPerSecond >= 0.0)
    }
}
