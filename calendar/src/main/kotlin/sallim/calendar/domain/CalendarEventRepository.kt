package sallim.calendar.domain

import java.time.LocalDateTime

interface CalendarEventRepository {
    fun save(event: CalendarEvent): CalendarEvent
    fun findById(id: CalendarEventId): CalendarEvent?
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent>
    fun deleteById(id: CalendarEventId)
}
