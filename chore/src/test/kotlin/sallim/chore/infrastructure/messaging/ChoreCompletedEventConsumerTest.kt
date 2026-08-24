package sallim.chore.infrastructure.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId

class ChoreCompletedEventConsumerTest : FunSpec({
    test("이벤트를 받으면 완료 기록을 저장한다") {
        val repository = FakeChoreCompletionRecordRepository()
        val consumer = ChoreCompletedEventConsumer(repository)
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        consumer.onMessage(event)

        repository.saved shouldHaveSize 1
        repository.saved.first().choreInstanceId shouldBe event.choreInstanceId
        repository.saved.first().choreDefinitionId shouldBe event.choreDefinitionId
        repository.saved.first().completedBy shouldBe event.completedBy
        repository.saved.first().completedAt shouldBe event.occurredAt
    }
})
