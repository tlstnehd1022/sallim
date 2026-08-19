package sallim.chore.domain

import sallim.common.domain.DomainEvent
import java.time.Instant

data class ChoreCompletedEvent(
    val choreInstanceId: ChoreInstanceId,
    val choreDefinitionId: ChoreDefinitionId,
    val completedBy: MemberId,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
