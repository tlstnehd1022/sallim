package sallim.chore.application

import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository

class FakeRoomRepository : RoomRepository {
    private val store = mutableMapOf<RoomId, Pair<Room, RoomPlacement>>()

    override fun save(room: Room, placement: RoomPlacement): Room {
        store[room.id] = room to placement
        return room
    }

    override fun findAll(): List<Pair<Room, RoomPlacement>> = store.values.toList()

    override fun findById(id: RoomId): Pair<Room, RoomPlacement>? = store[id]

    override fun deleteById(id: RoomId) {
        store.remove(id)
    }
}
