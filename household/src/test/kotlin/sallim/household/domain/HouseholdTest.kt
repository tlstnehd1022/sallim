package sallim.household.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class HouseholdTest : FunSpec({
    test("household 생성 시 최초 멤버는 OWNER다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.memberList shouldHaveSize 1
        household.memberList.first().role shouldBe MemberRole.OWNER
        household.memberList.first().displayName shouldBe "나"
    }

    test("household 생성 시 MemberJoinedEvent가 발행된다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.domainEvents shouldHaveSize 1
        val event = household.domainEvents.first() as MemberJoinedEvent
        event.householdId shouldBe household.id
        event.memberId shouldBe household.memberList.first().id
    }

    test("이름이 빈 문자열이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            Household.create(name = "", ownerDisplayName = "나")
        }
    }

    test("addMember로 두 번째 멤버를 추가할 수 있다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.addMember("짝꿍", MemberRole.MEMBER)

        household.memberList shouldHaveSize 2
        household.memberList.last().role shouldBe MemberRole.MEMBER
        household.domainEvents shouldHaveSize 2
    }
})
