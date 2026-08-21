package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "room")
class RoomEntity(
    @Id
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val z: Int
)
