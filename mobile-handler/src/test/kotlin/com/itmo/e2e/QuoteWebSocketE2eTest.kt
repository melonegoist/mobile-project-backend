package com.itmo.e2e

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.itmo.redis.RedisSubscriber
import com.itmo.routing.configureWebSocketRoute
import com.itmo.websocket.WebSocketManager
import com.redis.testcontainers.RedisContainer
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end check of the quotation distribution path inside mobile-handler:
 *   external publisher  →  Redis  →  RedisSubscriber  →  WebSocketManager  →  WS client
 *
 * The db-handler is not exercised here on purpose — mobile-handler's job is
 * to fan out price updates from Redis to connected WebSocket clients, and
 * that is what we want to prove independently.
 *
 * Important: REDIS_HOST / REDIS_PORT / REDIS_CHANNEL must NOT be set as OS
 * environment variables when running this test (those would override the
 * test-supplied container coordinates).
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuoteWebSocketE2eTest {

    companion object {
        @Container
        @JvmStatic
        val redis: RedisContainer = RedisContainer(
            DockerImageName.parse("redis:7-alpine")
        )

        private const val JWT_SECRET = "default-secret-key-change-in-production-min-32-chars"
        private const val CHANNEL = "quotes.ticks"
    }

    private fun signTestToken(userId: Long): String =
        JWT.create()
            .withSubject("user-$userId")
            .withClaim("userId", userId)
            .withClaim("username", "test")
            .sign(Algorithm.HMAC256(JWT_SECRET))

    @BeforeAll
    fun cleanWebSocketManager() {
        // The manager is a singleton; clear any leftovers from prior runs.
        val field = WebSocketManager::class.java.getDeclaredField("sessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(WebSocketManager) as java.util.concurrent.ConcurrentHashMap<*, *>
        map.clear()
    }

    @AfterAll
    fun shutdown() {
        // RedisSubscriber holds onto a Lettuce client; closing the container is enough.
    }

    @Test
    fun `quote published to redis fans out to connected ws clients`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to JWT_SECRET,
                "redis.host" to redis.host,
                "redis.port" to redis.firstMappedPort.toString(),
                "redis.channel" to CHANNEL
            )
        }

        application {
            install(WebSockets)
            configureWebSocketRoute()
            RedisSubscriber.start(this)
        }

        val token = signTestToken(42L)
        val client = createClient { install(ClientWebSockets) }

        client.webSocket({ url("ws://0.0.0.0/ws?token=$token") }) {
            // First message from server is the "connected" greeting; drain it.
            val greeting = withTimeout(5.seconds) { incoming.receive() } as Frame.Text
            assertTrue(greeting.readText().contains("connected"))

            // Publish a quote directly to Redis bypassing quotation-receiver.
            publishQuote("AAPL", 175.50)

            // Wait for the price_update message to arrive.
            val update = withTimeout(5.seconds) { incoming.receive() } as Frame.Text
            val text = update.readText()
            assertTrue(text.contains("price_update"))
            assertTrue(text.contains("AAPL"))
            assertTrue(text.contains("175.5"))
        }
    }

    @Test
    fun `ws without jwt is rejected`() = testApplication {
        environment {
            config = MapApplicationConfig(
                "jwt.secret" to JWT_SECRET,
                "redis.host" to redis.host,
                "redis.port" to redis.firstMappedPort.toString(),
                "redis.channel" to CHANNEL
            )
        }

        application {
            install(WebSockets)
            configureWebSocketRoute()
        }

        val client = createClient { install(ClientWebSockets) }

        client.webSocket({ url("ws://0.0.0.0/ws") }) {
            // Server should close the socket promptly.
            val reason = closeReason.await()
            // VIOLATED_POLICY = 1008
            assertEquals(1008.toShort(), reason?.code)
        }
    }

    private fun publishQuote(ticker: String, price: Double) {
        val uri = RedisURI.builder()
            .withHost(redis.host)
            .withPort(redis.firstMappedPort)
            .build()
        val client = RedisClient.create(uri)
        try {
            client.connect().use { conn ->
                val payload = """{"ticker":"$ticker","price":$price,"timestamp":"2026-05-14T10:00:00Z"}"""
                conn.sync().publish(CHANNEL, payload)
            }
        } finally {
            client.shutdown()
        }
    }
}
