package sallim.household.domain

interface HouseholdRepository {
    fun save(household: Household): Household
    fun findById(id: HouseholdId): Household?
}
