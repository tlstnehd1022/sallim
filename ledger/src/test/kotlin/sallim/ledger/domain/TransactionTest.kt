package sallim.ledger.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class TransactionTest : FunSpec({
    test("정상적인 값으로 생성하면 필드가 그대로 보존된다") {
        val id = TransactionId.generate()
        val memberId = MemberId.generate()
        val occurredAt = LocalDateTime.of(2026, 9, 9, 14, 30)

        val transaction = Transaction(id, memberId, 15000L, "식비", "장보기", occurredAt)

        transaction.id shouldBe id
        transaction.memberId shouldBe memberId
        transaction.amount shouldBe 15000L
        transaction.category shouldBe "식비"
        transaction.memo shouldBe "장보기"
        transaction.occurredAt shouldBe occurredAt
    }

    test("memo는 null일 수 있다") {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 5000L, "생활용품", null,
            LocalDateTime.of(2026, 9, 9, 10, 0)
        )

        transaction.memo shouldBe null
    }

    test("amount가 0이면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), 0L, "식비", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }

    test("amount가 음수면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), -1000L, "식비", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }

    test("category가 공백이면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), 1000L, "   ", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }
})
