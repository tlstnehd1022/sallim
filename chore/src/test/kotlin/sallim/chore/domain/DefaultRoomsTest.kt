package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class DefaultRoomsTest : FunSpec({
    test("방은 10개다") {
        DefaultRooms.rooms shouldHaveSize 10
    }

    test("평면도 배치는 방 개수와 같다") {
        DefaultRooms.floorPlan.placements shouldHaveSize 10
    }

    test("집안일 정의는 18개다") {
        DefaultRooms.choreDefinitions shouldHaveSize 18
    }

    test("모든 집안일은 실재하는 방을 참조한다") {
        val roomIds = DefaultRooms.rooms.map { it.id }.toSet()
        DefaultRooms.choreDefinitions.forEach { definition ->
            (definition.roomId in roomIds) shouldBe true
        }
    }
})
