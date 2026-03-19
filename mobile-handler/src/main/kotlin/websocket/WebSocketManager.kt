package com.itmo.websocket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

object WebSocketManager {
    private val logger = LoggerFactory.getLogger(WebSocketManager::class.java)
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<DefaultWebSocketServerSession>>()
    private val totalCount = AtomicInteger(0)

    fun addSession(userId: String, session: DefaultWebSocketServerSession) {
        sessions.getOrPut(userId) { CopyOnWriteArrayList() }.add(session)
        totalCount.incrementAndGet()
        logger.debug("WebSocket session added for user=$userId total=${totalCount.get()}")
    }

    fun removeSession(userId: String, session: DefaultWebSocketServerSession) {
        val list = sessions[userId] ?: return
        if (list.remove(session)) {
            totalCount.decrementAndGet()
        }
        if (list.isEmpty()) sessions.remove(userId)
        logger.debug("WebSocket session removed for user=$userId total=${totalCount.get()}")
    }

    suspend fun broadcastPriceUpdate(message: String) {
        val dead = mutableListOf<Pair<String, DefaultWebSocketServerSession>>()

        sessions.forEach { (userId, userSessions) ->
            userSessions.forEach { session ->
                try {
                    session.send(Frame.Text(message))
                } catch (_: Exception) {
                    dead.add(userId to session)
                }
            }
        }

        dead.forEach { (userId, session) -> removeSession(userId, session) }
    }

    suspend fun sendToUser(userId: String, message: String) {
        sessions[userId]?.toList()?.forEach { session ->
            try {
                session.send(Frame.Text(message))
            } catch (_: Exception) {
                removeSession(userId, session)
            }
        }
    }

    fun getTotalSessions(): Int = totalCount.get()
    fun getConnectedUserCount(): Int = sessions.size
}
