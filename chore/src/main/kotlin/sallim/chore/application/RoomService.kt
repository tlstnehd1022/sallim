package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.FloorPlan
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    @Transactional(readOnly = true)
    fun list(): List<Pair<Room, RoomPlacement>> = roomRepository.findAll()

    @Transactional
    fun create(name: String, x: Int, y: Int, w: Int, h: Int, z: Int): Room {
        val room = Room(RoomId.generate(), name)
        val placement = RoomPlacement(room.id, x, y, w, h, z)
        FloorPlan.of(listOf(placement))
        return roomRepository.save(room, placement)
    }

    @Transactional
    fun update(id: RoomId, name: String, x: Int, y: Int, w: Int, h: Int, z: Int): Room {
        roomRepository.findById(id) ?: throw NotFoundException("room not found: $id")
        val room = Room(id, name)
        val placement = RoomPlacement(id, x, y, w, h, z)
        FloorPlan.of(listOf(placement))
        return roomRepository.save(room, placement)
    }

    @Transactional
    fun delete(id: RoomId) {
        roomRepository.findById(id) ?: throw NotFoundException("room not found: $id")
        val definitionIds = choreDefinitionRepository.findAll()
            .filter { it.roomId == id }
            .map { it.id }
        choreInstanceRepository.findAll()
            .filter { it.choreDefinitionId in definitionIds }
            .forEach { choreInstanceRepository.deleteById(it.id) }
        definitionIds.forEach { choreDefinitionRepository.deleteById(it) }
        roomRepository.deleteById(id)
    }
}
