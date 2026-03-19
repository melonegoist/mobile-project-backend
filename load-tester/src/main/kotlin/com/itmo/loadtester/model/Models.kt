package com.itmo.loadtester.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ─── Auth ────────────────────────────────────────────────────────────────────

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val refreshToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Int = 3600
)

// ─── Trade ───────────────────────────────────────────────────────────────────

@Serializable
data class TradeRequest(
    val symbol: String,
    val quantity: Int,
    val tradeType: String,     // "buy" | "sell"
    val orderType: String = "market"
)

// ─── Load test control API ───────────────────────────────────────────────────

data class LoadTestStartRequest(
    val totalUsers: Int? = null,
    val rampUpSeconds: Int? = null,
    val minActionDelayMs: Long? = null,
    val maxActionDelayMs: Long? = null
)

data class LoadTestStatus(
    val running: Boolean,
    val totalUsers: Int,
    val activeUsers: Int,
    val rampUpSeconds: Int,
    val elapsedSeconds: Long,
    val metrics: MetricsSnapshot
)

data class MetricsSnapshot(
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val requestsPerSecond: Double,
    val avgLatencyMs: Double,
    val p95LatencyMs: Long,
    val p99LatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val errorRate: Double
)

// ─── Stock symbols for load test ─────────────────────────────────────────────

val STOCK_SYMBOLS = listOf(
    "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA",
    "NVDA", "META", "NFLX", "AMD", "INTC",
    "JPM", "GS", "BAC", "WFC", "V",
    "MA", "PYPL", "DIS", "BABA", "UBER"
)
