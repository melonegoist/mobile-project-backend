package com.itmo.dbhandler.controller

import com.itmo.dbhandler.api.HealthApi
import com.itmo.dbhandler.model.HealthGet200Response
import com.itmo.dbhandler.service.HealthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val healthService: HealthService
) : HealthApi {

    override fun healthGet(): ResponseEntity<HealthGet200Response> {
        return ResponseEntity.ok(healthService.getHealth())
    }
}