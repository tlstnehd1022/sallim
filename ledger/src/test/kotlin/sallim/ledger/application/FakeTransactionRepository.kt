package sallim.ledger.application

import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime

class FakeTransactionRepository : TransactionRepository {
    private val store = mutableMapOf<TransactionId, Transaction>()

    override fun save(transaction: Transaction): Transaction {
        store[transaction.id] = transaction
        return transaction
    }

    override fun findById(id: TransactionId): Transaction? = store[id]

    override fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction> =
        store.values.filter { !it.occurredAt.isBefore(from) && !it.occurredAt.isAfter(to) }

    override fun deleteById(id: TransactionId) {
        store.remove(id)
    }
}
