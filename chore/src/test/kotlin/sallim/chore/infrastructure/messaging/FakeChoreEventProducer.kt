package sallim.chore.infrastructure.messaging

import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

class FakeChoreEventProducer : ChoreEventProducer {
    val published = mutableListOf<ChoreCompletedEvent>()

    override fun publish(event: ChoreCompletedEvent) {
        published.add(event)
    }
}
