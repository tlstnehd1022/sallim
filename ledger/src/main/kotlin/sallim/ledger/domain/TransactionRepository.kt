package sallim.ledger.domain

import java.time.LocalDateTime

interface TransactionRepository {
    fun save(transaction: Transaction): Transaction
    fun findById(id: TransactionId): Transaction?
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction>
    fun deleteById(id: TransactionId)
}
