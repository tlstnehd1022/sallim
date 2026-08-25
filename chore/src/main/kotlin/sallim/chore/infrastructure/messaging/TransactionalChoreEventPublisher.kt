package sallim.chore.infrastructure.messaging

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

@Component
class TransactionalChoreEventPublisher(private val producer: ChoreEventProducer) {
    companion object {
        private val logger = LoggerFactory.getLogger(TransactionalChoreEventPublisher::class.java)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChoreCompleted(event: ChoreCompletedEvent) {
        runCatching { producer.publish(event) }
            .onFailure { logger.error("Kafka publish failed for chore instance {}, dropping event", event.choreInstanceId, it) }
    }
}
