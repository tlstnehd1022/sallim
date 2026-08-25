package sallim.chore.infrastructure.messaging

import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

class FakeChoreEventProducer : ChoreEventProducer {
    val published = mutableListOf<ChoreCompletedEvent>()
    var shouldThrow: Boolean = false

    override fun publish(event: ChoreCompletedEvent) {
        if (shouldThrow) throw RuntimeException("simulated Kafka publish failure")
        published.add(event)
    }
}
