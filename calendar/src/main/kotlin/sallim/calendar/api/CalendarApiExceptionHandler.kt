package sallim.calendar.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import sallim.calendar.application.NotFoundException

@RestControllerAdvice(basePackages = ["sallim.calendar"])
class CalendarApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = ResponseEntity.status(404).body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException) = ResponseEntity.status(400).body(mapOf("error" to e.message))

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? =
        ResponseEntity.status(statusCode).headers(headers).body(mapOf("error" to ex.message))
}
