package sallim.calendar.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.calendar.application.CalendarEventService
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class RecurrenceDto(val type: String, val times: Int?)

data class CalendarEventRequest(
    val title: String,
    val startAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?,
    val recurrence: RecurrenceDto?
)

data class CalendarEventResponse(
    val id: UUID,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?,
    val recurrence: RecurrenceDto?
)

data class CalendarEventOccurrenceResponse(
    val eventId: UUID,
    val title: String,
    val occurredAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?
)

@RestController
@RequestMapping("/api/calendar-events")
class CalendarEventController(private val service: CalendarEventService) {

    @GetMapping
    fun occurrencesIn(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): List<CalendarEventOccurrenceResponse> =
        service.occurrencesIn(from, to).map { (event, occurredAt) ->
            CalendarEventOccurrenceResponse(event.id.value, event.title, occurredAt, event.memberId.value, event.memo)
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CalendarEventRequest): CalendarEventResponse =
        service.create(
            request.title, request.startAt, MemberId(request.memberId), request.memo, request.recurrence?.toDomain()
        ).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: CalendarEventRequest): CalendarEventResponse =
        service.update(
            CalendarEventId(id), request.title, request.startAt, MemberId(request.memberId),
            request.memo, request.recurrence?.toDomain()
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        service.delete(CalendarEventId(id))
    }

    private fun CalendarEvent.toResponse() =
        CalendarEventResponse(id.value, title, startAt, memberId.value, memo, recurrence?.toDto())

    private fun RecurrenceDto.toDomain(): RecurrencePolicy = when (type) {
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "times is required for WEEKLY_N_TIMES" })
        "MONTHLY" -> Monthly
        else -> throw IllegalArgumentException("unknown recurrence type: $type")
    }

    private fun RecurrencePolicy.toDto(): RecurrenceDto = when (this) {
        is Daily -> RecurrenceDto("DAILY", null)
        is WeeklyNTimes -> RecurrenceDto("WEEKLY_N_TIMES", times)
        is Monthly -> RecurrenceDto("MONTHLY", null)
    }
}
