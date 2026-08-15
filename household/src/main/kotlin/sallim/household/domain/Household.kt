package sallim.household.domain

import sallim.common.domain.AggregateRoot
import java.time.Instant

class Household private constructor(
    override val id: HouseholdId,
    val name: String,
    val createdAt: Instant,
    private val members: MutableList<Member>
) : AggregateRoot<HouseholdId>() {

    val memberList: List<Member> get() = members.toList()

    fun addMember(displayName: String, role: MemberRole): Member {
        val member = Member(
            id = MemberId.generate(),
            householdId = id,
            displayName = displayName,
            role = role
        )
        members.add(member)
        registerEvent(MemberJoinedEvent(householdId = id, memberId = member.id))
        return member
    }

    companion object {
        fun create(name: String, ownerDisplayName: String): Household {
            require(name.isNotBlank()) { "household name must not be blank" }
            val household = Household(
                id = HouseholdId.generate(),
                name = name,
                createdAt = Instant.now(),
                members = mutableListOf()
            )
            household.addMember(ownerDisplayName, MemberRole.OWNER)
            return household
        }
    }
}
