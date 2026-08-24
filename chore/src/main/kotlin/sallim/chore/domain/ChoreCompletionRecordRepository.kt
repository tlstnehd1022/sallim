package sallim.chore.domain

import java.time.Instant

interface ChoreCompletionRecordRepository {
    fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant)
}
