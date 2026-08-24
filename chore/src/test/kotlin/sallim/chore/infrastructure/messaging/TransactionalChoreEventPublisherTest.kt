package sallim.chore.infrastructure.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId

class TransactionalChoreEventPublisherTest : FunSpec({
    test("이벤트를 받으면 producer에 그대로 전달한다") {
        val producer = FakeChoreEventProducer()
        val publisher = TransactionalChoreEventPublisher(producer)
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        publisher.onChoreCompleted(event)

        producer.published shouldHaveSize 1
        producer.published.first() shouldBe event
    }
})
