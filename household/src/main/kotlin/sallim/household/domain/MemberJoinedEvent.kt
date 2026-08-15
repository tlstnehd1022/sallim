package sallim.household.domain

import sallim.common.domain.DomainEvent
import java.time.Instant

data class MemberJoinedEvent(
    val householdId: HouseholdId,
    val memberId: MemberId,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
