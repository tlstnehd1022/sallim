package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String> {
    fun existsByChoreInstanceId(choreInstanceId: String): Boolean
}
