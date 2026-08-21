package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.util.UUID

@Repository
class ChoreInstanceRepositoryAdapter(
    private val jpaRepository: ChoreInstanceJpaRepository
) : ChoreInstanceRepository {

    override fun save(choreInstance: ChoreInstance): ChoreInstance {
        jpaRepository.save(
            ChoreInstanceEntity(
                id = choreInstance.id.value.toString(),
                choreDefinitionId = choreInstance.choreDefinitionId.value.toString(),
                scheduledDate = choreInstance.scheduledDate,
                completed = choreInstance.completed,
                completedBy = choreInstance.completedBy?.value?.toString(),
                completedAt = choreInstance.completedAt
            )
        )
        return choreInstance
    }

    override fun findById(id: ChoreInstanceId): ChoreInstance? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    private fun ChoreInstanceEntity.toDomain(): ChoreInstance = ChoreInstance.reconstitute(
        id = ChoreInstanceId(UUID.fromString(id)),
        choreDefinitionId = ChoreDefinitionId(UUID.fromString(choreDefinitionId)),
        scheduledDate = scheduledDate,
        completed = completed,
        completedBy = completedBy?.let { MemberId(UUID.fromString(it)) },
        completedAt = completedAt
    )
}
