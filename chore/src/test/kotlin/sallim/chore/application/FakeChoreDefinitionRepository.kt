package sallim.chore.application

import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository

class FakeChoreDefinitionRepository : ChoreDefinitionRepository {
    private val store = mutableMapOf<ChoreDefinitionId, ChoreDefinition>()

    override fun save(choreDefinition: ChoreDefinition): ChoreDefinition {
        store[choreDefinition.id] = choreDefinition
        return choreDefinition
    }

    override fun findAll(): List<ChoreDefinition> = store.values.toList()

    override fun findById(id: ChoreDefinitionId): ChoreDefinition? = store[id]

    override fun deleteById(id: ChoreDefinitionId) {
        store.remove(id)
    }
}
