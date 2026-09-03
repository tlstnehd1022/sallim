package sallim.chore.application

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import sallim.common.domain.Daily
import java.time.LocalDate

class CleanlinessServiceTest : FunSpec({
    test("방마다 하나의 청결도 점수를 계산한다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = ChoreDefinition(
            sallim.chore.domain.ChoreDefinitionId.generate(), room.id, "청소",
            MemberId.generate(), Daily, listOf("단계1"), "영상"
        )
        definitions.save(definition)
        instances.save(ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 10)))
        val service = CleanlinessService(rooms, definitions, instances)

        val scores = service.scoresForAllRooms(referenceDate = LocalDate.of(2026, 8, 20))

        scores shouldHaveSize 1
        scores.first().roomId shouldBe room.id
        (scores.first().score > 0) shouldBe true
    }

    test("할 일이 없는 방은 청결도 0이다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        roomService.create("거실", 26, 38, 74, 50, 1)
        val service = CleanlinessService(rooms, definitions, instances)

        val scores = service.scoresForAllRooms(referenceDate = LocalDate.of(2026, 8, 20))

        scores.first().score shouldBe 0.0
    }
})
