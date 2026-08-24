package sallim.chore.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.ChoreInstanceService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreInstanceSchedulerTest : FunSpec({
    test("실행하면 모든 정의를 조회해 소급 인스턴스를 생성한다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        val definitionService = ChoreDefinitionService(definitions, rooms, instances)
        val instanceService = ChoreInstanceService(instances)
        val scheduler = ChoreInstanceScheduler(definitionService, instanceService)

        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = definitionService.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        val today = LocalDate.now()
        // create()가 이미 오늘 인스턴스를 만들어뒀으니, 스케줄러가 소급할 게 있도록 지우고 3일 전 인스턴스로 되돌린다
        instances.deleteById(instances.findAll().first { it.choreDefinitionId == definition.id }.id)
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(3)))

        scheduler.generateDueInstances()

        instances.findAll().filter { it.choreDefinitionId == definition.id } shouldHaveSize 4  // -3(기존) + -2,-1,0(소급)
    }
})
