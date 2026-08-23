package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.CleanlinessScore
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomRepository
import java.time.LocalDate

@Service
class CleanlinessService(
    private val roomRepository: RoomRepository,
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun scoresForAllRooms(referenceDate: LocalDate = LocalDate.now()): List<RoomCleanliness> {
        val definitionsByRoom = choreDefinitionRepository.findAll().groupBy { it.roomId }
        val instancesByDefinition = choreInstanceRepository.findAll().groupBy { it.choreDefinitionId }
        return roomRepository.findAll().map { (room, _) ->
            val instances = (definitionsByRoom[room.id] ?: emptyList())
                .flatMap { instancesByDefinition[it.id] ?: emptyList() }
            RoomCleanliness(room.id, CleanlinessScore.compute(instances, referenceDate))
        }
    }
}

data class RoomCleanliness(val roomId: RoomId, val score: Double)
