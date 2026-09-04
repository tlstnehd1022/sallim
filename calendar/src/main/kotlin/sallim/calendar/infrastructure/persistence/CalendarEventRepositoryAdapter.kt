package sallim.calendar.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDateTime
import java.util.UUID

@Repository
class CalendarEventRepositoryAdapter(
    private val jpaRepository: CalendarEventJpaRepository
) : CalendarEventRepository {

    override fun save(event: CalendarEvent): CalendarEvent {
        val (recurrenceType, recurrenceTimes) = event.recurrence.toColumns()
        jpaRepository.save(
            CalendarEventEntity(
                id = event.id.value.toString(),
                title = event.title,
                startAt = event.startAt,
                memberId = event.memberId.value.toString(),
                memo = event.memo,
                recurrenceType = recurrenceType,
                recurrenceTimes = recurrenceTimes
            )
        )
        return event
    }

    override fun findById(id: CalendarEventId): CalendarEvent? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent> =
        jpaRepository.findByStartAtLessThanEqual(to).map { it.toDomain() }

    override fun deleteById(id: CalendarEventId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
        id = CalendarEventId(UUID.fromString(id)),
        title = title,
        startAt = startAt,
        memberId = MemberId(UUID.fromString(memberId)),
        memo = memo,
        recurrence = toRecurrencePolicy(recurrenceType, recurrenceTimes)
    )

    private fun RecurrencePolicy?.toColumns(): Pair<String?, Int?> = when (this) {
        null -> null to null
        is Daily -> "DAILY" to null
        is WeeklyNTimes -> "WEEKLY_N_TIMES" to times
        is Monthly -> "MONTHLY" to null
    }

    private fun toRecurrencePolicy(type: String?, times: Int?): RecurrencePolicy? = when (type) {
        null -> null
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "WEEKLY_N_TIMES requires recurrenceTimes" })
        "MONTHLY" -> Monthly
        else -> error("unknown recurrence type: $type")
    }
}
