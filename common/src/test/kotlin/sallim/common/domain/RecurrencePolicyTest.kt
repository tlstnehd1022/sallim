package sallim.common.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class RecurrencePolicyTest : FunSpec({
    val today = LocalDate.of(2026, 8, 19)

    test("Daily는 하루 뒤를 반환한다") {
        Daily.nextOccurrence(today) shouldBe today.plusDays(1)
    }

    test("WeeklyNTimes(1)은 7일 뒤를 반환한다") {
        WeeklyNTimes(1).nextOccurrence(today) shouldBe today.plusDays(7)
    }

    test("WeeklyNTimes(2)는 3일 뒤를 반환한다") {
        WeeklyNTimes(2).nextOccurrence(today) shouldBe today.plusDays(3)
    }

    test("WeeklyNTimes(7)은 1일 뒤를 반환한다") {
        WeeklyNTimes(7).nextOccurrence(today) shouldBe today.plusDays(1)
    }

    test("WeeklyNTimes(5)는 정수 나눗셈으로 1일 뒤를 반환한다 (근사치 한계)") {
        WeeklyNTimes(5).nextOccurrence(today) shouldBe today.plusDays(1)
    }

    test("Monthly는 한 달 뒤를 반환한다") {
        Monthly.nextOccurrence(today) shouldBe today.plusMonths(1)
    }

    test("WeeklyNTimes는 1..7 범위를 벗어나면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> { WeeklyNTimes(0) }
        shouldThrow<IllegalArgumentException> { WeeklyNTimes(8) }
    }
})
