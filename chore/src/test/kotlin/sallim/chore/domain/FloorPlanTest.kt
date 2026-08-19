package sallim.chore.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize

class FloorPlanTest : FunSpec({
    val roomId = RoomId.generate()

    test("범위 안 배치는 그대로 생성된다") {
        val floorPlan = FloorPlan.of(listOf(RoomPlacement(roomId, x = 26, y = 38, w = 74, h = 50, z = 1)))
        floorPlan.placements shouldHaveSize 1
    }

    test("w가 8 미만이면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 0, w = 7, h = 10, z = 1)))
        }
    }

    test("w가 100-x를 넘으면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 80, y = 0, w = 21, h = 10, z = 1)))
        }
    }

    test("h가 6 미만이면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 0, w = 10, h = 5, z = 1)))
        }
    }

    test("h가 100-y를 넘으면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 90, w = 10, h = 11, z = 1)))
        }
    }
})
