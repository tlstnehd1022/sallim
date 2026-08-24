package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaChoreCompletionRecordRepository::class)
class JpaChoreCompletionRecordRepositoryTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var repository: JpaChoreCompletionRecordRepository

    @Autowired
    lateinit var jpaRepository: ChoreCompletionRecordJpaRepository

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `완료 기록을 저장하면 조회된다`() {
        val instanceId = ChoreInstanceId.generate()
        val definitionId = ChoreDefinitionId.generate()
        val member = MemberId.generate()
        val completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()

        val all = jpaRepository.findAll()
        all shouldHaveSize 1
        all.first().choreInstanceId shouldBe instanceId.value.toString()
        all.first().choreDefinitionId shouldBe definitionId.value.toString()
        all.first().completedBy shouldBe member.value.toString()
        all.first().completedAt shouldBe completedAt
    }

    @Test
    fun `같은 인스턴스로 두 번 저장해도 중복 없이 무시된다`() {
        val instanceId = ChoreInstanceId.generate()
        val definitionId = ChoreDefinitionId.generate()
        val member = MemberId.generate()
        val completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()
        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()

        jpaRepository.findAll() shouldHaveSize 1
    }
}
