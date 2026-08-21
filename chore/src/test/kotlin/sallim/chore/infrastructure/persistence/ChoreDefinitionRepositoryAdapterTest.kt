package sallim.chore.infrastructure.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreDefinitionRepositoryAdapter::class)
class ChoreDefinitionRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreDefinitionRepositoryAdapter

    private fun choreDefinition(
        recurrence: RecurrencePolicy = Daily,
        steps: List<String> = listOf("헹구기")
    ) = ChoreDefinition(
        id = ChoreDefinitionId.generate(),
        roomId = RoomId.generate(),
        label = "설거지",
        assigneeId = MemberId.generate(),
        recurrence = recurrence,
        howToSteps = steps,
        videoQuery = "설거지 순서 팁"
    )

    @Test
    fun `Daily WeeklyNTimes Monthly 세 종류 모두 저장 후 그대로 읽힌다`() {
        val daily = choreDefinition(recurrence = Daily)
        val weekly = choreDefinition(recurrence = WeeklyNTimes(2))
        val monthly = choreDefinition(recurrence = Monthly)

        adapter.save(daily)
        adapter.save(weekly)
        adapter.save(monthly)

        val found = adapter.findAll().associateBy { it.id }
        found[daily.id]!!.recurrence shouldBe Daily
        found[weekly.id]!!.recurrence shouldBe WeeklyNTimes(2)
        found[monthly.id]!!.recurrence shouldBe Monthly
    }

    @Test
    fun `howToSteps 순서가 저장 순서 그대로 보존된다`() {
        val definition = choreDefinition(steps = listOf("첫번째", "두번째", "세번째"))

        adapter.save(definition)

        val found = adapter.findAll().first { it.id == definition.id }
        found.howToSteps shouldBe listOf("첫번째", "두번째", "세번째")
    }
}
