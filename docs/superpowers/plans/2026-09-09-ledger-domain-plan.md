# Ledger 도메인 모델 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ledger` 바운디드 컨텍스트에 `Transaction`(지출 기록) 순수 도메인 모델을 추가한다.

**Architecture:** chore/calendar가 확립한 패턴 그대로 — 프레임워크 의존 없는 순수 Kotlin 도메인 클래스 하나(`Transaction`)와 그 식별자 타입들만 만든다. 영속성/API는 다루지 않는다.

**Tech Stack:** Kotlin, Kotest.

**Spec:** `docs/superpowers/specs/2026-09-09-ledger-domain-design.md`

## Global Constraints

- `ledger`는 `MemberId`를 자체 복제한다 — household/chore/calendar의 `MemberId`를 참조하지 않는다(컨텍스트 간 직접 참조 금지, CLAUDE.md).
- 금액(`amount`)은 `Long`(원 단위 정수)이다 — `BigDecimal` 등 정밀도 도구는 쓰지 않는다(단일 통화 KRW 가정).
- `category`는 자유 문자열이다 — enum이나 값 목록으로 제한하지 않는다.
- `occurredAt`은 `LocalDateTime`이다(날짜만이 아니라 시각까지).
- 이번 서브프로젝트는 도메인 모델만 다룬다 — 영속성(JPA)/REST API/정산 계산/정기 지출(RecurrencePolicy 재사용)은 범위 밖이며, 이 플랜에 포함하지 않는다.
- `domain` 패키지는 Spring·JPA 등 프레임워크 의존 금지 — 순수 Kotlin(CLAUDE.md).

---

### Task 1: `ledger` 모듈 Gradle 설정 + `Transaction` 도메인 모델

**Files:**
- Modify: `ledger/build.gradle.kts`
- Create: `ledger/src/main/kotlin/sallim/ledger/domain/TransactionId.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/domain/MemberId.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/domain/Transaction.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/domain/TransactionTest.kt`

**Interfaces:**
- Consumes: `sallim.common.domain.Identifier` (기존, `common` 모듈)
- Produces: `sallim.ledger.domain.TransactionId`(`generate(): TransactionId`), `sallim.ledger.domain.MemberId`(`generate(): MemberId`), `sallim.ledger.domain.Transaction(id: TransactionId, memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime)` — 이 서브프로젝트의 최종 산출물, 이후 태스크 없음.

- [ ] **Step 1: `ledger/build.gradle.kts`를 채운다 (지금은 완전히 빈 파일)**

```kotlin
dependencies {
    implementation(project(":common"))
}
```

- [ ] **Step 2: 실패하는 테스트를 작성한다 — `sallim/ledger/domain/TransactionTest.kt`**

```kotlin
package sallim.ledger.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class TransactionTest : FunSpec({
    test("정상적인 값으로 생성하면 필드가 그대로 보존된다") {
        val id = TransactionId.generate()
        val memberId = MemberId.generate()
        val occurredAt = LocalDateTime.of(2026, 9, 9, 14, 30)

        val transaction = Transaction(id, memberId, 15000L, "식비", "장보기", occurredAt)

        transaction.id shouldBe id
        transaction.memberId shouldBe memberId
        transaction.amount shouldBe 15000L
        transaction.category shouldBe "식비"
        transaction.memo shouldBe "장보기"
        transaction.occurredAt shouldBe occurredAt
    }

    test("memo는 null일 수 있다") {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 5000L, "생활용품", null,
            LocalDateTime.of(2026, 9, 9, 10, 0)
        )

        transaction.memo shouldBe null
    }

    test("amount가 0이면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), 0L, "식비", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }

    test("amount가 음수면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), -1000L, "식비", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }

    test("category가 공백이면 IllegalArgumentException") {
        shouldThrow<IllegalArgumentException> {
            Transaction(
                TransactionId.generate(), MemberId.generate(), 1000L, "   ", null,
                LocalDateTime.of(2026, 9, 9, 10, 0)
            )
        }
    }
})
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.domain.TransactionTest"`
Expected: FAIL — `TransactionId`/`MemberId`/`Transaction`이 없어 컴파일 에러.

- [ ] **Step 4: `TransactionId`를 작성한다 — `sallim/ledger/domain/TransactionId.kt`**

```kotlin
package sallim.ledger.domain

import sallim.common.domain.Identifier
import java.util.UUID

class TransactionId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): TransactionId = TransactionId(UUID.randomUUID())
    }
}
```

- [ ] **Step 5: `MemberId`를 작성한다 — `sallim/ledger/domain/MemberId.kt`**

```kotlin
package sallim.ledger.domain

import sallim.common.domain.Identifier
import java.util.UUID

/** `ledger` 컨텍스트 로컬 복제본 — household/chore/calendar의 MemberId를 참조하지 않는다(의도적 중복). */
class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
```

- [ ] **Step 6: `Transaction`을 작성한다 — `sallim/ledger/domain/Transaction.kt`**

```kotlin
package sallim.ledger.domain

import java.time.LocalDateTime

class Transaction(
    val id: TransactionId,
    val memberId: MemberId,
    val amount: Long,
    val category: String,
    val memo: String?,
    val occurredAt: LocalDateTime
) {
    init {
        require(amount > 0) { "amount must be positive: $amount" }
        require(category.isNotBlank()) { "category must not be blank" }
    }
}
```

- [ ] **Step 7: 테스트 실행 — 통과 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.domain.TransactionTest"`
Expected: PASS (Docker 불필요 — 순수 도메인 로직)

- [ ] **Step 8: Commit**

```bash
git add ledger/build.gradle.kts ledger/src/main/kotlin/sallim/ledger/domain ledger/src/test/kotlin/sallim/ledger/domain
git commit -m "feat: Transaction 도메인 모델 추가"
```

## 다음 단계

이 플랜 완료 후 `superpowers:finishing-a-development-branch`로 머지/푸시.
