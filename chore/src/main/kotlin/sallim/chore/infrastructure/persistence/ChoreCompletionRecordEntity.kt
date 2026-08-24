package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "chore_completion_record")
class ChoreCompletionRecordEntity(
    @Id
    val id: String,
    val choreInstanceId: String,
    val choreDefinitionId: String,
    val completedBy: String,
    val completedAt: Instant
)
