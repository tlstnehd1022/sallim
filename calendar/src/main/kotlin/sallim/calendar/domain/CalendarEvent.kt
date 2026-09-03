package sallim.calendar.domain

import sallim.common.domain.RecurrencePolicy
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEvent(
    val id: CalendarEventId,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: MemberId,
    val memo: String?,
    val recurrence: RecurrencePolicy?
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    fun occurrencesIn(from: LocalDate, to: LocalDate): List<LocalDateTime> {
        if (recurrence == null) {
            val date = startAt.toLocalDate()
            return if (date in from..to) listOf(startAt) else emptyList()
        }
        val result = mutableListOf<LocalDateTime>()
        var date = startAt.toLocalDate()
        while (!date.isAfter(to)) {
            if (!date.isBefore(from)) result += date.atTime(startAt.toLocalTime())
            date = recurrence.nextOccurrence(date)
        }
        return result
    }
}
