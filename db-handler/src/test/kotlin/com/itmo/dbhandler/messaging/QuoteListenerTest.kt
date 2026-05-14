package com.itmo.dbhandler.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.itmo.dbhandler.service.QuoteService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.listener.RedisMessageListenerContainer

class QuoteListenerTest {

    private val quoteService = mockk<QuoteService>(relaxed = true)
    private val container = mockk<RedisMessageListenerContainer>(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    private val listener = QuoteListener(quoteService, objectMapper, container, "quotes.ticks")

    private fun message(payload: String) = mockk<Message>().also {
        every { it.body } returns payload.toByteArray()
    }

    @Test
    fun `onMessage forwards parsed quote to service`() {
        val payload = """{"ticker":"AAPL","price":123.45,"timestamp":"2026-05-14T10:00:00Z"}"""

        listener.onMessage(message(payload), null)

        val eventSlot = slot<QuoteEvent>()
        verify { quoteService.processQuote(capture(eventSlot)) }
        assertEquals("AAPL", eventSlot.captured.ticker)
        assertEquals(123.45f, eventSlot.captured.price, 0.001f)
    }

    @Test
    fun `onMessage swallows malformed json`() {
        listener.onMessage(message("not-json"), null)
        verify(exactly = 0) { quoteService.processQuote(any()) }
    }
}
