package sallim.ledger.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class TransactionRepositoryAdapter(
    private val jpaRepository: TransactionJpaRepository
) : TransactionRepository {

    override fun save(transaction: Transaction): Transaction {
        jpaRepository.save(
            TransactionEntity(
                id = transaction.id.value.toString(),
                memberId = transaction.memberId.value.toString(),
                amount = transaction.amount,
                category = transaction.category,
                memo = transaction.memo,
                occurredAt = transaction.occurredAt
            )
        )
        return transaction
    }

    override fun findById(id: TransactionId): Transaction? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction> =
        jpaRepository.findByOccurredAtBetween(from, to).map { it.toDomain() }

    override fun deleteById(id: TransactionId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = TransactionId(UUID.fromString(id)),
        memberId = MemberId(UUID.fromString(memberId)),
        amount = amount,
        category = category,
        memo = memo,
        occurredAt = occurredAt
    )
}
