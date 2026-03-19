package com.itmo.loadtester

import com.itmo.loadtester.config.LoadTestConfig
import com.itmo.loadtester.service.LoadTestService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class LoadTesterApplication {
    private val logger = LoggerFactory.getLogger(LoadTesterApplication::class.java)

    @Bean
    fun onStartup(loadTestService: LoadTestService, config: LoadTestConfig) = ApplicationRunner {
        logger.info(
            "Load Tester started. Target: ${config.targetUrl}, " +
            "Users: ${config.totalUsers}, Ramp-up: ${config.rampUpSeconds}s"
        )
        if (config.autoStart) {
            logger.info("AUTO_START=true – starting load test automatically")
            loadTestService.start()
        } else {
            logger.info("AUTO_START=false – use POST /load-test/start to begin")
        }
    }
}

fun main(args: Array<String>) {
    runApplication<LoadTesterApplication>(*args)
}
