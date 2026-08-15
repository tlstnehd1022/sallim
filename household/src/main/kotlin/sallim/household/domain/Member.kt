package sallim.household.domain

class Member(
    val id: MemberId,
    val householdId: HouseholdId,
    val displayName: String,
    val role: MemberRole
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}
