package com.itmo.redis

import com.itmo.websocket.WebSocketManager
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.ktor.server.application.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.time.Instant

object RedisSubscriber {
    private val logger = LoggerFactory.getLogger(RedisSubscriber::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)
    private var redisClient: RedisClient? = null
    private var connection: StatefulRedisPubSubConnection<String, String>? = null

    fun start(application: Application) {
        val config = application.environment.config
        val host = System.getenv("REDIS_HOST")
            ?: config.propertyOrNull("redis.host")?.getString()
            ?: "localhost"
        val port = System.getenv("REDIS_PORT")?.toIntOrNull()
            ?: config.propertyOrNull("redis.port")?.getString()?.toIntOrNull()
            ?: 6379
        val channel = System.getenv("REDIS_CHANNEL")
            ?: config.propertyOrNull("redis.channel")?.getString()
            ?: "quotes.ticks"

        try {
            redisClient = RedisClient.create(
                RedisURI.builder()
                    .withHost(host)
                    .withPort(port)
                    .build()
            )

            connection = redisClient!!.connectPubSub()
            connection!!.addListener(object : RedisPubSubAdapter<String, String>() {
                override fun message(channel: String, message: String) {
                    scope.launch { handleQuoteMessage(message) }
                }
            })

            connection!!.sync().subscribe(channel)
            logger.info("Redis subscriber started: $host:$port, channel=$channel")
        } catch (e: Exception) {
            logger.error("Failed to connect to Redis: ${e.message}. Price updates via WebSocket will be unavailable.")
        }

        application.environment.monitor.subscribe(ApplicationStopped) {
            connection?.close()
            redisClient?.shutdown()
        }
    }

    private suspend fun handleQuoteMessage(raw: String) {
        try {
            val event = Json.parseToJsonElement(raw).jsonObject
            val ticker = event["ticker"]?.jsonPrimitive?.contentOrNull ?: return
            val price = event["price"]?.jsonPrimitive?.doubleOrNull ?: return
            val timestamp = event["timestamp"]?.jsonPrimitive?.contentOrNull
                ?: Instant.now().toString()

            val wsMessage = buildJsonObject {
                put("type", "price_update")
                put("data", buildJsonObject {
                    put("symbol", ticker)
                    put("price", price)
                })
                put("timestamp", timestamp)
            }.toString()

            WebSocketManager.broadcastPriceUpdate(wsMessage)
        } catch (e: Exception) {
            logger.error("Failed to process Redis quote message: ${e.message}")
        }
    }
}
