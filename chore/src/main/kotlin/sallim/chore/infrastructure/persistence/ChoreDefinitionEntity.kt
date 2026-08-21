package sallim.chore.infrastructure.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

@Entity
@Table(name = "chore_definition")
class ChoreDefinitionEntity(
    @Id
    val id: String,
    val roomId: String,
    val label: String,
    val assigneeId: String,
    val recurrenceType: String,
    val recurrenceTimes: Int?,
    val videoQuery: String,
    @ElementCollection
    @CollectionTable(
        name = "chore_definition_step",
        joinColumns = [JoinColumn(name = "chore_definition_id")]
    )
    @OrderColumn(name = "step_order")
    @Column(name = "step", columnDefinition = "TEXT")
    val howToSteps: List<String>
)
