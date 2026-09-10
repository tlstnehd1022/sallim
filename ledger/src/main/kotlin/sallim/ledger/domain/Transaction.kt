package sallim.ledger.domain

import java.time.LocalDateTime

class Transaction(
    val id: TransactionId,
    val memberId: MemberId,
    val amount: Long,
    val category: String,
    val memo: String?,
    val occurredAt: LocalDateTime
) {
    init {
        require(amount > 0) { "amount must be positive: $amount" }
        require(category.isNotBlank()) { "category must not be blank" }
    }
}
