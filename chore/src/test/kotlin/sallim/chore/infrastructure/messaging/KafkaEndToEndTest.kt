package sallim.chore.infrastructure.messaging

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.KafkaListener
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RecordingChoreCompletedListener {
    val received = mutableListOf<ChoreCompletedEvent>()
    val latch = CountDownLatch(1)

    @KafkaListener(topics = ["chore.completed"], groupId = "test-recorder")
    fun onMessage(event: ChoreCompletedEvent) {
        received.add(event)
        latch.countDown()
    }
}

/**
 * `TestApplication`(전체 `sallim.chore` 패키지를 컴포넌트 스캔)을 그대로 쓰면 JPA
 * `@Repository` 어댑터(`ChoreInstanceRepositoryAdapter` 등)까지 컨텍스트에 딸려 들어와
 * DataSource 없이는 기동이 실패한다. 이 종단 간 테스트는 Kafka 프로듀서/컨슈머 배선만
 * 검증하면 되므로, `@ComponentScan` 없이 자동 설정만 켜는 전용 최소 설정을 둔다 —
 * 필요한 빈(`KafkaChoreEventProducer`, `RecordingChoreCompletedListener`)은 `@Import`로
 * 명시적으로만 등록한다. 공유 `TestApplication`은 다른 통합 테스트(`@DataJpaTest` 슬라이스)가
 * 여전히 그대로 쓰므로 건드리지 않는다.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class KafkaEndToEndTestConfig

@SpringBootTest(
    classes = [KafkaEndToEndTestConfig::class],
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration," +
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
    ]
)
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

        val received = listener.latch.await(30, TimeUnit.SECONDS)
        received shouldBe true
        listener.received shouldHaveSize 1
        listener.received.first() shouldBe event
    }
}
