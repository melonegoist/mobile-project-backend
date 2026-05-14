package com.itmo.dbhandler.e2e

import com.itmo.dbhandler.repository.StockRepository
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.math.BigDecimal
import java.time.Duration
import java.time.OffsetDateTime

/**
 * End-to-end coverage of the quotation ingestion path:
 *   external publisher  →  Redis  →  QuoteListener  →  QuoteService  →  DB
 */
class QuoteIngestionE2eTest : E2eTestBase() {

    @Autowired
    private lateinit var stockRepository: StockRepository

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

    @Test
    fun `redis quote message updates stock price in database`() {
        val before = stockRepository.findById("AAPL").orElseThrow()
        val originalPrice = before.currentPrice

        val payload = """{"ticker":"AAPL","price":999.99,"timestamp":"${OffsetDateTime.now()}"}"""
        redisTemplate.convertAndSend("quotes.ticks", payload)

        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(200)).untilAsserted {
            val updated = stockRepository.findById("AAPL").orElseThrow()
            assertNotEquals(0, updated.currentPrice.compareTo(originalPrice)) {
                "price should change, was $originalPrice and remains ${updated.currentPrice}"
            }
            assertEquals(0, updated.currentPrice.compareTo(BigDecimal("999.9900")))
        }
    }

    @Test
    fun `unknown ticker is dropped silently`() {
        val payload = """{"ticker":"NOPE","price":1.23,"timestamp":"${OffsetDateTime.now()}"}"""
        redisTemplate.convertAndSend("quotes.ticks", payload)

        // Just give the listener some time, then check the stock list is untouched.
        Thread.sleep(500)
        // There is no stock NOPE, so findById remains empty.
        assertTrue(stockRepository.findById("NOPE").isEmpty)
    }
}
