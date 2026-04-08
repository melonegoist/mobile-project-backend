package com.itmo.dbhandler.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.itmo.dbhandler.service.QuoteService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct


@Component
class QuoteListener(
    private val quoteService: QuoteService,
    private val objectMapper: ObjectMapper,
    private val redisMessageListenerContainer: RedisMessageListenerContainer,
    @Value("\${quotation.redis.channel}") private val channel: String
) : MessageListener {

    private val log = LoggerFactory.getLogger(QuoteListener::class.java)

    @PostConstruct
    fun init() {
        val listenerAdapter = MessageListenerAdapter(this)
        redisMessageListenerContainer.addMessageListener(listenerAdapter, PatternTopic(channel))
        log.info("Subscribed to Redis channel: {}", channel)
    }

    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val jsonString = String(message.body)
            log.debug("Received raw message: {}", jsonString)
            val quote = objectMapper.readValue(jsonString, QuoteEvent::class.java)
            log.info("Received quote: ticker={}, price={}", quote.ticker, quote.price)
            quoteService.processQuote(quote)
        } catch (e: Exception) {
            log.error("Failed to process quote message: {}", String(message.body), e)
        }
    }
}