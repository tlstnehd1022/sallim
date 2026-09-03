package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import sallim.common.domain.Daily
import java.time.LocalDate

class RoomServiceTest : FunSpec({
    fun newService(): Triple<RoomService, FakeRoomRepository, Pair<FakeChoreDefinitionRepository, FakeChoreInstanceRepository>> {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        return Triple(RoomService(rooms, definitions, instances), rooms, definitions to instances)
    }

    test("방을 생성하면 조회된다") {
        val (service) = newService()

        val room = service.create("거실", 26, 38, 74, 50, 1)

        service.list() shouldHaveSize 1
        service.list().first().first.id shouldBe room.id
    }

    test("범위를 벗어난 좌표로 생성하면 IllegalArgumentException") {
        val (service) = newService()

        shouldThrow<IllegalArgumentException> { service.create("거실", 95, 0, 50, 10, 1) }
    }

    test("존재하지 않는 방을 수정하면 NotFoundException") {
        val (service) = newService()

        shouldThrow<NotFoundException> { service.update(RoomId.generate(), "거실", 0, 0, 10, 10, 1) }
    }

    test("방을 삭제하면 그 방의 할 일 정의와 인스턴스도 함께 삭제된다") {
        val (service, rooms, defAndInst) = newService()
        val (definitions, instances) = defAndInst
        val room = service.create("거실", 26, 38, 74, 50, 1)
        val definition = ChoreDefinition(
            ChoreDefinitionId.generate(), room.id, "청소", MemberId.generate(), Daily, listOf("단계1"), "영상"
        )
        definitions.save(definition)
        val instance = ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 20))
        instances.save(instance)

        service.delete(room.id)

        rooms.findAll() shouldHaveSize 0
        definitions.findAll() shouldHaveSize 0
        instances.findAll() shouldHaveSize 0
    }
})
