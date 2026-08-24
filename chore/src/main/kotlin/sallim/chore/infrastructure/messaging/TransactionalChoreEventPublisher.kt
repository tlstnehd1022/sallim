package sallim.chore.infrastructure.messaging

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

@Component
class TransactionalChoreEventPublisher(private val producer: ChoreEventProducer) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChoreCompleted(event: ChoreCompletedEvent) {
        producer.publish(event)
    }
}
