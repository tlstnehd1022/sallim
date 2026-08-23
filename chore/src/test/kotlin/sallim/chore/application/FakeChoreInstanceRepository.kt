package sallim.chore.application

import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository

class FakeChoreInstanceRepository : ChoreInstanceRepository {
    private val store = mutableMapOf<ChoreInstanceId, ChoreInstance>()

    override fun save(choreInstance: ChoreInstance): ChoreInstance {
        store[choreInstance.id] = choreInstance
        return choreInstance
    }

    override fun findById(id: ChoreInstanceId): ChoreInstance? = store[id]

    override fun findAll(): List<ChoreInstance> = store.values.toList()

    override fun deleteById(id: ChoreInstanceId) {
        store.remove(id)
    }
}
