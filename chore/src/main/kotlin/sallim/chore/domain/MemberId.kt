package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
