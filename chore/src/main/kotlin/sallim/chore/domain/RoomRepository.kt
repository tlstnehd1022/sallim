package sallim.chore.domain

interface RoomRepository {
    fun save(room: Room, placement: RoomPlacement): Room
    fun findAll(): List<Pair<Room, RoomPlacement>>
    fun findById(id: RoomId): Pair<Room, RoomPlacement>?
    fun deleteById(id: RoomId)
}
