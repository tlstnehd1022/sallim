package sallim.ledger.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
class TransactionEntity(
    @Id
    val id: String,
    val memberId: String,
    val amount: Long,
    val category: String,
    val memo: String?,
    val occurredAt: LocalDateTime
)
