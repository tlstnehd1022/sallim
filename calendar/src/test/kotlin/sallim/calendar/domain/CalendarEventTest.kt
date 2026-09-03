package sallim.calendar.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventTest : FunSpec({
    fun event(startAt: LocalDateTime, recurrence: sallim.common.domain.RecurrencePolicy? = null, title: String = "일정") =
        CalendarEvent(CalendarEventId.generate(), title, startAt, MemberId.generate(), null, recurrence)

    test("제목이 공백이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            event(LocalDateTime.of(2026, 8, 20, 15, 0), title = " ")
        }
    }

    test("단일 일정은 조회 범위 안에 있으면 자기 자신 하나를 반환한다") {
        val startAt = LocalDateTime.of(2026, 8, 20, 15, 0)
        val single = event(startAt)

        val result = single.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first() shouldBe startAt
    }

    test("단일 일정은 조회 범위 밖이면 빈 목록을 반환한다") {
        val single = event(LocalDateTime.of(2026, 9, 1, 15, 0))

        val result = single.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result.shouldBeEmpty()
    }

    test("Daily 반복은 범위 안의 모든 날짜에 같은 시각으로 나타난다") {
        val startAt = LocalDateTime.of(2026, 8, 18, 9, 30)
        val recurring = event(startAt, Daily)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22))

        result shouldHaveSize 3
        result shouldBe listOf(
            LocalDateTime.of(2026, 8, 20, 9, 30),
            LocalDateTime.of(2026, 8, 21, 9, 30),
            LocalDateTime.of(2026, 8, 22, 9, 30)
        )
    }

    test("WeeklyNTimes 반복도 계산된 간격으로 범위 안에 나타난다") {
        val startAt = LocalDateTime.of(2026, 8, 1, 18, 0)
        val recurring = event(startAt, WeeklyNTimes(2))  // nextOccurrence는 7/2=3일 간격

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10))

        result shouldBe listOf(
            LocalDateTime.of(2026, 8, 1, 18, 0),
            LocalDateTime.of(2026, 8, 4, 18, 0),
            LocalDateTime.of(2026, 8, 7, 18, 0),
            LocalDateTime.of(2026, 8, 10, 18, 0)
        )
    }

    test("Monthly 반복도 계산된 간격으로 범위 안에 나타난다") {
        val startAt = LocalDateTime.of(2026, 6, 15, 12, 0)
        val recurring = event(startAt, Monthly)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first() shouldBe LocalDateTime.of(2026, 8, 15, 12, 0)
    }

    test("반복 일정도 시작일이 조회 범위보다 늦으면 빈 목록을 반환한다") {
        val recurring = event(LocalDateTime.of(2026, 9, 1, 9, 0), Daily)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result.shouldBeEmpty()
    }

    test("Monthly 반복은 말일 시작 시 짧은 달을 지나면서 날짜가 영구히 밀린다 (알려진 한계)") {
        val startAt = LocalDateTime.of(2026, 1, 31, 10, 0)
        val recurring = event(startAt, Monthly)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 4, 30))

        result shouldBe listOf(
            LocalDateTime.of(2026, 1, 31, 10, 0),
            LocalDateTime.of(2026, 2, 28, 10, 0),
            LocalDateTime.of(2026, 3, 28, 10, 0),  // 3/31이 아니라 3/28 — 2월 클램핑이 영구히 이어짐
            LocalDateTime.of(2026, 4, 28, 10, 0)   // 4/30도 아님
        )
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val single = event(LocalDateTime.of(2026, 8, 20, 15, 0))

        shouldThrow<IllegalArgumentException> {
            single.occurrencesIn(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))
        }
    }

    test("to가 비현실적으로 먼 미래면 IllegalArgumentException") {
        val single = event(LocalDateTime.of(2026, 8, 20, 15, 0))

        shouldThrow<IllegalArgumentException> {
            single.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.MAX)
        }
    }
})
