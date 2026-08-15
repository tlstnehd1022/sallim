package sallim.household.domain

import sallim.common.domain.Identifier
import java.util.UUID

class HouseholdId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): HouseholdId = HouseholdId(UUID.randomUUID())
    }
}
