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

    // ponytail: Monthly 반복을 nextOccurrence 연쇄로 계산해서, 말일 근처(29~31일) 시작 일정은
    // 첫 짧은 달(2월 등)에서 클램핑된 뒤 영구히 그 날짜로 고정됨 (예: 1/31 시작 → 2/28 → 3/28,
    // 3/31로 안 돌아옴) — startAt 기준으로 매번 재계산(startAt.plusMonths(n))하도록 바꾸면 해소.
    // chore의 스케줄러(ChoreInstanceService.generateDueInstances)도 같은 패턴이라 함께 검토 필요.
    fun occurrencesIn(from: LocalDate, to: LocalDate): List<LocalDateTime> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
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
