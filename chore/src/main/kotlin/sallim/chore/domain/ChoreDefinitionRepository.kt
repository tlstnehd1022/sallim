package sallim.chore.domain

interface ChoreDefinitionRepository {
    fun save(choreDefinition: ChoreDefinition): ChoreDefinition
    fun findAll(): List<ChoreDefinition>
    fun findById(id: ChoreDefinitionId): ChoreDefinition?
    fun deleteById(id: ChoreDefinitionId)
}
