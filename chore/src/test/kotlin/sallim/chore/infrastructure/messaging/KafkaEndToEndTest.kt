package sallim.chore.infrastructure.messaging

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import sallim.chore.TestApplication
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Component
class RecordingChoreCompletedListener {
    val received = mutableListOf<ChoreCompletedEvent>()
    val latch = CountDownLatch(1)

    @KafkaListener(topics = ["chore.completed"], groupId = "test-recorder")
    fun onMessage(event: ChoreCompletedEvent) {
        received.add(event)
        latch.countDown()
    }
}

@SpringBootTest(classes = [TestApplication::class])
@Import(KafkaChoreEventProducer::class, RecordingChoreCompletedListener::class)
class KafkaEndToEndTest : AbstractKafkaIntegrationTest() {

    @Autowired
    lateinit var producer: KafkaChoreEventProducer

    @Autowired
    lateinit var listener: RecordingChoreCompletedListener

    @Test
    fun `발행한 이벤트를 실제 브로커를 거쳐 컨슈머가 받는다`() {
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        producer.publish(event)

        val received = listener.latch.await(10, TimeUnit.SECONDS)
        received shouldBe true
        listener.received shouldHaveSize 1
    }
}
