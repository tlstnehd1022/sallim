package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreStatsServiceTest : FunSpec({
    test("쿼리 포트 결과를 그대로 반환한다") {
        val query = FakeChoreCompletionStatsQuery()
        val member = MemberId.generate()
        query.result = listOf(MemberCompletionCount(member, 3L))
        val service = ChoreStatsService(query)

        val result = service.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first().memberId shouldBe member
        result.first().count shouldBe 3L
        query.lastFrom shouldBe LocalDate.of(2026, 8, 1)
        query.lastTo shouldBe LocalDate.of(2026, 8, 31)
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = ChoreStatsService(FakeChoreCompletionStatsQuery())

        shouldThrow<IllegalArgumentException> {
            service.countByMember(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))
        }
    }
})
