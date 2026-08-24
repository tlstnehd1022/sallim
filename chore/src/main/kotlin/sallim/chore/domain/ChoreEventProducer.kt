package sallim.chore.domain

interface ChoreEventProducer {
    fun publish(event: ChoreCompletedEvent)
}
