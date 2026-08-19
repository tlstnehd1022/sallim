package sallim.chore.domain

class FloorPlan private constructor(val placements: List<RoomPlacement>) {
    companion object {
        fun of(placements: List<RoomPlacement>): FloorPlan {
            placements.forEach { p ->
                require(p.w in 8..(100 - p.x)) { "w must be within [8, ${100 - p.x}]: ${p.w}" }
                require(p.h in 6..(100 - p.y)) { "h must be within [6, ${100 - p.y}]: ${p.h}" }
            }
            return FloorPlan(placements)
        }
    }
}
