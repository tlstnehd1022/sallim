package sallim.chore.domain

class Room(
    val id: RoomId,
    val name: String
) {
    init {
        require(name.isNotBlank()) { "room name must not be blank" }
    }
}
