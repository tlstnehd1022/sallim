package sallim.calendar.domain

import sallim.common.domain.Identifier
import java.util.UUID

class CalendarEventId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): CalendarEventId = CalendarEventId(UUID.randomUUID())
    }
}
