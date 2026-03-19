package com.itmo.loadtester.controller

import com.itmo.loadtester.model.LoadTestStartRequest
import com.itmo.loadtester.model.LoadTestStatus
import com.itmo.loadtester.service.LoadTestService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/load-test")
class LoadTestController(private val loadTestService: LoadTestService) {

    @PostMapping("/start")
    fun start(@RequestBody(required = false) req: LoadTestStartRequest?): ResponseEntity<Map<String, Any>> {
        loadTestService.start(
            totalUsers = req?.totalUsers ?: 0,
            rampUpSeconds = req?.rampUpSeconds ?: 0,
            minDelayMs = req?.minActionDelayMs ?: 0L,
            maxDelayMs = req?.maxActionDelayMs ?: 0L
        )
        return ResponseEntity.ok(mapOf("status" to "started", "message" to "Load test initiated"))
    }

    @PostMapping("/stop")
    fun stop(): ResponseEntity<Map<String, String>> {
        loadTestService.stop()
        return ResponseEntity.ok(mapOf("status" to "stopped"))
    }

    @GetMapping("/status")
    fun status(): LoadTestStatus = loadTestService.status()

    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> =
        ResponseEntity.ok(mapOf("status" to "healthy", "service" to "load-tester"))
}
