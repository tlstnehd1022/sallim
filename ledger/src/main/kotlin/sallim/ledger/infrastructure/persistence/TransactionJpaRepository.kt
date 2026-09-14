package sallim.ledger.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface TransactionJpaRepository : JpaRepository<TransactionEntity, String> {
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<TransactionEntity>
}
