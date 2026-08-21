package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "chore_instance")
class ChoreInstanceEntity(
    @Id
    val id: String,
    val choreDefinitionId: String,
    val scheduledDate: LocalDate,
    val completed: Boolean,
    val completedBy: String?,
    val completedAt: Instant?
)
