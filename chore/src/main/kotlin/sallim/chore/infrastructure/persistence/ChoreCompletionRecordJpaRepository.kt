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
            "WHERE r.completedAt >= :fromInclusive AND r.completedAt < :toExclusive " +
            "GROUP BY r.completedBy"
    )
    fun countByMemberCompletedAtInRange(
        @Param("fromInclusive") fromInclusive: Instant,
        @Param("toExclusive") toExclusive: Instant
    ): List<MemberCountProjection>
}

interface MemberCountProjection {
    val memberId: String
    val count: Long
}
