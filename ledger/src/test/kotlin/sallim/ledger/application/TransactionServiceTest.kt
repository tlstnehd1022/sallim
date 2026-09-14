package sallim.ledger.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.TransactionId
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionServiceTest : FunSpec({
    val septFrom = LocalDateTime.of(2026, 9, 1, 0, 0)
    val septTo = LocalDateTime.of(2026, 9, 30, 23, 59)

    test("생성한 거래를 조회 범위 안에서 조회할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()

        val created = service.create(member, 15000L, "식비", "장보기", LocalDateTime.of(2026, 9, 10, 14, 0))

        val result = service.list(septFrom, septTo, null, null)
        result shouldHaveSize 1
        result.first().id shouldBe created.id
    }

    test("조회 범위 밖의 거래는 나오지 않는다") {
        val service = TransactionService(FakeTransactionRepository())
        service.create(MemberId.generate(), 15000L, "식비", null, LocalDateTime.of(2026, 10, 1, 0, 0))

        service.list(septFrom, septTo, null, null) shouldHaveSize 0
    }

    test("memberId로 필터링할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member1 = MemberId.generate()
        val member2 = MemberId.generate()
        service.create(member1, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
        service.create(member2, 2000L, "식비", null, LocalDateTime.of(2026, 9, 11, 10, 0))

        val result = service.list(septFrom, septTo, member1, null)
        result shouldHaveSize 1
        result.first().memberId shouldBe member1
    }

    test("category로 필터링할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()
        service.create(member, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
        service.create(member, 2000L, "생활용품", null, LocalDateTime.of(2026, 9, 11, 10, 0))

        val result = service.list(septFrom, septTo, null, "생활용품")
        result shouldHaveSize 1
        result.first().category shouldBe "생활용품"
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<IllegalArgumentException> {
            service.list(septTo, septFrom, null, null)
        }
    }

    test("to가 비현실적으로 먼 미래면 IllegalArgumentException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<IllegalArgumentException> {
            service.list(septFrom, LocalDate.MAX.atStartOfDay(), null, null)
        }
    }

    test("존재하는 거래를 수정하면 값이 갱신된다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()
        val created = service.create(member, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))

        val updated = service.update(created.id, member, 2000L, "생활용품", "메모", LocalDateTime.of(2026, 9, 11, 11, 0))

        updated.amount shouldBe 2000L
        updated.category shouldBe "생활용품"
        updated.memo shouldBe "메모"
    }

    test("존재하지 않는 거래를 수정하면 NotFoundException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<NotFoundException> {
            service.update(TransactionId.generate(), MemberId.generate(), 1000L, "식비", null, septFrom)
        }
    }

    test("존재하는 거래를 삭제하면 이후 조회에서 사라진다") {
        val service = TransactionService(FakeTransactionRepository())
        val created = service.create(MemberId.generate(), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))

        service.delete(created.id)

        service.list(septFrom, septTo, null, null) shouldHaveSize 0
    }

    test("존재하지 않는 거래를 삭제하면 NotFoundException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<NotFoundException> {
            service.delete(TransactionId.generate())
        }
    }
})
