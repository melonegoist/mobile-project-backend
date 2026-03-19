package com.itmo.loadtester.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.itmo.loadtester.config.LoadTestConfig
import com.itmo.loadtester.model.LoadTestStatus
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Service
class LoadTestService(
    private val config: LoadTestConfig,
    private val metricsCollector: MetricsCollector,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(LoadTestService::class.java)

    private val running = AtomicBoolean(false)
    private val activeUsers = AtomicInteger(0)
    private val startTime = AtomicLong(0L)
    private var executor = Executors.newVirtualThreadPerTaskExecutor()
    private val futures = mutableListOf<Future<*>>()
    private val futuresLock = Any()

    // Shared OkHttpClient – connection pooling, reused by all virtual users
    private val sharedHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5000, 30, TimeUnit.SECONDS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun start(
        totalUsers: Int = 0,
        rampUpSeconds: Int = 0,
        minDelayMs: Long = 0L,
        maxDelayMs: Long = 0L
    ) {
        // Fall back to config values when caller passes 0 (default / not set)
        val effectiveUsers = if (totalUsers > 0) totalUsers else config.totalUsers
        val effectiveRampUp = if (rampUpSeconds > 0) rampUpSeconds else config.rampUpSeconds
        val effectiveMinDelay = if (minDelayMs > 0) minDelayMs else config.minActionDelayMs
        val effectiveMaxDelay = if (maxDelayMs > 0) maxDelayMs else config.maxActionDelayMs
        startInternal(effectiveUsers, effectiveRampUp, effectiveMinDelay, effectiveMaxDelay)
    }

    private fun startInternal(
        totalUsers: Int,
        rampUpSeconds: Int,
        minDelayMs: Long,
        maxDelayMs: Long
    ) {
        if (running.getAndSet(true)) {
            logger.warn("Load test already running")
            return
        }

        metricsCollector.reset()
        startTime.set(System.currentTimeMillis())
        executor = Executors.newVirtualThreadPerTaskExecutor()
        synchronized(futuresLock) { futures.clear() }
        activeUsers.set(0)

        logger.info("Starting load test: users=$totalUsers rampUp=${rampUpSeconds}s target=${config.targetUrl}")

        // Ramp-up orchestrator runs on a virtual thread
        executor.submit {
            try {
                val delayBetweenUsersMs = max(1L, (rampUpSeconds * 1000L) / totalUsers)

                for (i in 1..totalUsers) {
                    if (!running.get()) break

                    val user = VirtualUser(
                        userId = i,
                        targetUrl = config.targetUrl,
                        minDelayMs = minDelayMs,
                        maxDelayMs = maxDelayMs,
                        initialDeposit = config.initialDeposit,
                        metricsCollector = metricsCollector,
                        running = running,
                        httpClient = sharedHttpClient,
                        objectMapper = objectMapper,
                        wsEnabled = config.websocketEnabled
                    )

                    val future = executor.submit(user)
                    synchronized(futuresLock) { futures.add(future) }
                    activeUsers.incrementAndGet()

                    if (delayBetweenUsersMs > 0) Thread.sleep(delayBetweenUsersMs)
                }

                logger.info("All $totalUsers virtual users started")
            } catch (_: InterruptedException) {
                logger.info("Ramp-up interrupted")
            }
        }
    }

    fun stop() {
        if (!running.getAndSet(false)) {
            logger.warn("Load test not running")
            return
        }

        logger.info("Stopping load test...")
        synchronized(futuresLock) {
            futures.forEach { it.cancel(true) }
        }
        executor.shutdownNow()
        executor.awaitTermination(10, TimeUnit.SECONDS)
        activeUsers.set(0)
        logger.info("Load test stopped. Final metrics: ${metricsCollector.snapshot()}")
    }

    fun status(): LoadTestStatus {
        val elapsed = if (startTime.get() > 0)
            (System.currentTimeMillis() - startTime.get()) / 1000
        else 0L

        return LoadTestStatus(
            running = running.get(),
            totalUsers = config.totalUsers,
            activeUsers = activeUsers.get(),
            rampUpSeconds = config.rampUpSeconds,
            elapsedSeconds = elapsed,
            metrics = metricsCollector.snapshot()
        )
    }
}
