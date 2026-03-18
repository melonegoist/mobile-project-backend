package com.itmo.dbhandler.dto

import java.time.OffsetDateTime

data class ErrorResponse(
    val code: Int,
    val message: String,
    val details: String? = null,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    val path: String? = null
)