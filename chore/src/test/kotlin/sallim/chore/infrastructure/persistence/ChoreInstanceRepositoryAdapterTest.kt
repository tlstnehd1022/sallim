package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        found.completedAt shouldBe instance.completedAt!!.truncatedTo(ChronoUnit.MICROS)
        found.domainEvents.shouldBeEmpty()
    }
}
