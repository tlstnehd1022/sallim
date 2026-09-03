package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.ApplicationEventPublisher
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDate

class ChoreInstanceServiceTest : FunSpec({
    fun choreDefinition(recurrence: RecurrencePolicy): ChoreDefinition = ChoreDefinition(
        ChoreDefinitionId.generate(), RoomId.generate(), "청소", MemberId.generate(), recurrence, listOf("단계1"), "영상"
    )

    test("날짜로 필터링해 조회한다") {
        val instances = FakeChoreInstanceRepository()
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21)))
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })

        service.listByDate(LocalDate.of(2026, 8, 20)) shouldHaveSize 1
    }

    test("완료 처리하면 저장된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val member = MemberId.generate()

        val completed = service.complete(instance.id, member)

        completed.completed shouldBe true
        instances.findById(instance.id)!!.completed shouldBe true
    }

    test("존재하지 않는 인스턴스를 완료 처리하면 NotFoundException") {
        val service = ChoreInstanceService(FakeChoreInstanceRepository(), ApplicationEventPublisher { })

        shouldThrow<NotFoundException> { service.complete(ChoreInstanceId.generate(), MemberId.generate()) }
    }

    test("이미 완료된 인스턴스를 다시 완료 처리하면 IllegalStateException") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val member = MemberId.generate()
        service.complete(instance.id, member)

        shouldThrow<IllegalStateException> { service.complete(instance.id, member) }
    }

    test("최근 인스턴스로부터 오늘까지 매일 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(3)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 3
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(2), today.minusDays(1), today)
    }

    test("WeeklyNTimes도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val definition = choreDefinition(WeeklyNTimes(2))  // nextOccurrence는 7/2=3일 간격
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(7)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(4), today.minusDays(1))
    }

    test("Monthly도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val definition = choreDefinition(Monthly)
        val today = LocalDate.of(2026, 6, 15)
        instances.save(ChoreInstance.schedule(definition.id, today.minusMonths(2)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusMonths(1), today)
    }

    test("이미 오늘까지 인스턴스가 있으면 아무것도 생성하지 않는다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today))

        service.generateDueInstances(listOf(definition), today).shouldBeEmpty()
    }

    test("인스턴스가 하나도 없는 정의는 건너뛴다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { })
        val definition = choreDefinition(Daily)

        service.generateDueInstances(listOf(definition), LocalDate.now()).shouldBeEmpty()
    }

    test("완료 처리하면 ChoreCompletedEvent가 발행된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val publishedEvents = mutableListOf<Any>()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { publishedEvents.add(it) })
        val member = MemberId.generate()

        service.complete(instance.id, member)

        publishedEvents shouldHaveSize 1
        val event = publishedEvents.first() as ChoreCompletedEvent
        event.choreInstanceId shouldBe instance.id
        event.completedBy shouldBe member
    }
})
