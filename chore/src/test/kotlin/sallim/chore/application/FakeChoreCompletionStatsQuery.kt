package sallim.chore.application

import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate

class FakeChoreCompletionStatsQuery : ChoreCompletionStatsQuery {
    var result: List<MemberCompletionCount> = emptyList()
    var lastFrom: LocalDate? = null
    var lastTo: LocalDate? = null

    override fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        lastFrom = from
        lastTo = to
        return result
    }
}
