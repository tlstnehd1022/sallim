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
import java.time.LocalDate
import java.time.ZoneId

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaChoreCompletionRecordRepository::class, JpaChoreCompletionStatsQuery::class)
class JpaChoreCompletionStatsQueryTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var recordRepository: JpaChoreCompletionRecordRepository

    @Autowired
    lateinit var statsQuery: JpaChoreCompletionStatsQuery

    @Autowired
    lateinit var em: TestEntityManager

    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `기간 안의 기록만 멤버별로 집계된다`() {
        val member1 = MemberId.generate()
        val member2 = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()

        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 8, 10).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 8, 15).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member2, LocalDate.of(2026, 8, 12).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 7, 31).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member2, LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 2
        result.first { it.memberId == member1 }.count shouldBe 2L
        result.first { it.memberId == member2 }.count shouldBe 1L
    }

    @Test
    fun `to 날짜 당일 기록도 포함된다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 8, 31).atTime(23, 0).atZone(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first().count shouldBe 1L
    }

    @Test
    fun `범위 밖에만 기록이 있으면 빈 목록을 반환한다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 0
    }

    @Test
    fun `from 날짜 시작 시각의 기록도 포함된다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 8, 1).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first().count shouldBe 1L
    }

    @Test
    fun `from과 to가 같으면 그 하루만 집계한다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 8, 10).atTime(12, 0).atZone(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 8, 11).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10))

        result shouldHaveSize 1
        result.first().count shouldBe 1L
    }
}
