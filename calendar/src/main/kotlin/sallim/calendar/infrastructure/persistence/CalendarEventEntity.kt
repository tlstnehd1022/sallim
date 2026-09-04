package sallim.calendar.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "calendar_event")
class CalendarEventEntity(
    @Id
    val id: String,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: String,
    val memo: String?,
    val recurrenceType: String?,
    val recurrenceTimes: Int?
)
