package sallim.ledger.infrastructure.persistence

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
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransactionRepositoryAdapter::class)
class TransactionRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: TransactionRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `저장한 거래를 다시 읽으면 값이 같다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 15000L, "식비", "장보기",
            LocalDateTime.of(2026, 9, 10, 14, 30)
        )

        adapter.save(transaction)
        em.flush()
        em.clear()

        val found = adapter.findById(transaction.id)
        found.shouldNotBeNull()
        found.memberId shouldBe transaction.memberId
        found.amount shouldBe 15000L
        found.category shouldBe "식비"
        found.memo shouldBe "장보기"
        found.occurredAt shouldBe LocalDateTime.of(2026, 9, 10, 14, 30)
    }

    @Test
    fun `memo가 null인 거래를 저장하고 조회하면 null로 돌아온다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 5000L, "생활용품", null,
            LocalDateTime.of(2026, 9, 10, 10, 0)
        )

        adapter.save(transaction)
        em.flush()
        em.clear()

        val found = adapter.findById(transaction.id)
        found.shouldNotBeNull()
        found.memo shouldBe null
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(TransactionId.generate()).shouldBeNull()
    }

    @Test
    fun `findByOccurredAtBetween은 경계값을 포함한다`() {
        val before = Transaction(
            TransactionId.generate(), MemberId.generate(), 1000L, "식비", null,
            LocalDateTime.of(2026, 8, 31, 23, 59)
        )
        val fromBoundary = Transaction(
            TransactionId.generate(), MemberId.generate(), 2000L, "식비", null,
            LocalDateTime.of(2026, 9, 1, 0, 0)
        )
        val toBoundary = Transaction(
            TransactionId.generate(), MemberId.generate(), 3000L, "식비", null,
            LocalDateTime.of(2026, 9, 30, 23, 59)
        )
        val after = Transaction(
            TransactionId.generate(), MemberId.generate(), 4000L, "식비", null,
            LocalDateTime.of(2026, 10, 1, 0, 0)
        )
        adapter.save(before)
        adapter.save(fromBoundary)
        adapter.save(toBoundary)
        adapter.save(after)
        em.flush()
        em.clear()

        val found = adapter.findByOccurredAtBetween(
            LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59)
        )

        found shouldHaveSize 2
        found.map { it.amount }.toSet() shouldBe setOf(2000L, 3000L)
    }

    @Test
    fun `삭제하면 findById 결과가 null이 된다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 1000L, "식비", null,
            LocalDateTime.of(2026, 9, 10, 14, 0)
        )
        adapter.save(transaction)
        em.flush()
        em.clear()

        adapter.deleteById(transaction.id)
        em.flush()
        em.clear()

        adapter.findById(transaction.id).shouldBeNull()
    }
}
