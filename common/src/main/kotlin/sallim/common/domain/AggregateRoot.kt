package sallim.common.domain

abstract class AggregateRoot<ID : Identifier<*>> {
    abstract val id: ID

    private val _domainEvents = mutableListOf<DomainEvent>()
    val domainEvents: List<DomainEvent> get() = _domainEvents.toList()

    protected fun registerEvent(event: DomainEvent) {
        _domainEvents.add(event)
    }

    fun clearEvents() {
        _domainEvents.clear()
    }
}
