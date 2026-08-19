package sallim.chore.domain

data class RoomPlacement(
    val roomId: RoomId,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val z: Int
)
