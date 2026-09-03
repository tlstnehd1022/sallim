package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreDefinitionRepositoryAdapter::class)
class ChoreDefinitionRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreDefinitionRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

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
        em.flush()
        em.clear()

        val found = adapter.findAll().associateBy { it.id }
        found[daily.id]!!.recurrence shouldBe Daily
        found[weekly.id]!!.recurrence shouldBe WeeklyNTimes(2)
        found[monthly.id]!!.recurrence shouldBe Monthly
    }

    @Test
    fun `howToSteps 순서가 저장 순서 그대로 보존된다`() {
        val definition = choreDefinition(steps = listOf("첫번째", "두번째", "세번째"))

        adapter.save(definition)
        em.flush()
        em.clear()

        val found = adapter.findAll().first { it.id == definition.id }
        found.howToSteps shouldBe listOf("첫번째", "두번째", "세번째")
    }

    @Test
    fun `저장한 정의를 id로 조회하면 값이 같다`() {
        val definition = choreDefinition()
        adapter.save(definition)
        em.flush()
        em.clear()

        val found = adapter.findById(definition.id)

        found.shouldNotBeNull()
        found.id shouldBe definition.id
        found.label shouldBe definition.label
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(ChoreDefinitionId.generate()) shouldBe null
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val definition = choreDefinition()
        adapter.save(definition)
        em.flush()
        em.clear()

        adapter.deleteById(definition.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }

    @Test
    fun `같은 id로 다시 저장하면 howToSteps 목록 길이가 바뀌어도 갱신된다`() {
        val definition = choreDefinition(steps = listOf("첫번째", "두번째", "세번째"))
        adapter.save(definition)
        em.flush()
        em.clear()

        val updated = ChoreDefinition(
            id = definition.id,
            roomId = definition.roomId,
            label = "설거지-수정",
            assigneeId = definition.assigneeId,
            recurrence = WeeklyNTimes(3),
            howToSteps = listOf("한 단계만"),
            videoQuery = "새 영상"
        )
        adapter.save(updated)
        em.flush()
        em.clear()

        val found = adapter.findById(definition.id)
        found.shouldNotBeNull()
        found.label shouldBe "설거지-수정"
        found.recurrence shouldBe WeeklyNTimes(3)
        found.howToSteps shouldBe listOf("한 단계만")
    }
}
