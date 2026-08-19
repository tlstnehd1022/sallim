# Chore(집안일) 도메인 모델 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chore` 모듈에 순수 Kotlin으로 Chore(집안일) 도메인 모델(Room/FloorPlan/ChoreDefinition/ChoreInstance/RecurrencePolicy/CleanlinessScore)을 구현하고, 모바일 목업의 시드 데이터(방 10개·할 일 18개)를 그대로 포팅한다.

**Architecture:** `chore` 모듈은 `common`(AggregateRoot/DomainEvent/Identifier)만 참조하고 `household`는 참조하지 않는다 — 담당자는 chore 모듈이 자체 정의한 `MemberId`(household의 것과 값은 같을 수 있으나 타입 독립)로 참조한다. `ChoreInstance`가 `AggregateRoot`로서 완료 시 `ChoreCompletedEvent`를 발행한다.

**Tech Stack:** Kotlin 2.0.20 / JDK 21 / JUnit5 + Kotest 5.9.1 (기존 스캐폴딩 그대로, 신규 의존성 없음)

**Spec:** `docs/superpowers/specs/2026-08-19-chore-domain-design.md` (및 `sallim-master-spec.md` 4.3장/4.4장, `mobile/src/domain/seedRooms.ts`, `mobile/src/domain/cleanliness.ts`)

## Global Constraints

- 바운디드 컨텍스트 간 직접 참조 금지 — `chore`는 `household`를 참조하지 않는다 (CLAUDE.md)
- `domain` 패키지는 Spring·JPA 등 프레임워크 의존 금지 — 순수 Kotlin (CLAUDE.md)
- YAGNI — 설계 문서에 명시된 것만 구현. 영속성/API/스케줄러/Kafka/CQRS/household 연동은 이번 범위 밖 (설계 문서 "제외" 절)
- `RecurrencePolicy`는 `Daily`/`WeeklyNTimes(n)`/`Monthly` 3종만 (갭#1 확장은 다음 서브프로젝트)
- `CleanlinessScore` 공식은 `mobile/src/domain/cleanliness.ts`의 `DELAY_COEFFICIENT = 0.15`와 동일해야 한다

---

## File Structure

```
chore/
  build.gradle.kts                                              (수정)
  src/main/kotlin/sallim/chore/domain/
    RoomId.kt
    Room.kt
    RoomPlacement.kt
    FloorPlan.kt
    MemberId.kt
    RecurrencePolicy.kt
    ChoreDefinitionId.kt
    ChoreDefinition.kt
    ChoreInstanceId.kt
    ChoreCompletedEvent.kt
    ChoreInstance.kt
    CleanlinessScore.kt
    DefaultRooms.kt
  src/test/kotlin/sallim/chore/domain/
    FloorPlanTest.kt
    RecurrencePolicyTest.kt
    ChoreDefinitionTest.kt
    ChoreInstanceTest.kt
    CleanlinessScoreTest.kt
    DefaultRoomsTest.kt
```

---

### Task 1: 모듈 배선 + Room / FloorPlan

**Files:**
- Modify: `chore/build.gradle.kts`
- Create: `chore/src/main/kotlin/sallim/chore/domain/RoomId.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/Room.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/RoomPlacement.kt`
- Test: `chore/src/test/kotlin/sallim/chore/domain/FloorPlanTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/FloorPlan.kt`

**Interfaces:**
- Consumes: `sallim.common.domain.Identifier` (household 모듈에서 이미 검증된 패턴)
- Produces:
  - `class RoomId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): RoomId }`
  - `class Room(val id: RoomId, val name: String)`
  - `data class RoomPlacement(val roomId: RoomId, val x: Int, val y: Int, val w: Int, val h: Int, val z: Int)`
  - `class FloorPlan { val placements: List<RoomPlacement> }` — `companion object { fun of(placements: List<RoomPlacement>): FloorPlan }`
  - Task 6의 `DefaultRooms`가 이 넷을 모두 사용한다.

- [ ] **Step 1: chore → common 의존성 추가**

`chore/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":common"))
}
```

- [ ] **Step 2: RoomId, Room, RoomPlacement 작성**

순수 데이터 타입이라 별도 단위 테스트 없이 바로 작성한다 (`Identifier`의 equals/hashCode는 household 모듈에서 이미 검증됨).

`chore/src/main/kotlin/sallim/chore/domain/RoomId.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class RoomId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): RoomId = RoomId(UUID.randomUUID())
    }
}
```

`chore/src/main/kotlin/sallim/chore/domain/Room.kt`:
```kotlin
package sallim.chore.domain

class Room(
    val id: RoomId,
    val name: String
) {
    init {
        require(name.isNotBlank()) { "room name must not be blank" }
    }
}
```

`chore/src/main/kotlin/sallim/chore/domain/RoomPlacement.kt`:
```kotlin
package sallim.chore.domain

data class RoomPlacement(
    val roomId: RoomId,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val z: Int
)
```

- [ ] **Step 3: FloorPlan 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/FloorPlanTest.kt`:
```kotlin
package sallim.chore.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize

class FloorPlanTest : FunSpec({
    val roomId = RoomId.generate()

    test("범위 안 배치는 그대로 생성된다") {
        val floorPlan = FloorPlan.of(listOf(RoomPlacement(roomId, x = 26, y = 38, w = 74, h = 50, z = 1)))
        floorPlan.placements shouldHaveSize 1
    }

    test("w가 8 미만이면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 0, w = 7, h = 10, z = 1)))
        }
    }

    test("w가 100-x를 넘으면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 80, y = 0, w = 21, h = 10, z = 1)))
        }
    }

    test("h가 6 미만이면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 0, w = 10, h = 5, z = 1)))
        }
    }

    test("h가 100-y를 넘으면 거부한다") {
        shouldThrow<IllegalArgumentException> {
            FloorPlan.of(listOf(RoomPlacement(roomId, x = 0, y = 90, w = 10, h = 11, z = 1)))
        }
    }
})
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.FloorPlanTest"`
Expected: FAIL — `FloorPlan` 클래스가 없어 컴파일 에러.

- [ ] **Step 5: FloorPlan 구현**

`chore/src/main/kotlin/sallim/chore/domain/FloorPlan.kt`:
```kotlin
package sallim.chore.domain

class FloorPlan private constructor(val placements: List<RoomPlacement>) {
    companion object {
        fun of(placements: List<RoomPlacement>): FloorPlan {
            placements.forEach { p ->
                require(p.w in 8..(100 - p.x)) { "w must be within [8, ${100 - p.x}]: ${p.w}" }
                require(p.h in 6..(100 - p.y)) { "h must be within [6, ${100 - p.y}]: ${p.h}" }
            }
            return FloorPlan(placements)
        }
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.FloorPlanTest"`
Expected: PASS (5개 테스트 모두 통과)

- [ ] **Step 7: Commit**

```bash
git add chore/build.gradle.kts chore/src/main/kotlin/sallim/chore/domain/RoomId.kt chore/src/main/kotlin/sallim/chore/domain/Room.kt chore/src/main/kotlin/sallim/chore/domain/RoomPlacement.kt chore/src/main/kotlin/sallim/chore/domain/FloorPlan.kt chore/src/test/kotlin/sallim/chore/domain/FloorPlanTest.kt
git commit -m "feat: chore 모듈 배선 + Room/FloorPlan 도메인 모델"
```

---

### Task 2: MemberId + RecurrencePolicy

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/MemberId.kt`
- Test: `chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt`

**Interfaces:**
- Consumes: `sallim.common.domain.Identifier`
- Produces:
  - `class MemberId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): MemberId }`. household의 `MemberId`와 이름은 같지만 별개 타입 (모듈 의존 없음).
  - `sealed interface RecurrencePolicy { fun nextOccurrence(after: LocalDate): LocalDate }`
  - `data object Daily : RecurrencePolicy`
  - `data class WeeklyNTimes(val times: Int) : RecurrencePolicy` — `times`는 1..7, 아니면 `IllegalArgumentException`
  - `data object Monthly : RecurrencePolicy`
  - Task 3의 `ChoreDefinition.assigneeId`가 `MemberId`를, `ChoreDefinition.recurrence`가 `RecurrencePolicy`를 사용한다.

- [ ] **Step 1: MemberId 작성**

순수 데이터 타입이라 별도 테스트 없이 바로 작성한다.

`chore/src/main/kotlin/sallim/chore/domain/MemberId.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
```

- [ ] **Step 2: RecurrencePolicy 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt`:
```kotlin
package sallim.chore.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class RecurrencePolicyTest : FunSpec({
    val today = LocalDate.of(2026, 8, 19)

    test("Daily는 하루 뒤를 반환한다") {
        Daily.nextOccurrence(today) shouldBe today.plusDays(1)
    }

    test("WeeklyNTimes(1)은 7일 뒤를 반환한다") {
        WeeklyNTimes(1).nextOccurrence(today) shouldBe today.plusDays(7)
    }

    test("WeeklyNTimes(2)는 3일 뒤를 반환한다") {
        WeeklyNTimes(2).nextOccurrence(today) shouldBe today.plusDays(3)
    }

    test("WeeklyNTimes(7)은 1일 뒤를 반환한다") {
        WeeklyNTimes(7).nextOccurrence(today) shouldBe today.plusDays(1)
    }

    test("Monthly는 한 달 뒤를 반환한다") {
        Monthly.nextOccurrence(today) shouldBe today.plusMonths(1)
    }

    test("WeeklyNTimes는 1..7 범위를 벗어나면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> { WeeklyNTimes(0) }
        shouldThrow<IllegalArgumentException> { WeeklyNTimes(8) }
    }
})
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.RecurrencePolicyTest"`
Expected: FAIL — `RecurrencePolicy`, `Daily`, `WeeklyNTimes`, `Monthly` 클래스가 없어 컴파일 에러.

- [ ] **Step 4: RecurrencePolicy 구현**

`chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt`:
```kotlin
package sallim.chore.domain

import java.time.LocalDate

sealed interface RecurrencePolicy {
    fun nextOccurrence(after: LocalDate): LocalDate
}

data object Daily : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusDays(1)
}

// ponytail: 요일 지정 없는 균등 분배 근사치(7일/times) — 특정 요일 반복이 필요해지면(갭#1)
// 요일 집합을 받는 정책으로 교체
data class WeeklyNTimes(val times: Int) : RecurrencePolicy {
    init {
        require(times in 1..7) { "times must be within 1..7: $times" }
    }

    override fun nextOccurrence(after: LocalDate): LocalDate =
        after.plusDays((7 / times).toLong())
}

data object Monthly : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusMonths(1)
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.RecurrencePolicyTest"`
Expected: PASS (6개 테스트 모두 통과)

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/MemberId.kt chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt
git commit -m "feat: chore MemberId + RecurrencePolicy(Daily/WeeklyNTimes/Monthly)"
```

---

### Task 3: ChoreDefinition

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionId.kt`
- Test: `chore/src/test/kotlin/sallim/chore/domain/ChoreDefinitionTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreDefinition.kt`

**Interfaces:**
- Consumes: `RoomId`(Task 1), `MemberId`/`RecurrencePolicy`(Task 2)
- Produces:
  - `class ChoreDefinitionId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): ChoreDefinitionId }`
  - `class ChoreDefinition(val id: ChoreDefinitionId, val roomId: RoomId, val label: String, val assigneeId: MemberId, val recurrence: RecurrencePolicy, val howToSteps: List<String>, val videoQuery: String)`
  - Task 4의 `ChoreInstance.choreDefinitionId`가 `ChoreDefinitionId`를 참조하고, Task 6의 `DefaultRooms`가 `ChoreDefinition`을 생성한다.

- [ ] **Step 1: ChoreDefinitionId 작성**

순수 데이터 타입이라 별도 테스트 없이 바로 작성한다.

`chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionId.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class ChoreDefinitionId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): ChoreDefinitionId = ChoreDefinitionId(UUID.randomUUID())
    }
}
```

- [ ] **Step 2: ChoreDefinition 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/ChoreDefinitionTest.kt`:
```kotlin
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
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.ChoreDefinitionTest"`
Expected: FAIL — `ChoreDefinition` 클래스가 없어 컴파일 에러.

- [ ] **Step 4: ChoreDefinition 구현**

`chore/src/main/kotlin/sallim/chore/domain/ChoreDefinition.kt`:
```kotlin
package sallim.chore.domain

class ChoreDefinition(
    val id: ChoreDefinitionId,
    val roomId: RoomId,
    val label: String,
    val assigneeId: MemberId,
    val recurrence: RecurrencePolicy,
    val howToSteps: List<String>,
    val videoQuery: String
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(howToSteps.isNotEmpty()) { "howToSteps must not be empty" }
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.ChoreDefinitionTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionId.kt chore/src/main/kotlin/sallim/chore/domain/ChoreDefinition.kt chore/src/test/kotlin/sallim/chore/domain/ChoreDefinitionTest.kt
git commit -m "feat: ChoreDefinition 도메인 모델"
```

---

### Task 4: ChoreInstance + ChoreCompletedEvent

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceId.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreCompletedEvent.kt`
- Test: `chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt`

**Interfaces:**
- Consumes: `ChoreDefinitionId`(Task 3), `MemberId`(Task 2), `sallim.common.domain.AggregateRoot`/`DomainEvent`(common 모듈)
- Produces:
  - `class ChoreInstanceId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): ChoreInstanceId }`
  - `data class ChoreCompletedEvent(val choreInstanceId: ChoreInstanceId, val choreDefinitionId: ChoreDefinitionId, val completedBy: MemberId, override val occurredAt: Instant) : DomainEvent`
  - `class ChoreInstance : AggregateRoot<ChoreInstanceId>` — `companion object { fun schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance }`, `val scheduledDate: LocalDate`, `val completed: Boolean`, `val completedBy: MemberId?`, `val completedAt: Instant?`, `fun complete(memberId: MemberId)`
  - Task 5의 `CleanlinessScore.compute`가 `ChoreInstance` 목록을 입력받는다.

- [ ] **Step 1: ChoreInstanceId, ChoreCompletedEvent 작성**

순수 데이터 타입이라 별도 테스트 없이 바로 작성한다 (발행 여부는 `ChoreInstanceTest`에서 검증).

`chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceId.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.Identifier
import java.util.UUID

class ChoreInstanceId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): ChoreInstanceId = ChoreInstanceId(UUID.randomUUID())
    }
}
```

`chore/src/main/kotlin/sallim/chore/domain/ChoreCompletedEvent.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.DomainEvent
import java.time.Instant

data class ChoreCompletedEvent(
    val choreInstanceId: ChoreInstanceId,
    val choreDefinitionId: ChoreDefinitionId,
    val completedBy: MemberId,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
```

- [ ] **Step 2: ChoreInstance 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt`:
```kotlin
package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ChoreInstanceTest : FunSpec({
    val choreDefinitionId = ChoreDefinitionId.generate()
    val scheduledDate = LocalDate.of(2026, 8, 19)

    test("schedule 직후에는 미완료 상태다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)

        instance.completed shouldBe false
        instance.completedBy shouldBe null
        instance.completedAt shouldBe null
    }

    test("complete 호출 시 완료 처리되고 ChoreCompletedEvent가 발행된다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        val memberId = MemberId.generate()

        instance.complete(memberId)

        instance.completed shouldBe true
        instance.completedBy shouldBe memberId
        instance.domainEvents shouldHaveSize 1
        val event = instance.domainEvents.first() as ChoreCompletedEvent
        event.choreInstanceId shouldBe instance.id
        event.choreDefinitionId shouldBe choreDefinitionId
        event.completedBy shouldBe memberId
    }

    test("이미 완료된 인스턴스는 다시 완료할 수 없다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        instance.complete(MemberId.generate())

        io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
            instance.complete(MemberId.generate())
        }
    }
})
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.ChoreInstanceTest"`
Expected: FAIL — `ChoreInstance` 클래스가 없어 컴파일 에러.

- [ ] **Step 4: ChoreInstance 구현**

`chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt`:
```kotlin
package sallim.chore.domain

import sallim.common.domain.AggregateRoot
import java.time.Instant
import java.time.LocalDate

class ChoreInstance private constructor(
    override val id: ChoreInstanceId,
    val choreDefinitionId: ChoreDefinitionId,
    val scheduledDate: LocalDate,
    completed: Boolean,
    completedBy: MemberId?,
    completedAt: Instant?
) : AggregateRoot<ChoreInstanceId>() {

    var completed: Boolean = completed
        private set
    var completedBy: MemberId? = completedBy
        private set
    var completedAt: Instant? = completedAt
        private set

    fun complete(memberId: MemberId) {
        check(!completed) { "chore instance already completed" }
        completed = true
        completedBy = memberId
        completedAt = Instant.now()
        registerEvent(
            ChoreCompletedEvent(
                choreInstanceId = id,
                choreDefinitionId = choreDefinitionId,
                completedBy = memberId
            )
        )
    }

    companion object {
        fun schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance =
            ChoreInstance(
                id = ChoreInstanceId.generate(),
                choreDefinitionId = choreDefinitionId,
                scheduledDate = scheduledDate,
                completed = false,
                completedBy = null,
                completedAt = null
            )
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.ChoreInstanceTest"`
Expected: PASS (3개 테스트 모두 통과)

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceId.kt chore/src/main/kotlin/sallim/chore/domain/ChoreCompletedEvent.kt chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt
git commit -m "feat: ChoreInstance 애그리거트 + ChoreCompletedEvent"
```

---

### Task 5: CleanlinessScore

**Files:**
- Test: `chore/src/test/kotlin/sallim/chore/domain/CleanlinessScoreTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/CleanlinessScore.kt`

**Interfaces:**
- Consumes: `ChoreInstance`(Task 4)
- Produces: `object CleanlinessScore { fun compute(instances: List<ChoreInstance>, referenceDate: LocalDate): Double }`

- [ ] **Step 1: CleanlinessScore 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/CleanlinessScoreTest.kt`:
```kotlin
package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBe
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
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.CleanlinessScoreTest"`
Expected: FAIL — `CleanlinessScore` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: CleanlinessScore 구현**

`chore/src/main/kotlin/sallim/chore/domain/CleanlinessScore.kt`:
```kotlin
package sallim.chore.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object CleanlinessScore {
    private const val DELAY_COEFFICIENT = 0.15

    fun compute(instances: List<ChoreInstance>, referenceDate: LocalDate): Double =
        instances
            .filter { !it.completed && !it.scheduledDate.isAfter(referenceDate) }
            .sumOf { instance ->
                val overdueDays = ChronoUnit.DAYS.between(instance.scheduledDate, referenceDate)
                1 + overdueDays * DELAY_COEFFICIENT
            }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.CleanlinessScoreTest"`
Expected: PASS (6개 테스트 모두 통과)

- [ ] **Step 5: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/CleanlinessScore.kt chore/src/test/kotlin/sallim/chore/domain/CleanlinessScoreTest.kt
git commit -m "feat: CleanlinessScore 계산 (모바일 cleanliness.ts와 동일 공식)"
```

---

### Task 6: DefaultRooms 시드 데이터 + 최종 빌드 검증

**Files:**
- Test: `chore/src/test/kotlin/sallim/chore/domain/DefaultRoomsTest.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/DefaultRooms.kt`

**Interfaces:**
- Consumes: `Room`/`RoomPlacement`/`FloorPlan`(Task 1), `MemberId`/`RecurrencePolicy`(Task 2), `ChoreDefinitionId`/`ChoreDefinition`(Task 3)
- Produces: `object DefaultRooms { val ME: MemberId; val PARTNER: MemberId; val rooms: List<Room>; val floorPlan: FloorPlan; val choreDefinitions: List<ChoreDefinition> }` — 이후 서브프로젝트(영속성/시딩)가 이 객체를 초기 데이터 소스로 사용한다.

- [ ] **Step 1: DefaultRooms 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/DefaultRoomsTest.kt`:
```kotlin
package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize

class DefaultRoomsTest : FunSpec({
    test("방은 10개다") {
        DefaultRooms.rooms shouldHaveSize 10
    }

    test("평면도 배치는 방 개수와 같다") {
        DefaultRooms.floorPlan.placements shouldHaveSize 10
    }

    test("집안일 정의는 18개다") {
        DefaultRooms.choreDefinitions shouldHaveSize 18
    }

    test("모든 집안일은 실재하는 방을 참조한다") {
        val roomIds = DefaultRooms.rooms.map { it.id }.toSet()
        DefaultRooms.choreDefinitions.forEach { definition ->
            (definition.roomId in roomIds) shouldBe true
        }
    }
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.DefaultRoomsTest"`
Expected: FAIL — `DefaultRooms` 클래스가 없어 컴파일 에러. (`shouldBe` import 누락 시 컴파일 에러도 함께 나므로, Step 1 코드에 `import io.kotest.matchers.shouldBe`를 포함시켰는지 확인 — 아래 최종본에는 포함되어 있다.)

Step 1 코드 상단 import를 다음으로 고정한다:
```kotlin
package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
```

- [ ] **Step 3: DefaultRooms 구현 (모바일 seedRooms.ts 포팅)**

`chore/src/main/kotlin/sallim/chore/domain/DefaultRooms.kt`:
```kotlin
package sallim.chore.domain

object DefaultRooms {
    val ME: MemberId = MemberId.generate()
    val PARTNER: MemberId = MemberId.generate()

    private fun chore(
        roomId: RoomId,
        label: String,
        assignee: MemberId,
        recurrence: RecurrencePolicy,
        steps: List<String>,
        videoQuery: String
    ) = ChoreDefinition(
        id = ChoreDefinitionId.generate(),
        roomId = roomId,
        label = label,
        assigneeId = assignee,
        recurrence = recurrence,
        howToSteps = steps,
        videoQuery = videoQuery
    )

    private val kveranda = Room(RoomId.generate(), "주방 베란다")
    private val kid = Room(RoomId.generate(), "아이방")
    private val kitchen = Room(RoomId.generate(), "주방")
    private val myroom = Room(RoomId.generate(), "컴퓨터방")
    private val bath = Room(RoomId.generate(), "공용욕실")
    private val living = Room(RoomId.generate(), "거실")
    private val entry = Room(RoomId.generate(), "현관")
    private val master = Room(RoomId.generate(), "안방")
    private val mbath = Room(RoomId.generate(), "안방욕실")
    private val lveranda = Room(RoomId.generate(), "거실 베란다")

    val rooms: List<Room> = listOf(kveranda, kid, kitchen, myroom, bath, living, entry, master, mbath, lveranda)

    val floorPlan: FloorPlan = FloorPlan.of(
        listOf(
            RoomPlacement(kveranda.id, x = 26, y = 0, w = 36, h = 8, z = 2),
            RoomPlacement(kid.id, x = 0, y = 8, w = 26, h = 30, z = 2),
            RoomPlacement(kitchen.id, x = 26, y = 8, w = 36, h = 30, z = 2),
            RoomPlacement(myroom.id, x = 62, y = 8, w = 38, h = 30, z = 2),
            RoomPlacement(bath.id, x = 0, y = 38, w = 26, h = 15, z = 2),
            RoomPlacement(living.id, x = 26, y = 38, w = 74, h = 50, z = 1),
            RoomPlacement(entry.id, x = 78, y = 38, w = 22, h = 14, z = 3),
            RoomPlacement(master.id, x = 0, y = 53, w = 26, h = 35, z = 1),
            RoomPlacement(mbath.id, x = 0, y = 53, w = 15, h = 13, z = 3),
            RoomPlacement(lveranda.id, x = 0, y = 88, w = 100, h = 12, z = 2)
        )
    )

    val choreDefinitions: List<ChoreDefinition> = listOf(
        chore(
            kveranda.id, "분리수거", PARTNER, WeeklyNTimes(2),
            listOf("플라스틱은 라벨 떼고 한 번 헹구기", "종이 상자는 테이프·송장 제거", "금·일 저녁에 한 번에 배출"),
            "분리수거 제대로 하는 법"
        ),
        chore(
            kid.id, "장난감 정리", PARTNER, Daily,
            listOf("바구니 3개로 종류별 분류", "아이와 5분 타이머 걸고 같이", "안 쓰는 건 상자에 격리 보관"),
            "아이방 장난감 수납"
        ),
        chore(
            kid.id, "침구 정리", PARTNER, Daily,
            listOf("이불은 발끝부터 반듯하게", "베개 털어 각 세우기", "주 1회 커버 세탁"),
            "침구 정리 습관"
        ),
        chore(
            kitchen.id, "설거지", ME, Daily,
            listOf("기름기 없는 그릇부터 먼저", "수세미는 주 1회 교체", "마지막에 싱크볼까지 닦기"),
            "설거지 순서 팁"
        ),
        chore(
            kitchen.id, "음식물 쓰레기", PARTNER, Daily,
            listOf("물기를 최대한 짜기", "신문지로 한 번 감싸기", "저녁 산책 나갈 때 같이"),
            "음식물 쓰레기 냄새 잡기"
        ),
        chore(
            kitchen.id, "가스레인지 닦기", ME, WeeklyNTimes(2),
            listOf("식은 뒤 베이킹소다 뿌리기", "5분 두고 마른 천으로 밀기", "틈새는 면봉으로 마무리"),
            "가스레인지 기름때 제거"
        ),
        chore(
            myroom.id, "책상 정리", ME, Daily,
            listOf("책상 위 물건을 전부 내리기", "자주 쓰는 것만 다시 올리기", "서류는 트레이 한 곳에"),
            "책상 정리 루틴"
        ),
        chore(
            myroom.id, "케이블 정리", ME, Monthly,
            listOf("전원 뽑고 전부 분리", "케이블 타이로 묶기", "라벨 붙여 구분"),
            "책상 밑 케이블 정리"
        ),
        chore(
            bath.id, "변기 청소", PARTNER, WeeklyNTimes(1),
            listOf("세정제 뿌리고 5분 방치", "솔로 테두리 안쪽부터", "물내림 버튼·손잡이 소독"),
            "변기 청소하는 법"
        ),
        chore(
            bath.id, "세면대 닦기", ME, WeeklyNTimes(2),
            listOf("배수구 머리카락 먼저 제거", "구연산수로 물때 녹이기", "수전은 마른 천으로 광내기"),
            "세면대 물때 제거"
        ),
        chore(
            living.id, "바닥 청소기", PARTNER, Daily,
            listOf("바닥 물건 먼저 치우기", "창가에서 문 쪽으로 밀기", "소파 밑은 3초 더"),
            "빠른 바닥 청소"
        ),
        chore(
            living.id, "소파 위 옷 치우기", ME, Daily,
            listOf("입을 옷 / 빨래 두 더미로", "빨래는 바로 세탁기로", "5분 넘기지 않기"),
            "옷 쌓임 방지 정리"
        ),
        chore(
            living.id, "테이블 닦기", ME, Daily,
            listOf("컵·리모컨 제자리로", "물티슈 후 마른 천으로", "컵받침 깔아두기"),
            "거실 테이블 정리"
        ),
        chore(
            entry.id, "신발 정리", PARTNER, WeeklyNTimes(1),
            listOf("오늘 신은 것만 밖에 두기", "나머지는 신발장 안으로", "현관 바닥 물걸레"),
            "현관 신발 수납"
        ),
        chore(
            master.id, "이불 정리", ME, Daily,
            listOf("일어나자마자 걷어 환기", "10분 뒤 반듯하게 펴기", "주 1회 이불 털기"),
            "침대 정리 30초"
        ),
        chore(
            master.id, "옷 정리", PARTNER, WeeklyNTimes(1),
            listOf("의자 위 옷부터 처리", "계절 아닌 옷은 상단칸", "안 입는 옷 3벌 비우기"),
            "옷장 정리 방법"
        ),
        chore(
            mbath.id, "거울 닦기", ME, WeeklyNTimes(1),
            listOf("물 스프레이 후 스퀴지", "마른 극세사로 마무리", "세면대 튄 자국까지"),
            "거울 얼룩 없이 닦기"
        ),
        chore(
            lveranda.id, "빨래 개기", ME, Daily,
            listOf("드라마 한 편 = 바구니 하나", "옷장 칸별로 쌓기", "갠 즉시 넣기"),
            "빨래 빨리 개는 법"
        )
    )
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.DefaultRoomsTest"`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 5: chore 모듈 전체 + 루트 빌드 검증**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1~6에서 작성한 모든 테스트 통과)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — 전체 모듈(6개)이 컴파일되고 모든 테스트가 통과한다.

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/DefaultRooms.kt chore/src/test/kotlin/sallim/chore/domain/DefaultRoomsTest.kt
git commit -m "feat: DefaultRooms 시드 데이터 포팅 (방 10개, 집안일 18개)"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- 모듈 의존성(`chore`→`common`만) → Task 1
- `Room`/`RoomPlacement`/`FloorPlan`(경계 검증) → Task 1
- `MemberId`(chore 자체 정의), `RecurrencePolicy` 3종 → Task 2
- `ChoreDefinition` → Task 3
- `ChoreInstance`(AggregateRoot) + `ChoreCompletedEvent` → Task 4
- `CleanlinessScore`(모바일 공식과 동일 상수) → Task 5
- `DefaultRooms` 시드 데이터(방 10개/할일 18개) → Task 6
- 제외 항목(JPA/API/스케줄러/Kafka/CQRS/household 연동) → 계획에 포함하지 않음, Global Constraints에 명시

**Placeholder scan:** 전 단계 실제 코드/커맨드 포함. TBD/TODO 없음.

**Type consistency:** `RoomId`/`Room`/`RoomPlacement`/`FloorPlan`(Task1) → `MemberId`/`RecurrencePolicy`(Task2) → `ChoreDefinitionId`/`ChoreDefinition`(Task3) → `ChoreInstanceId`/`ChoreCompletedEvent`/`ChoreInstance`(Task4) → `CleanlinessScore`(Task5) → `DefaultRooms`(Task6) 순서로 각 태스크의 Produces가 다음 태스크의 Consumes와 시그니처 일치. `DefaultRooms`는 Task 1/2/3의 생성자 시그니처를 그대로 사용.
