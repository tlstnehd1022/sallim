package sallim.chore.infrastructure.messaging

import sallim.chore.domain.ChoreCompletionRecordRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant

class FakeChoreCompletionRecordRepository : ChoreCompletionRecordRepository {
    data class Record(
        val choreInstanceId: ChoreInstanceId,
        val choreDefinitionId: ChoreDefinitionId,
        val completedBy: MemberId,
        val completedAt: Instant
    )

    val saved = mutableListOf<Record>()

    override fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) {
        saved.add(Record(choreInstanceId, choreDefinitionId, completedBy, completedAt))
    }
}
