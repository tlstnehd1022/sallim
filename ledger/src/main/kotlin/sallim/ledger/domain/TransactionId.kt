package sallim.ledger.domain

import sallim.common.domain.Identifier
import java.util.UUID

class TransactionId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): TransactionId = TransactionId(UUID.randomUUID())
    }
}
