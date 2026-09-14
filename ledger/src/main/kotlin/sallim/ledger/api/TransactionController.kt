package sallim.ledger.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.ledger.application.TransactionService
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import java.time.LocalDateTime
import java.util.UUID

data class TransactionRequest(
    val memberId: UUID, val amount: Long, val category: String, val memo: String?, val occurredAt: LocalDateTime
)

data class TransactionResponse(
    val id: UUID, val memberId: UUID, val amount: Long, val category: String, val memo: String?, val occurredAt: LocalDateTime
)

@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

    @GetMapping
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(required = false) memberId: UUID?,
        @RequestParam(required = false) category: String?
    ): List<TransactionResponse> =
        service.list(from, to, memberId?.let { MemberId(it) }, category).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: TransactionRequest): TransactionResponse =
        service.create(MemberId(request.memberId), request.amount, request.category, request.memo, request.occurredAt)
            .toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: TransactionRequest): TransactionResponse =
        service.update(
            TransactionId(id), MemberId(request.memberId), request.amount, request.category, request.memo, request.occurredAt
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        service.delete(TransactionId(id))
    }

    private fun Transaction.toResponse() =
        TransactionResponse(id.value, memberId.value, amount, category, memo, occurredAt)
}
