package sallim.chore.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreInstanceService
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CompleteRequest(val completedBy: UUID)

data class ChoreInstanceResponse(
    val id: UUID,
    val choreDefinitionId: UUID,
    val scheduledDate: LocalDate,
    val completed: Boolean,
    val completedBy: UUID?,
    val completedAt: Instant?
)

@RestController
@RequestMapping("/api/chore-instances")
class ChoreInstanceController(private val choreInstanceService: ChoreInstanceService) {

    @GetMapping
    fun listByDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate): List<ChoreInstanceResponse> =
        choreInstanceService.listByDate(date).map { it.toResponse() }

    @PostMapping("/{id}/complete")
    fun complete(@PathVariable id: UUID, @RequestBody request: CompleteRequest): ChoreInstanceResponse =
        choreInstanceService.complete(ChoreInstanceId(id), MemberId(request.completedBy)).toResponse()

    private fun ChoreInstance.toResponse() =
        ChoreInstanceResponse(id.value, choreDefinitionId.value, scheduledDate, completed, completedBy?.value, completedAt)
}
