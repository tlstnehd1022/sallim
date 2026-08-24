package sallim.chore.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

@Component
class KafkaChoreEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, ChoreCompletedEvent>
) : ChoreEventProducer {
    override fun publish(event: ChoreCompletedEvent) {
        kafkaTemplate.send("chore.completed", event.choreInstanceId.value.toString(), event)
    }
}
