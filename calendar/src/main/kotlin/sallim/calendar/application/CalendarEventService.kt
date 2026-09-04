package sallim.calendar.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import sallim.calendar.domain.MemberId
import sallim.common.domain.RecurrencePolicy
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CalendarEventService(private val repository: CalendarEventRepository) {

    @Transactional(readOnly = true)
    fun occurrencesIn(from: LocalDate, to: LocalDate): List<Pair<CalendarEvent, LocalDateTime>> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
        val toDateTime = to.plusDays(1).atStartOfDay().minusNanos(1)
        return repository.findByStartAtLessThanEqual(toDateTime)
            .flatMap { event -> event.occurrencesIn(from, to).map { event to it } }
            .sortedBy { it.second }
    }

    @Transactional
    fun create(
        title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?
    ): CalendarEvent =
        repository.save(CalendarEvent(CalendarEventId.generate(), title, startAt, memberId, memo, recurrence))

    @Transactional
    fun update(
        id: CalendarEventId, title: String, startAt: LocalDateTime, memberId: MemberId,
        memo: String?, recurrence: RecurrencePolicy?
    ): CalendarEvent {
        repository.findById(id) ?: throw NotFoundException("calendar event not found: $id")
        return repository.save(CalendarEvent(id, title, startAt, memberId, memo, recurrence))
    }

    @Transactional
    fun delete(id: CalendarEventId) {
        repository.findById(id) ?: throw NotFoundException("calendar event not found: $id")
        repository.deleteById(id)
    }
}
