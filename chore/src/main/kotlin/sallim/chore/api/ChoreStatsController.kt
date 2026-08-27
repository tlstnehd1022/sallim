package sallim.chore.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreStatsService
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate
import java.util.UUID

data class MemberCompletionCountResponse(val memberId: UUID, val count: Long)

@RestController
@RequestMapping("/api/chore-stats")
class ChoreStatsController(private val statsService: ChoreStatsService) {

    @GetMapping
    fun countByMember(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): List<MemberCompletionCountResponse> =
        statsService.countByMember(from, to).map { it.toResponse() }

    private fun MemberCompletionCount.toResponse() = MemberCompletionCountResponse(memberId.value, count)
}
