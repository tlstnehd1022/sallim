package sallim.chore.infrastructure.messaging

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreCompletionRecordRepository

@Component
class ChoreCompletedEventConsumer(private val repository: ChoreCompletionRecordRepository) {
    @KafkaListener(topics = ["chore.completed"], groupId = "chore-stats")
    fun onMessage(event: ChoreCompletedEvent) {
        repository.save(event.choreInstanceId, event.choreDefinitionId, event.completedBy, event.occurredAt)
    }
}
