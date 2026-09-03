package sallim.chore.domain

import sallim.common.domain.RecurrencePolicy

class ChoreDefinition(
    val id: ChoreDefinitionId,
    val roomId: RoomId,
    val label: String,
    val assigneeId: MemberId,
    val recurrence: RecurrencePolicy,
    val howToSteps: List<String>,
    val videoQuery: String
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(howToSteps.isNotEmpty()) { "howToSteps must not be empty" }
    }
}
