package com.itmo.loadtester.service

import com.itmo.loadtester.model.MetricsSnapshot
import org.springframework.stereotype.Component
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import kotlin.math.ceil

@Component
class MetricsCollector {
    private val totalRequests = LongAdder()
    private val successfulRequests = LongAdder()
    private val failedRequests = LongAdder()
    private val totalLatencyMs = LongAdder()
    private val minLatencyMs = AtomicLong(Long.MAX_VALUE)
    private val maxLatencyMs = AtomicLong(0L)

    // Sliding window for percentile calculation (last 10k samples)
    private val latencySamples = PriorityBlockingQueue<Long>()
    private val startTime = AtomicLong(0L)

    fun reset() {
        totalRequests.reset()
        successfulRequests.reset()
        failedRequests.reset()
        totalLatencyMs.reset()
        minLatencyMs.set(Long.MAX_VALUE)
        maxLatencyMs.set(0L)
        latencySamples.clear()
        startTime.set(System.currentTimeMillis())
    }

    fun recordSuccess(latencyMs: Long) {
        totalRequests.increment()
        successfulRequests.increment()
        recordLatency(latencyMs)
    }

    fun recordFailure(latencyMs: Long) {
        totalRequests.increment()
        failedRequests.increment()
        recordLatency(latencyMs)
    }

    private fun recordLatency(latencyMs: Long) {
        totalLatencyMs.add(latencyMs)
        minLatencyMs.updateAndGet { minOf(it, latencyMs) }
        maxLatencyMs.updateAndGet { maxOf(it, latencyMs) }

        latencySamples.offer(latencyMs)
        // Trim to 100_000 samples to avoid unbounded memory growth
        while (latencySamples.size > 100_000) latencySamples.poll()
    }

    fun snapshot(): MetricsSnapshot {
        val total = totalRequests.sum()
        val success = successfulRequests.sum()
        val failed = failedRequests.sum()
        val avgLatency = if (total > 0) totalLatencyMs.sum().toDouble() / total else 0.0

        val elapsedSec = (System.currentTimeMillis() - startTime.get()).coerceAtLeast(1) / 1000.0
        val rps = total / elapsedSec

        val sorted = latencySamples.toList().sorted()
        val p95 = percentile(sorted, 0.95)
        val p99 = percentile(sorted, 0.99)
        val minLat = if (total > 0) minLatencyMs.get().let { if (it == Long.MAX_VALUE) 0L else it } else 0L
        val maxLat = if (total > 0) maxLatencyMs.get() else 0L
        val errorRate = if (total > 0) failed.toDouble() / total * 100.0 else 0.0

        return MetricsSnapshot(
            totalRequests = total,
            successfulRequests = success,
            failedRequests = failed,
            requestsPerSecond = String.format("%.2f", rps).toDouble(),
            avgLatencyMs = String.format("%.2f", avgLatency).toDouble(),
            p95LatencyMs = p95,
            p99LatencyMs = p99,
            minLatencyMs = minLat,
            maxLatencyMs = maxLat,
            errorRate = String.format("%.2f", errorRate).toDouble()
        )
    }

    private fun percentile(sorted: List<Long>, pct: Double): Long {
        if (sorted.isEmpty()) return 0L
        val idx = ceil(pct * sorted.size).toInt().coerceIn(1, sorted.size) - 1
        return sorted[idx]
    }
}
