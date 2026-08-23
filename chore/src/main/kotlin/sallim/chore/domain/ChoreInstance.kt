package sallim.chore.domain

import sallim.common.domain.AggregateRoot
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ChoreInstance private constructor(
    override val id: ChoreInstanceId,
    val choreDefinitionId: ChoreDefinitionId,
    val scheduledDate: LocalDate,
    completed: Boolean,
    completedBy: MemberId?,
    completedAt: Instant?
) : AggregateRoot<ChoreInstanceId>() {

    var completed: Boolean = completed
        private set
    var completedBy: MemberId? = completedBy
        private set
    var completedAt: Instant? = completedAt
        private set

    fun complete(memberId: MemberId) {
        check(!completed) { "chore instance already completed" }
        completed = true
        completedBy = memberId
        // MySQL DATETIME(6)은 마이크로초까지만 저장 — 미리 잘라둬야 저장 후 재조회 시 값이 정확히 같다.
        completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        registerEvent(
            ChoreCompletedEvent(
                choreInstanceId = id,
                choreDefinitionId = choreDefinitionId,
                completedBy = memberId
            )
        )
    }

    companion object {
        fun schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance =
            ChoreInstance(
                id = ChoreInstanceId.generate(),
                choreDefinitionId = choreDefinitionId,
                scheduledDate = scheduledDate,
                completed = false,
                completedBy = null,
                completedAt = null
            )

        fun reconstitute(
            id: ChoreInstanceId,
            choreDefinitionId: ChoreDefinitionId,
            scheduledDate: LocalDate,
            completed: Boolean,
            completedBy: MemberId?,
            completedAt: Instant?
        ): ChoreInstance =
            ChoreInstance(
                id = id,
                choreDefinitionId = choreDefinitionId,
                scheduledDate = scheduledDate,
                completed = completed,
                completedBy = completedBy,
                completedAt = completedAt
            )
    }
}
