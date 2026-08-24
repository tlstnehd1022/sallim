package sallim.chore.infrastructure.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreInstanceRepositoryAdapter::class)
class ChoreInstanceRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreInstanceRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `미완료 인스턴스를 저장하고 다시 읽으면 값이 같다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))

        adapter.save(instance)
        em.flush()
        em.clear()
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.id shouldBe instance.id
        found.choreDefinitionId shouldBe instance.choreDefinitionId
        found.scheduledDate shouldBe instance.scheduledDate
        found.completed shouldBe false
        found.completedBy shouldBe null
        found.completedAt shouldBe null
    }

    @Test
    fun `완료된 인스턴스를 저장 후 다시 읽으면 상태는 보존되고 이벤트는 재발행되지 않는다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        val member = MemberId.generate()
        instance.complete(member)

        adapter.save(instance)
        em.flush()
        em.clear()
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.completed shouldBe true
        found.completedBy shouldBe member
        found.completedAt shouldBe instance.completedAt
        found.domainEvents.shouldBeEmpty()
    }

    @Test
    fun `여러 인스턴스를 저장하면 findAll로 전부 조회된다`() {
        val a = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        val b = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21))
        adapter.save(a)
        adapter.save(b)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 2
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        adapter.save(instance)
        em.flush()
        em.clear()

        adapter.deleteById(instance.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }

    @Test
    fun `같은 정의와 날짜로 두 번 저장하면 유니크 제약 위반 예외가 난다`() {
        val definitionId = ChoreDefinitionId.generate()
        val date = LocalDate.of(2026, 8, 20)
        adapter.save(ChoreInstance.schedule(definitionId, date))
        em.flush()

        shouldThrow<DataIntegrityViolationException> {
            adapter.save(ChoreInstance.schedule(definitionId, date))
            em.flush()
        }
    }
}
