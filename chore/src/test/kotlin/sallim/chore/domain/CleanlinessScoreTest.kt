package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import java.time.LocalDate

class CleanlinessScoreTest : FunSpec({
    val choreDefinitionId = ChoreDefinitionId.generate()
    val referenceDate = LocalDate.of(2026, 8, 19)

    test("미완료 인스턴스가 없으면 0.0이다") {
        CleanlinessScore.compute(emptyList(), referenceDate) shouldBe (0.0 plusOrMinus 0.0001)
    }

    test("오늘 도래한 미완료 인스턴스 하나는 1.0이다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, referenceDate)
        CleanlinessScore.compute(listOf(instance), referenceDate) shouldBe (1.0 plusOrMinus 0.0001)
    }

    test("3일 지연된 미완료 인스턴스는 1 + 3*0.15 = 1.45다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, referenceDate.minusDays(3))
        CleanlinessScore.compute(listOf(instance), referenceDate) shouldBe (1.45 plusOrMinus 0.0001)
    }

    test("완료된 인스턴스는 제외한다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, referenceDate.minusDays(3))
        instance.complete(MemberId.generate())
        CleanlinessScore.compute(listOf(instance), referenceDate) shouldBe (0.0 plusOrMinus 0.0001)
    }

    test("아직 도래하지 않은(미래) 인스턴스는 제외한다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, referenceDate.plusDays(1))
        CleanlinessScore.compute(listOf(instance), referenceDate) shouldBe (0.0 plusOrMinus 0.0001)
    }

    test("여러 인스턴스는 합산한다 (모바일 computeCleanliness와 동일 케이스)") {
        val overdueBy3 = ChoreInstance.schedule(choreDefinitionId, referenceDate.minusDays(3))
        val dueToday = ChoreInstance.schedule(choreDefinitionId, referenceDate)
        CleanlinessScore.compute(listOf(overdueBy3, dueToday), referenceDate) shouldBe (2.45 plusOrMinus 0.0001)
    }
})
