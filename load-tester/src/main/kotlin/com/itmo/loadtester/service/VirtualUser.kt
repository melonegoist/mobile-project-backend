package com.itmo.loadtester.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.itmo.loadtester.model.STOCK_SYMBOLS
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private val JSON_MT: MediaType = "application/json; charset=utf-8".toMediaType()
private val logger = LoggerFactory.getLogger(VirtualUser::class.java)

class VirtualUser(
    private val userId: Int,
    private val targetUrl: String,
    private val minDelayMs: Long,
    private val maxDelayMs: Long,
    private val initialDeposit: Double,
    private val metricsCollector: MetricsCollector,
    private val running: AtomicBoolean,
    private val httpClient: OkHttpClient,
    private val objectMapper: ObjectMapper,
    private val wsEnabled: Boolean
) : Runnable {

    private val username = "loaduser_$userId"
    private val password = "LoadPass\$${userId}!"
    private val email = "loaduser$userId@loadtest.internal"
    private var token: String? = null
    private var wsClient: WebSocket? = null

    override fun run() {
        try {
            setup()
            while (running.get() && !Thread.currentThread().isInterrupted) {
                doRandomAction()
                Thread.sleep(Random.nextLong(minDelayMs, maxDelayMs))
            }
        } catch (_: InterruptedException) {
            // graceful shutdown
        } catch (e: Exception) {
            logger.debug("VirtualUser $userId terminated: ${e.message}")
        } finally {
            wsClient?.close(1000, "Load test stopped")
        }
    }

    private fun setup() {
        // Try login first, then register if needed
        if (!login()) {
            register()
            login()
        }

        if (token == null) {
            logger.warn("User $userId: could not authenticate, skipping")
            return
        }

        // Make an initial deposit so the user can trade
        deposit(initialDeposit)

        if (wsEnabled) connectWebSocket()
    }

    private fun register(): Boolean {
        val body = mapOf(
            "username" to username,
            "email" to email,
            "password" to password,
            "firstName" to "Load",
            "lastName" to "User$userId"
        )
        return execute("POST", "/auth/register", body, authenticated = false) != null
    }

    private fun login(): Boolean {
        val body = mapOf("username" to username, "password" to password)
        val response = execute("POST", "/auth/login", body, authenticated = false) ?: return false
        return try {
            val map = objectMapper.readValue<Map<String, Any>>(response)
            token = map["token"] as? String
            token != null
        } catch (_: Exception) {
            false
        }
    }

    private fun deposit(amount: Double) {
        execute("POST", "/account/deposit", mapOf("amount" to amount, "paymentMethod" to "card"))
    }

    private fun connectWebSocket() {
        val wsUrl = targetUrl.replace("http://", "ws://").replace("https://", "wss://")
        val request = Request.Builder()
            .url("$wsUrl/ws?token=${token ?: return}")
            .build()

        wsClient = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                logger.debug("User $userId WS failure: ${t.message}")
            }
        })
    }

    private fun doRandomAction() {
        if (token == null) return
        val roll = Random.nextInt(100)
        when {
            roll < 20 -> execute("GET", "/market/stocks?limit=20&offset=0")
            roll < 35 -> execute("GET", "/market/stocks/${randomSymbol()}")
            roll < 50 -> execute("GET", "/portfolio")
            roll < 60 -> executeTrade("buy")
            roll < 68 -> executeTrade("sell")
            roll < 76 -> execute("GET", "/account")
            roll < 84 -> execute("GET", "/trades/history?limit=10")
            roll < 90 -> execute("GET", "/market/summary")
            roll < 95 -> execute("GET", "/watchlist")
            else      -> execute("GET", "/profile/stats")
        }
    }

    private fun executeTrade(type: String) {
        val body = mapOf(
            "symbol" to randomSymbol(),
            "quantity" to Random.nextInt(1, 6),
            "tradeType" to type,
            "orderType" to "market"
        )
        execute("POST", "/trades", body)
    }

    private fun execute(
        method: String,
        path: String,
        body: Any? = null,
        authenticated: Boolean = true
    ): String? {
        val t0 = System.currentTimeMillis()
        val url = "$targetUrl$path"
        return try {
            val reqBody = body?.let {
                objectMapper.writeValueAsString(it).toRequestBody(JSON_MT)
            }

            val req = Request.Builder().url(url).apply {
                if (authenticated) token?.let { addHeader("Authorization", "Bearer $it") }
                when (method) {
                    "POST"   -> post(reqBody ?: "".toRequestBody(JSON_MT))
                    "PUT"    -> put(reqBody ?: "".toRequestBody(JSON_MT))
                    "DELETE" -> delete()
                    else     -> get()
                }
            }.build()

            httpClient.newCall(req).execute().use { resp ->
                val latency = System.currentTimeMillis() - t0
                val responseBody = resp.body?.string()
                if (resp.isSuccessful) {
                    metricsCollector.recordSuccess(latency)
                } else {
                    metricsCollector.recordFailure(latency)
                }
                responseBody
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - t0
            metricsCollector.recordFailure(latency)
            null
        }
    }

    private fun randomSymbol() = STOCK_SYMBOLS.random()
}
