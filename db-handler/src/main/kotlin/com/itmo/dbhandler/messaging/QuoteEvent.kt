package com.itmo.dbhandler.messaging

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

data class QuoteEvent(
    @JsonProperty("ticker")
    val ticker: String,
    @JsonProperty("price")
    val price: Float,
    @JsonProperty("timestamp")
    val timestamp: String
) {
    fun getTimestampAsOffsetDateTime(): OffsetDateTime {
        return try {
            OffsetDateTime.parse(timestamp)
        } catch (e: Exception) {
            OffsetDateTime.now()
        }
    }
}