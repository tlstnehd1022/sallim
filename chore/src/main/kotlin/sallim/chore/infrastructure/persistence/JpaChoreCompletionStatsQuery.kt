package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class JpaChoreCompletionStatsQuery(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionStatsQuery {
    override fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        val zone = ZoneId.of("Asia/Seoul")
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(zone).toInstant()
        return jpaRepository.countByMemberBetween(fromInstant, toInstant)
            .map { MemberCompletionCount(MemberId(UUID.fromString(it.memberId)), it.count) }
    }
}
