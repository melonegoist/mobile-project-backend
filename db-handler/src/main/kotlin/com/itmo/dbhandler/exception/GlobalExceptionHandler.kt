package com.itmo.dbhandler.exception

import com.itmo.dbhandler.model.Error
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException, request: WebRequest): ResponseEntity<Error> {
        val error = Error(
            code = ex.statusCode.value(),
            message = ex.reason ?: "Error occurred",
            details = ex.message,
            timestamp = OffsetDateTime.now(),
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity(error, ex.statusCode)
    }

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentialsException(ex: BadCredentialsException, request: WebRequest): ResponseEntity<Error> {
        val error = Error(
            code = HttpStatus.UNAUTHORIZED.value(),
            message = "Invalid credentials",
            details = ex.message,
            timestamp = OffsetDateTime.now(),
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity(error, HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException, request: WebRequest): ResponseEntity<Error> {
        val errors = ex.bindingResult.allErrors.joinToString(", ") {
            ((it as? FieldError)?.let { "${it.field}: ${it.defaultMessage}" } ?: it.defaultMessage).toString()
        }

        val error = Error(
            code = HttpStatus.BAD_REQUEST.value(),
            message = "Validation failed",
            details = errors,
            timestamp = OffsetDateTime.now(),
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: WebRequest): ResponseEntity<Error> {
        val error = Error(
            code = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = "Internal server error",
            details = ex.message,
            timestamp = OffsetDateTime.now(),
            path = request.getDescription(false).removePrefix("uri=")
        )
        return ResponseEntity(error, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}