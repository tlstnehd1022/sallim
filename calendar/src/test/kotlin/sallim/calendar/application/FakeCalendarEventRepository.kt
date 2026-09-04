package sallim.calendar.application

import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import java.time.LocalDateTime

class FakeCalendarEventRepository : CalendarEventRepository {
    private val store = mutableMapOf<CalendarEventId, CalendarEvent>()

    override fun save(event: CalendarEvent): CalendarEvent {
        store[event.id] = event
        return event
    }

    override fun findById(id: CalendarEventId): CalendarEvent? = store[id]

    override fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent> =
        store.values.filter { !it.startAt.isAfter(to) }

    override fun deleteById(id: CalendarEventId) {
        store.remove(id)
    }
}
