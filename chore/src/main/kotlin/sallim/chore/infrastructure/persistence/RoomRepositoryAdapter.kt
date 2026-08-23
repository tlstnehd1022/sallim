package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository
import java.util.UUID

@Repository
class RoomRepositoryAdapter(
    private val jpaRepository: RoomJpaRepository
) : RoomRepository {

    override fun save(room: Room, placement: RoomPlacement): Room {
        jpaRepository.save(
            RoomEntity(
                id = room.id.value.toString(),
                name = room.name,
                x = placement.x,
                y = placement.y,
                w = placement.w,
                h = placement.h,
                z = placement.z
            )
        )
        return room
    }

    override fun findAll(): List<Pair<Room, RoomPlacement>> =
        jpaRepository.findAll().map { it.toDomain() }

    override fun findById(id: RoomId): Pair<Room, RoomPlacement>? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun deleteById(id: RoomId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun RoomEntity.toDomain(): Pair<Room, RoomPlacement> {
        val roomId = RoomId(UUID.fromString(id))
        return Room(roomId, name) to RoomPlacement(roomId, x, y, w, h, z)
    }
}
