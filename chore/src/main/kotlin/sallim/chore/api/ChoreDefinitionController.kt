package sallim.chore.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes
import java.util.UUID

data class RecurrenceDto(val type: String, val times: Int?)

data class ChoreDefinitionRequest(
    val label: String,
    val roomId: UUID,
    val assigneeId: UUID,
    val recurrence: RecurrenceDto,
    val howToSteps: List<String>,
    val videoQuery: String
)

data class ChoreDefinitionResponse(
    val id: UUID,
    val roomId: UUID,
    val label: String,
    val assigneeId: UUID,
    val recurrence: RecurrenceDto,
    val howToSteps: List<String>,
    val videoQuery: String
)

@RestController
@RequestMapping("/api/chore-definitions")
class ChoreDefinitionController(private val choreDefinitionService: ChoreDefinitionService) {

    @GetMapping
    fun list(): List<ChoreDefinitionResponse> = choreDefinitionService.list().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ChoreDefinitionRequest): ChoreDefinitionResponse =
        choreDefinitionService.create(
            request.label, RoomId(request.roomId), MemberId(request.assigneeId),
            request.recurrence.toDomain(), request.howToSteps, request.videoQuery
        ).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: ChoreDefinitionRequest): ChoreDefinitionResponse =
        choreDefinitionService.update(
            ChoreDefinitionId(id), request.label, RoomId(request.roomId), MemberId(request.assigneeId),
            request.recurrence.toDomain(), request.howToSteps, request.videoQuery
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        choreDefinitionService.delete(ChoreDefinitionId(id))
    }

    private fun ChoreDefinition.toResponse() =
        ChoreDefinitionResponse(id.value, roomId.value, label, assigneeId.value, recurrence.toDto(), howToSteps, videoQuery)

    private fun RecurrenceDto.toDomain(): RecurrencePolicy = when (type) {
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "times is required for WEEKLY_N_TIMES" })
        "MONTHLY" -> Monthly
        else -> throw IllegalArgumentException("unknown recurrence type: $type")
    }

    private fun RecurrencePolicy.toDto(): RecurrenceDto = when (this) {
        is Daily -> RecurrenceDto("DAILY", null)
        is WeeklyNTimes -> RecurrenceDto("WEEKLY_N_TIMES", times)
        is Monthly -> RecurrenceDto("MONTHLY", null)
    }
}
