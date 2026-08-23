package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomRepository

@Service
class ChoreDefinitionService(
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val roomRepository: RoomRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun list(): List<ChoreDefinition> = choreDefinitionRepository.findAll()

    fun create(
        label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = ChoreDefinition(
            ChoreDefinitionId.generate(), roomId, label, assigneeId, recurrence, howToSteps, videoQuery
        )
        return choreDefinitionRepository.save(definition)
    }

    fun update(
        id: ChoreDefinitionId, label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = ChoreDefinition(id, roomId, label, assigneeId, recurrence, howToSteps, videoQuery)
        return choreDefinitionRepository.save(definition)
    }

    fun delete(id: ChoreDefinitionId) {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        choreInstanceRepository.findAll()
            .filter { it.choreDefinitionId == id }
            .forEach { choreInstanceRepository.deleteById(it.id) }
        choreDefinitionRepository.deleteById(id)
    }
}
