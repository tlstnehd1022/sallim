package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class RoomId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): RoomId = RoomId(UUID.randomUUID())
    }
}
