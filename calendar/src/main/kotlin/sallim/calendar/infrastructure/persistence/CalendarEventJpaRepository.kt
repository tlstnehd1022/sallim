package sallim.calendar.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface CalendarEventJpaRepository : JpaRepository<CalendarEventEntity, String> {
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEventEntity>
}
