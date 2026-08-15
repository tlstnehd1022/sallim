package sallim.common.domain

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
