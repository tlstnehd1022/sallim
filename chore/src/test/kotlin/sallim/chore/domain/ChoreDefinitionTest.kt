package sallim.chore.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ChoreDefinitionTest : FunSpec({
    fun newDefinition(label: String = "설거지", steps: List<String> = listOf("헹구기")) = ChoreDefinition(
        id = ChoreDefinitionId.generate(),
        roomId = RoomId.generate(),
        label = label,
        assigneeId = MemberId.generate(),
        recurrence = Daily,
        howToSteps = steps,
        videoQuery = "설거지 순서 팁"
    )

    test("label이 빈 문자열이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> { newDefinition(label = "  ") }
    }

    test("howToSteps가 비어 있으면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> { newDefinition(steps = emptyList()) }
    }
})
