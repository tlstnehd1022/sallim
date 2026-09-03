package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.util.UUID

@Repository
class ChoreDefinitionRepositoryAdapter(
    private val jpaRepository: ChoreDefinitionJpaRepository
) : ChoreDefinitionRepository {

    override fun save(choreDefinition: ChoreDefinition): ChoreDefinition {
        val (recurrenceType, recurrenceTimes) = choreDefinition.recurrence.toColumns()
        jpaRepository.save(
            ChoreDefinitionEntity(
                id = choreDefinition.id.value.toString(),
                roomId = choreDefinition.roomId.value.toString(),
                label = choreDefinition.label,
                assigneeId = choreDefinition.assigneeId.value.toString(),
                recurrenceType = recurrenceType,
                recurrenceTimes = recurrenceTimes,
                videoQuery = choreDefinition.videoQuery,
                howToSteps = choreDefinition.howToSteps
            )
        )
        return choreDefinition
    }

    override fun findAll(): List<ChoreDefinition> =
        jpaRepository.findAll().map { it.toDomain() }

    override fun findById(id: ChoreDefinitionId): ChoreDefinition? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun deleteById(id: ChoreDefinitionId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun ChoreDefinitionEntity.toDomain(): ChoreDefinition = ChoreDefinition(
        id = ChoreDefinitionId(UUID.fromString(id)),
        roomId = RoomId(UUID.fromString(roomId)),
        label = label,
        assigneeId = MemberId(UUID.fromString(assigneeId)),
        recurrence = toRecurrencePolicy(recurrenceType, recurrenceTimes),
        howToSteps = howToSteps,
        videoQuery = videoQuery
    )

    private fun RecurrencePolicy.toColumns(): Pair<String, Int?> = when (this) {
        is Daily -> "DAILY" to null
        is WeeklyNTimes -> "WEEKLY_N_TIMES" to times
        is Monthly -> "MONTHLY" to null
    }

    private fun toRecurrencePolicy(type: String, times: Int?): RecurrencePolicy = when (type) {
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "WEEKLY_N_TIMES requires recurrenceTimes" })
        "MONTHLY" -> Monthly
        else -> error("unknown recurrence type: $type")
    }
}
