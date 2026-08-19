package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class ChoreInstanceId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): ChoreInstanceId = ChoreInstanceId(UUID.randomUUID())
    }
}
