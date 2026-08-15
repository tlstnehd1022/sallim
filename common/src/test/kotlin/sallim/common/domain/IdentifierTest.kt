package sallim.common.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

private class SampleId(value: UUID) : Identifier<UUID>(value)
private class OtherId(value: UUID) : Identifier<UUID>(value)

class IdentifierTest : FunSpec({
    test("두 Identifier는 같은 타입 + 같은 값이면 동등하다") {
        val uuid = UUID.randomUUID()
        SampleId(uuid) shouldBe SampleId(uuid)
    }

    test("타입이 다르면 값이 같아도 동등하지 않다") {
        val uuid = UUID.randomUUID()
        val sample: Identifier<UUID> = SampleId(uuid)
        val other: Identifier<UUID> = OtherId(uuid)
        (sample == other) shouldBe false
    }
})
