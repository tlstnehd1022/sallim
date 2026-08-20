package sallim.chore.domain

interface ChoreInstanceRepository {
    fun save(choreInstance: ChoreInstance): ChoreInstance
    fun findById(id: ChoreInstanceId): ChoreInstance?
}
