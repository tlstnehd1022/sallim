package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate

@Service
class ChoreStatsService(private val query: ChoreCompletionStatsQuery) {
    @Transactional(readOnly = true)
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
        return query.countByMember(from, to)
    }
}
