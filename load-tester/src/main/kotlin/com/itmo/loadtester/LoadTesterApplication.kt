package com.itmo.loadtester

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class LoadTesterApplication

fun main(args: Array<String>) {
    runApplication<LoadTesterApplication>(*args)
}
