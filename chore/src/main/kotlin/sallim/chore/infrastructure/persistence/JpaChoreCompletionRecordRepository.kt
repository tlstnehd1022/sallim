package sallim.chore.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreCompletionRecordRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.util.UUID

@Repository
class JpaChoreCompletionRecordRepository(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionRecordRepository {
    companion object {
        private val logger = LoggerFactory.getLogger(JpaChoreCompletionRecordRepository::class.java)
    }

    override fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) {
        val instanceIdStr = choreInstanceId.value.toString()
        if (jpaRepository.existsByChoreInstanceId(instanceIdStr)) {
            logger.info("chore completion record already exists for instance {}, skipping (Kafka redelivery)", choreInstanceId)
            return
        }
        jpaRepository.save(
            ChoreCompletionRecordEntity(
                id = UUID.randomUUID().toString(),
                choreInstanceId = instanceIdStr,
                choreDefinitionId = choreDefinitionId.value.toString(),
                completedBy = completedBy.value.toString(),
                completedAt = completedAt
            )
        )
    }
}
