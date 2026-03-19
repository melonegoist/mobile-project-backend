package com.itmo.loadtester.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.DefaultValue
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "load-test")
data class LoadTestConfig(
    var targetUrl: String = "http://localhost:9090",
    var totalUsers: Int = 10_000,
    var rampUpSeconds: Int = 300,
    var minActionDelayMs: Long = 1_000,
    var maxActionDelayMs: Long = 5_000,
    var initialDeposit: Double = 100_000.0,
    var autoStart: Boolean = false,
    var websocketEnabled: Boolean = true
)
