package sallim.chore.domain

import java.time.LocalDate

sealed interface RecurrencePolicy {
    fun nextOccurrence(after: LocalDate): LocalDate
}

data object Daily : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusDays(1)
}

// ponytail: 요일 지정 없는 균등 분배 근사치(7일/times) — 특정 요일 반복이 필요해지면(갭#1)
// 요일 집합을 받는 정책으로 교체
data class WeeklyNTimes(val times: Int) : RecurrencePolicy {
    init {
        require(times in 1..7) { "times must be within 1..7: $times" }
    }

    override fun nextOccurrence(after: LocalDate): LocalDate =
        after.plusDays((7 / times).toLong())
}

data object Monthly : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusMonths(1)
}
