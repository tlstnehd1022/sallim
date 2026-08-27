package sallim.chore.domain

import java.time.LocalDate

interface ChoreCompletionStatsQuery {
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount>
}

data class MemberCompletionCount(val memberId: MemberId, val count: Long)
