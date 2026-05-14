package com.itmo.websocket

import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.url
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class WebSocketManagerTest {

    /**
     * WebSocketManager is a singleton, so we have to reset it between tests
     * to keep them order-independent.
     */
    private fun resetManager() {
        val field = WebSocketManager::class.java.getDeclaredField("sessions")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(WebSocketManager) as java.util.concurrent.ConcurrentHashMap<
            String, java.util.concurrent.CopyOnWriteArrayList<DefaultWebSocketServerSession>>
        val totalField = WebSocketManager::class.java.getDeclaredField("totalCount")
        totalField.isAccessible = true
        val total = totalField.get(WebSocketManager) as java.util.concurrent.atomic.AtomicInteger
        map.clear()
        total.set(0)
    }

    @BeforeEach
    fun setUp() = resetManager()

    @AfterEach
    fun tearDown() = resetManager()

    @Test
    fun `addSession increments totals`() = testApplication {
        install(WebSockets)
        val joined = CompletableDeferred<Unit>()
        routing {
            webSocket("/ws") {
                WebSocketManager.addSession("u1", this)
                joined.complete(Unit)
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }
        client.webSocket({ url("ws://0.0.0.0/ws") }) {
            joined.await()
            assertEquals(1, WebSocketManager.getTotalSessions())
            assertEquals(1, WebSocketManager.getConnectedUserCount())
        }
    }

    @Test
    fun `broadcastPriceUpdate reaches subscriber`() = testApplication {
        install(WebSockets)
        val joined = CompletableDeferred<Unit>()
        routing {
            webSocket("/ws") {
                WebSocketManager.addSession("u1", this)
                joined.complete(Unit)
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }
        client.webSocket({ url("ws://0.0.0.0/ws") }) {
            joined.await()
            WebSocketManager.broadcastPriceUpdate("""{"type":"price_update"}""")

            val frame = withTimeout(2.seconds) { incoming.receive() } as Frame.Text
            assertTrue(frame.readText().contains("price_update"))
        }
    }

    @Test
    fun `sendToUser delivers only to requested user`() = testApplication {
        install(WebSockets)
        val joinedAlice = CompletableDeferred<Unit>()
        val joinedBob = CompletableDeferred<Unit>()
        routing {
            webSocket("/ws/alice") {
                WebSocketManager.addSession("alice", this)
                joinedAlice.complete(Unit)
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
            webSocket("/ws/bob") {
                WebSocketManager.addSession("bob", this)
                joinedBob.complete(Unit)
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }
        client.webSocket({ url("ws://0.0.0.0/ws/bob") }) {
            client.webSocket({ url("ws://0.0.0.0/ws/alice") }) {
                joinedAlice.await()
                joinedBob.await()
                assertEquals(2, WebSocketManager.getConnectedUserCount())

                WebSocketManager.sendToUser("alice", """{"to":"alice"}""")

                val aliceFrame = withTimeout(2.seconds) { incoming.receive() } as Frame.Text
                assertTrue(aliceFrame.readText().contains("alice"))
            }
            // After exiting the inner block, give bob a window to *not* receive anything.
            // If sendToUser bled to the wrong user, the receive below would succeed.
            delay(150)
            assertTrue(incoming.tryReceive().isFailure)
        }
    }

    @Test
    fun `removeSession decrements and clears empty bucket`() = testApplication {
        install(WebSockets)
        val joined = CompletableDeferred<DefaultWebSocketServerSession>()
        routing {
            webSocket("/ws") {
                WebSocketManager.addSession("u1", this)
                joined.complete(this)
                for (frame in incoming) {
                    if (frame is Frame.Close) break
                }
            }
        }
        val client = createClient { install(ClientWebSockets) }
        client.webSocket({ url("ws://0.0.0.0/ws") }) {
            val session = joined.await()
            assertEquals(1, WebSocketManager.getTotalSessions())

            WebSocketManager.removeSession("u1", session)

            assertEquals(0, WebSocketManager.getTotalSessions())
            assertEquals(0, WebSocketManager.getConnectedUserCount())
        }
    }
}
