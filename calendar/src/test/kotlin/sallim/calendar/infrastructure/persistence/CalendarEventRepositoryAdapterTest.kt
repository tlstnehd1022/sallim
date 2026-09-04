package sallim.calendar.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CalendarEventRepositoryAdapter::class)
class CalendarEventRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: CalendarEventRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `반복 없는 일정을 저장하고 조회하면 recurrence가 null로 돌아온다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
            MemberId.generate(), "케이크 사기", null
        )

        adapter.save(event)
        em.flush()
        em.clear()

        val found = adapter.findById(event.id)
        found.shouldNotBeNull()
        found.title shouldBe "생일"
        found.startAt shouldBe LocalDateTime.of(2026, 9, 10, 14, 0)
        found.memo shouldBe "케이크 사기"
        found.recurrence shouldBe null
    }

    @Test
    fun `반복 있는 일정을 저장하고 조회하면 recurrence가 복원된다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "운동", LocalDateTime.of(2026, 9, 1, 7, 0),
            MemberId.generate(), null, WeeklyNTimes(3)
        )

        adapter.save(event)
        em.flush()
        em.clear()

        val found = adapter.findById(event.id)
        found.shouldNotBeNull()
        found.recurrence shouldBe WeeklyNTimes(3)
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(CalendarEventId.generate()).shouldBeNull()
    }

    @Test
    fun `findByStartAtLessThanEqual은 startAt이 to보다 늦은 이벤트를 제외한다`() {
        val before = CalendarEvent(
            CalendarEventId.generate(), "이전", LocalDateTime.of(2026, 9, 10, 9, 0),
            MemberId.generate(), null, null
        )
        val boundary = CalendarEvent(
            CalendarEventId.generate(), "경계", LocalDateTime.of(2026, 9, 15, 23, 59),
            MemberId.generate(), null, null
        )
        val after = CalendarEvent(
            CalendarEventId.generate(), "이후", LocalDateTime.of(2026, 9, 20, 0, 0),
            MemberId.generate(), null, null
        )
        adapter.save(before)
        adapter.save(boundary)
        adapter.save(after)
        em.flush()
        em.clear()

        val found = adapter.findByStartAtLessThanEqual(LocalDateTime.of(2026, 9, 15, 23, 59))

        found shouldHaveSize 2
        found.map { it.title }.toSet() shouldBe setOf("이전", "경계")
    }

    @Test
    fun `삭제하면 findById 결과가 null이 된다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
            MemberId.generate(), null, null
        )
        adapter.save(event)
        em.flush()
        em.clear()

        adapter.deleteById(event.id)
        em.flush()
        em.clear()

        adapter.findById(event.id).shouldBeNull()
    }
}
