package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class ChoreInstanceTest : FunSpec({
    val choreDefinitionId = ChoreDefinitionId.generate()
    val scheduledDate = LocalDate.of(2026, 8, 19)

    test("schedule 직후에는 미완료 상태다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)

        instance.completed shouldBe false
        instance.completedBy shouldBe null
        instance.completedAt shouldBe null
    }

    test("complete 호출 시 완료 처리되고 ChoreCompletedEvent가 발행된다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        val memberId = MemberId.generate()

        instance.complete(memberId)

        instance.completed shouldBe true
        instance.completedBy shouldBe memberId
        instance.domainEvents shouldHaveSize 1
        val event = instance.domainEvents.first() as ChoreCompletedEvent
        event.choreInstanceId shouldBe instance.id
        event.choreDefinitionId shouldBe choreDefinitionId
        event.completedBy shouldBe memberId
    }

    test("이미 완료된 인스턴스는 다시 완료할 수 없다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        instance.complete(MemberId.generate())

        io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
            instance.complete(MemberId.generate())
        }
    }

    test("reconstitute는 저장된 상태를 그대로 복원하고 이벤트를 발행하지 않는다") {
        val memberId = MemberId.generate()
        val completedAt = Instant.now()

        val instance = ChoreInstance.reconstitute(
            id = ChoreInstanceId.generate(),
            choreDefinitionId = choreDefinitionId,
            scheduledDate = scheduledDate,
            completed = true,
            completedBy = memberId,
            completedAt = completedAt
        )

        instance.completed shouldBe true
        instance.completedBy shouldBe memberId
        instance.completedAt shouldBe completedAt
        instance.domainEvents shouldHaveSize 0
    }
})
