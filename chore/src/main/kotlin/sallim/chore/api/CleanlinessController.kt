package sallim.chore.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.CleanlinessService
import sallim.chore.application.RoomCleanliness
import java.util.UUID

data class CleanlinessResponse(val roomId: UUID, val score: Double)

@RestController
@RequestMapping("/api/cleanliness")
class CleanlinessController(private val cleanlinessService: CleanlinessService) {

    @GetMapping
    fun list(): List<CleanlinessResponse> =
        cleanlinessService.scoresForAllRooms().map { it.toResponse() }

    private fun RoomCleanliness.toResponse() = CleanlinessResponse(roomId.value, score)
}
