package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import java.time.LocalDate

class ChoreDefinitionServiceTest : FunSpec({
    fun newService(): Triple<ChoreDefinitionService, FakeRoomRepository, Pair<FakeChoreDefinitionRepository, FakeChoreInstanceRepository>> {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        return Triple(ChoreDefinitionService(definitions, rooms, instances), rooms, definitions to instances)
    }

    test("존재하는 방에 할 일 정의를 생성할 수 있다") {
        val (service, rooms) = newService()
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        val definition = service.create(
            "청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상"
        )

        service.list() shouldHaveSize 1
        definition.roomId shouldBe room.id
    }

    test("존재하지 않는 방을 참조하면 IllegalArgumentException") {
        val (service) = newService()

        shouldThrow<IllegalArgumentException> {
            service.create("청소", RoomId.generate(), MemberId.generate(), Daily, listOf("단계1"), "영상")
        }
    }

    test("존재하지 않는 정의를 수정하면 NotFoundException") {
        val (service, rooms) = newService()
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        shouldThrow<NotFoundException> {
            service.update(ChoreDefinitionId.generate(), "청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        }
    }

    test("정의를 삭제하면 그 정의의 인스턴스도 함께 삭제된다") {
        val (service, rooms, defAndInst) = newService()
        val (definitions, instances) = defAndInst
        val roomService = RoomService(rooms, definitions, instances)
        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = service.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        instances.save(ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 20)))

        service.delete(definition.id)

        service.list() shouldHaveSize 0
        instances.findAll() shouldHaveSize 0
    }

    test("생성 시 오늘 날짜 인스턴스가 함께 생성된다") {
        val (service, rooms, defAndInst) = newService()
        val (_, instances) = defAndInst
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        val definition = service.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")

        val created = instances.findAll().filter { it.choreDefinitionId == definition.id }
        created shouldHaveSize 1
        created.first().scheduledDate shouldBe LocalDate.now()
    }
})
