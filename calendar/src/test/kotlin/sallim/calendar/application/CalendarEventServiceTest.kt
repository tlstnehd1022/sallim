package sallim.calendar.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventServiceTest : FunSpec({
    test("단일 일정을 생성하고 범위 안에서 조회하면 자기 자신 하나가 나온다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val member = MemberId.generate()

        val created = service.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), member, "케이크 사기", null
        )

        val result = service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        result shouldHaveSize 1
        result.first().first.id shouldBe created.id
        result.first().second shouldBe LocalDateTime.of(2026, 9, 10, 14, 0)
    }

    test("조회 범위 밖의 단일 일정은 나오지 않는다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), MemberId.generate(), null, null)

        val result = service.occurrencesIn(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        result shouldHaveSize 0
    }

    test("반복 일정은 범위 안에서 여러 번 나온다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        service.create("운동", LocalDateTime.of(2026, 9, 1, 7, 0), MemberId.generate(), null, Daily)

        val result = service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))
        result shouldHaveSize 5
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<IllegalArgumentException> {
            service.occurrencesIn(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1))
        }
    }

    test("to가 비현실적으로 먼 미래면 IllegalArgumentException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<IllegalArgumentException> {
            service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.MAX)
        }
    }

    test("존재하는 일정을 수정하면 값이 갱신된다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val member = MemberId.generate()
        val created = service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), member, null, null)

        val updated = service.update(
            created.id, "생일파티", LocalDateTime.of(2026, 9, 11, 18, 0), member, "장소 예약", null
        )

        updated.title shouldBe "생일파티"
        updated.startAt shouldBe LocalDateTime.of(2026, 9, 11, 18, 0)
        updated.memo shouldBe "장소 예약"
    }

    test("존재하지 않는 일정을 수정하면 NotFoundException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<NotFoundException> {
            service.update(
                CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
                MemberId.generate(), null, null
            )
        }
    }

    test("존재하는 일정을 삭제하면 이후 조회에서 사라진다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val created = service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), MemberId.generate(), null, null)

        service.delete(created.id)

        service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)) shouldHaveSize 0
    }

    test("존재하지 않는 일정을 삭제하면 NotFoundException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<NotFoundException> {
            service.delete(CalendarEventId.generate())
        }
    }
})
