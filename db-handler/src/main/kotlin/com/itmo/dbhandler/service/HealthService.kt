package com.itmo.dbhandler.service

import com.itmo.dbhandler.model.HealthGet200Response
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class HealthService(
    @Value("\${spring.application.version:1.0.0}") private val appVersion: String
) {

    fun getHealth(): HealthGet200Response {
        return HealthGet200Response(
            status = "healthy",
            version = appVersion,
            timestamp = OffsetDateTime.now()
        )
    }
}