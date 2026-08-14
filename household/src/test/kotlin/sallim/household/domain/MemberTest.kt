package sallim.household.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class MemberTest : FunSpec({
    test("displayName이 빈 문자열이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            Member(
                id = MemberId.generate(),
                householdId = HouseholdId.generate(),
                displayName = "  ",
                role = MemberRole.MEMBER
            )
        }
    }
})
