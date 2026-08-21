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
        jpaRepository.findAll().map { entity ->
            val roomId = RoomId(UUID.fromString(entity.id))
            Room(roomId, entity.name) to RoomPlacement(roomId, entity.x, entity.y, entity.w, entity.h, entity.z)
        }
}
