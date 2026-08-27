package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String> {
    fun existsByChoreInstanceId(choreInstanceId: String): Boolean

    @Query(
        "SELECT r.completedBy AS memberId, COUNT(r) AS count " +
            "FROM ChoreCompletionRecordEntity r " +
            "WHERE r.completedAt BETWEEN :from AND :to " +
            "GROUP BY r.completedBy"
    )
    fun countByMemberBetween(@Param("from") from: Instant, @Param("to") to: Instant): List<MemberCountProjection>
}

interface MemberCountProjection {
    val memberId: String
    val count: Long
}
