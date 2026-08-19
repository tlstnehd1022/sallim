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
// ponytail: times가 4~6이면 정수 나눗셈(7/times)이 1일 간격으로 절삭됨 — 위와 동일한 요일 기반 재설계(갭#1)로 해소
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
