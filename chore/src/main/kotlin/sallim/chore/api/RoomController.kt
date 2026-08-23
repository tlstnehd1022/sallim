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
import sallim.chore.application.RoomService
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import java.util.UUID

data class RoomRequest(val name: String, val x: Int, val y: Int, val w: Int, val h: Int, val z: Int)
data class RoomResponse(val id: UUID, val name: String, val x: Int, val y: Int, val w: Int, val h: Int, val z: Int)

@RestController
@RequestMapping("/api/rooms")
class RoomController(private val roomService: RoomService) {

    @GetMapping
    fun list(): List<RoomResponse> = roomService.list().map { (room, placement) -> room.toResponse(placement) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: RoomRequest): RoomResponse {
        val room = roomService.create(request.name, request.x, request.y, request.w, request.h, request.z)
        return room.toResponse(RoomPlacement(room.id, request.x, request.y, request.w, request.h, request.z))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: RoomRequest): RoomResponse {
        val room = roomService.update(RoomId(id), request.name, request.x, request.y, request.w, request.h, request.z)
        return room.toResponse(RoomPlacement(room.id, request.x, request.y, request.w, request.h, request.z))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        roomService.delete(RoomId(id))
    }

    private fun Room.toResponse(placement: RoomPlacement) =
        RoomResponse(id.value, name, placement.x, placement.y, placement.w, placement.h, placement.z)
}
