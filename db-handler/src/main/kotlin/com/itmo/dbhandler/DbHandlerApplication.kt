package com.itmo.dbhandler

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DbHandlerApplication

fun main(args: Array<String>) {
    runApplication<DbHandlerApplication>(*args)
}
