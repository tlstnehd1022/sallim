package sallim.common.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import java.time.Instant
import java.util.UUID

private class AggregateSampleId(value: UUID) : Identifier<UUID>(value)

private data class SampleEvent(override val occurredAt: Instant = Instant.now()) : DomainEvent

private class SampleAggregate(override val id: AggregateSampleId) : AggregateRoot<AggregateSampleId>() {
    fun doSomething() {
        registerEvent(SampleEvent())
    }
}

class AggregateRootTest : FunSpec({
    test("이벤트를 등록하면 domainEvents에 쌓인다") {
        val aggregate = SampleAggregate(AggregateSampleId(UUID.randomUUID()))
        aggregate.doSomething()
        aggregate.domainEvents shouldHaveSize 1
    }

    test("clearEvents 호출 시 이벤트가 비워진다") {
        val aggregate = SampleAggregate(AggregateSampleId(UUID.randomUUID()))
        aggregate.doSomething()
        aggregate.clearEvents()
        aggregate.domainEvents.shouldBeEmpty()
    }
})
