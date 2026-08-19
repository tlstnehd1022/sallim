package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class ChoreDefinitionId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): ChoreDefinitionId = ChoreDefinitionId(UUID.randomUUID())
    }
}
