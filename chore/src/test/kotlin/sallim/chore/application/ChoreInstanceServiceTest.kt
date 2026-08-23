package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreInstanceServiceTest : FunSpec({
    test("날짜로 필터링해 조회한다") {
        val instances = FakeChoreInstanceRepository()
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21)))
        val service = ChoreInstanceService(instances)

        service.listByDate(LocalDate.of(2026, 8, 20)) shouldHaveSize 1
    }

    test("완료 처리하면 저장된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()

        val completed = service.complete(instance.id, member)

        completed.completed shouldBe true
        instances.findById(instance.id)!!.completed shouldBe true
    }

    test("존재하지 않는 인스턴스를 완료 처리하면 NotFoundException") {
        val service = ChoreInstanceService(FakeChoreInstanceRepository())

        shouldThrow<NotFoundException> { service.complete(ChoreInstanceId.generate(), MemberId.generate()) }
    }

    test("이미 완료된 인스턴스를 다시 완료 처리하면 IllegalStateException") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()
        service.complete(instance.id, member)

        shouldThrow<IllegalStateException> { service.complete(instance.id, member) }
    }
})
