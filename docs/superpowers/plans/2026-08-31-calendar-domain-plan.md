# Calendar(캘린더) 도메인 모델 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `RecurrencePolicy`를 `chore`에서 `common`으로 이전해 컨텍스트 간 공유 가능하게 만들고, 그 위에 `calendar` 모듈의 첫 도메인 모델(`CalendarEvent`)을 추가한다.

**Architecture:** `RecurrencePolicy`는 chore/room 같은 컨텍스트 전용 타입을 참조하지 않는 순수 시간 개념이라 `sallim.common.domain`(이미 `Identifier`/`AggregateRoot`/`DomainEvent`가 있는 공유 커널)으로 옮긴다. `CalendarEvent`는 정의+반복을 하나의 클래스로 갖고, 조회 시점에 `occurrencesIn(from, to)`으로 즉석 계산 — 별도 인스턴스 테이블이나 스케줄러 없음.

**Tech Stack:** Kotlin(JDK 21) — 신규 의존성 없음, `calendar/build.gradle.kts`는 이번에 처음 채워짐

**Spec:** `docs/superpowers/specs/2026-08-31-calendar-domain-design.md`

## Global Constraints

- `RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`의 로직은 변경하지 않는다 — 패키지 이전만
- `calendar`는 `MemberId`를 자체 복제한다 — `household`/`chore`의 `MemberId`를 참조하지 않는다
- `CalendarEvent`는 정의+반복 통합, 별도 인스턴스/스케줄러 없음 — occurrence는 항상 조회 시점 계산
- 반복 일정에 종료일(until) 없음 — 조회 범위로만 경계가 생긴다
- 이번 서브프로젝트는 도메인 모델 + 단위 테스트까지만 — 영속성/API는 범위 밖

---

## File Structure

```
common/src/main/kotlin/sallim/common/domain/
  RecurrencePolicy.kt                                            (신규 — chore에서 이전, 로직 동일)
common/src/test/kotlin/sallim/common/domain/
  RecurrencePolicyTest.kt                                        (신규 — chore에서 이전, 내용 동일)
chore/src/main/kotlin/sallim/chore/domain/
  RecurrencePolicy.kt                                            (삭제)
  ChoreDefinition.kt                                              (수정 — import 추가)
  DefaultRooms.kt                                                 (수정 — import 추가)
chore/src/main/kotlin/sallim/chore/application/
  ChoreDefinitionService.kt                                       (수정 — import 경로 변경)
chore/src/main/kotlin/sallim/chore/api/
  ChoreDefinitionController.kt                                    (수정 — import 경로 변경)
chore/src/main/kotlin/sallim/chore/infrastructure/persistence/
  ChoreDefinitionRepositoryAdapter.kt                             (수정 — import 경로 변경)
chore/src/test/kotlin/sallim/chore/domain/
  RecurrencePolicyTest.kt                                         (삭제)
  ChoreDefinitionTest.kt                                          (수정 — import 경로 변경)
chore/src/test/kotlin/sallim/chore/application/
  ChoreDefinitionServiceTest.kt                                   (수정 — import 경로 변경)
  ChoreInstanceServiceTest.kt                                     (수정 — import 경로 변경)
  CleanlinessServiceTest.kt                                       (수정 — import 경로 변경)
  RoomServiceTest.kt                                              (수정 — import 경로 변경)
chore/src/test/kotlin/sallim/chore/api/
  ChoreDefinitionControllerTest.kt                                (수정 — import 경로 변경)
  ChoreInstanceSchedulerTest.kt                                   (수정 — import 경로 변경)
chore/src/test/kotlin/sallim/chore/infrastructure/persistence/
  ChoreDefinitionRepositoryAdapterTest.kt                         (수정 — import 경로 변경)
calendar/build.gradle.kts                                         (수정 — 지금 완전히 빈 파일)
calendar/src/main/kotlin/sallim/calendar/domain/
  CalendarEventId.kt                                              (신규)
  MemberId.kt                                                     (신규)
  CalendarEvent.kt                                                (신규)
calendar/src/test/kotlin/sallim/calendar/domain/
  CalendarEventTest.kt                                            (신규)
```

---

### Task 1: `RecurrencePolicy`를 `common`으로 이전

**Files:**
- Create: `common/src/main/kotlin/sallim/common/domain/RecurrencePolicy.kt`
- Create: `common/src/test/kotlin/sallim/common/domain/RecurrencePolicyTest.kt`
- Delete: `chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt`
- Delete: `chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt`
- Modify: 13개 chore 파일 (아래 Step 4에 파일별 정확한 변경 내용)

**Interfaces:**
- Consumes: 없음 (순수 이전, 로직 변경 없음)
- Produces: `sallim.common.domain.RecurrencePolicy`(sealed interface) / `Daily`/`WeeklyNTimes`/`Monthly` — Task 2의 `CalendarEvent`가 이 타입들을 그대로 쓴다. chore 쪽 13개 파일도 이 새 경로를 가리키게 된다.

- [ ] **Step 1: `common`에 새 파일 작성 (내용은 기존과 완전히 동일, package 줄만 다름)**

`common/src/main/kotlin/sallim/common/domain/RecurrencePolicy.kt`:
```kotlin
package sallim.common.domain

import java.time.LocalDate

sealed interface RecurrencePolicy {
    fun nextOccurrence(after: LocalDate): LocalDate
}

data object Daily : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusDays(1)
}

// ponytail: 요일 지정 없는 균등 분배 근사치(7일/times) — 특정 요일 반복이 필요해지면(갭#1)
// 요일 집합을 받는 정책으로 교체
// ponytail: times가 4~6이면 정수 나눗셈(7/times)이 1일 간격으로 절삭됨 — 위와 동일한 요일 기반 재설계(갭#1)로 해소
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

`common/src/test/kotlin/sallim/common/domain/RecurrencePolicyTest.kt`:
```kotlin
package sallim.common.domain

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

    test("WeeklyNTimes(5)는 정수 나눗셈으로 1일 뒤를 반환한다 (근사치 한계)") {
        WeeklyNTimes(5).nextOccurrence(today) shouldBe today.plusDays(1)
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

- [ ] **Step 2: 옛 파일 삭제**

```bash
rm chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt
rm chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt
```

- [ ] **Step 3: 컴파일 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` (이 셸에 `JAVA_HOME`이 PATH에 없음) 후 `./gradlew :chore:compileKotlin :chore:compileTestKotlin`
Expected: FAIL — `RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`를 못 찾는 "unresolved reference" 컴파일 에러가 아래 13개 파일에서 발생.

- [ ] **Step 4: 13개 chore 파일 수정 (파일별 정확한 변경)**

각 파일은 사용하는 심볼만큼만 `sallim.common.domain.*` import를 추가/변경한다. 기존 import 정렬 순서(대체로 `io.*`/`org.*` → `sallim.chore.*` → `java.*`)는 기능적으로 무관하니 엄격히 맞출 필요 없음 — 컴파일만 통과하면 된다. 각 파일에 필요한 최종 import 목록:

1. **`chore/src/main/kotlin/sallim/chore/domain/ChoreDefinition.kt`** — 지금 import 블록이 아예 없음(같은 패키지라 안 씀). `package sallim.chore.domain` 줄 바로 아래에 빈 줄 하나 두고 추가:
   ```kotlin
   import sallim.common.domain.RecurrencePolicy
   ```

2. **`chore/src/main/kotlin/sallim/chore/domain/DefaultRooms.kt`** — 마찬가지로 import 블록 없음. 추가:
   ```kotlin
   import sallim.common.domain.Daily
   import sallim.common.domain.Monthly
   import sallim.common.domain.RecurrencePolicy
   import sallim.common.domain.WeeklyNTimes
   ```

3. **`chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt`** — 기존 `import sallim.chore.domain.RecurrencePolicy` 줄을 아래로 교체:
   ```kotlin
   import sallim.common.domain.RecurrencePolicy
   ```

4. **`chore/src/main/kotlin/sallim/chore/api/ChoreDefinitionController.kt`** — 기존 `import sallim.chore.domain.Daily`, `import sallim.chore.domain.Monthly`, `import sallim.chore.domain.RecurrencePolicy` 세 줄을 아래로 교체(`WeeklyNTimes`도 파일 안에서 쓰이므로 이것도 추가):
   ```kotlin
   import sallim.common.domain.Daily
   import sallim.common.domain.Monthly
   import sallim.common.domain.RecurrencePolicy
   import sallim.common.domain.WeeklyNTimes
   ```

5. **`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt`** — 기존 `import sallim.chore.domain.Daily`, `import sallim.chore.domain.Monthly`, `import sallim.chore.domain.RecurrencePolicy`, `import sallim.chore.domain.WeeklyNTimes` 네 줄을 아래로 교체:
   ```kotlin
   import sallim.common.domain.Daily
   import sallim.common.domain.Monthly
   import sallim.common.domain.RecurrencePolicy
   import sallim.common.domain.WeeklyNTimes
   ```

6. **`chore/src/test/kotlin/sallim/chore/domain/ChoreDefinitionTest.kt`** — 같은 패키지라 import 없이 `Daily` 등을 썼었는데 이제 다른 패키지가 됐으므로 새로 추가 (파일에서 실제 쓰는 게 `Daily`뿐인지 확인 후, 쓰는 것만):
   ```kotlin
   import sallim.common.domain.Daily
   ```

7. **`chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt`** — 기존 `import sallim.chore.domain.Daily` 교체:
   ```kotlin
   import sallim.common.domain.Daily
   ```

8. **`chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt`** — 기존 `import sallim.chore.domain.Daily`, `import sallim.chore.domain.Monthly`, `import sallim.chore.domain.RecurrencePolicy`, `import sallim.chore.domain.WeeklyNTimes` 네 줄 교체:
   ```kotlin
   import sallim.common.domain.Daily
   import sallim.common.domain.Monthly
   import sallim.common.domain.RecurrencePolicy
   import sallim.common.domain.WeeklyNTimes
   ```

9. **`chore/src/test/kotlin/sallim/chore/application/CleanlinessServiceTest.kt`** — 기존 `import sallim.chore.domain.Daily` 교체:
   ```kotlin
   import sallim.common.domain.Daily
   ```

10. **`chore/src/test/kotlin/sallim/chore/application/RoomServiceTest.kt`** — 기존 `import sallim.chore.domain.Daily` 교체:
    ```kotlin
    import sallim.common.domain.Daily
    ```

11. **`chore/src/test/kotlin/sallim/chore/api/ChoreDefinitionControllerTest.kt`** — 기존 `import sallim.chore.domain.Daily` 교체:
    ```kotlin
    import sallim.common.domain.Daily
    ```

12. **`chore/src/test/kotlin/sallim/chore/api/ChoreInstanceSchedulerTest.kt`** — 기존 `import sallim.chore.domain.Daily` 교체:
    ```kotlin
    import sallim.common.domain.Daily
    ```

13. **`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt`** — 기존 `import sallim.chore.domain.Daily`, `import sallim.chore.domain.Monthly`, `import sallim.chore.domain.RecurrencePolicy`, `import sallim.chore.domain.WeeklyNTimes` 네 줄 교체:
    ```kotlin
    import sallim.common.domain.Daily
    import sallim.common.domain.Monthly
    import sallim.common.domain.RecurrencePolicy
    import sallim.common.domain.WeeklyNTimes
    ```

`chore/build.gradle.kts`는 이미 `implementation(project(":common"))`을 갖고 있으므로 빌드 설정 변경 불필요.

- [ ] **Step 5: 컴파일 → 통과 확인**

Run: `./gradlew :common:compileKotlin :common:compileTestKotlin :chore:compileKotlin :chore:compileTestKotlin`
Expected: `BUILD SUCCESSFUL` — unresolved reference 에러 전부 해소.

- [ ] **Step 6: 전체 테스트 실행 (Docker 있으면 전체, 없으면 Docker 불필요한 것만)**

Run: `./gradlew :common:test`
Expected: PASS — `RecurrencePolicyTest` 7개 전부 통과 (로직 변경 없으니 그대로).

Run: `./gradlew :chore:test --tests "sallim.chore.domain.*" --tests "sallim.chore.application.*" --tests "sallim.chore.api.*"`
Expected: PASS — Docker 불필요한 도메인/application/api 테스트 전부 여전히 통과. (persistence 통합 테스트는 Docker 필요 — 환경에 따라 별도 확인)

- [ ] **Step 7: Commit**

```bash
git add common/src/main/kotlin/sallim/common/domain/RecurrencePolicy.kt common/src/test/kotlin/sallim/common/domain/RecurrencePolicyTest.kt chore/src/main/kotlin/sallim/chore/domain/ChoreDefinition.kt chore/src/main/kotlin/sallim/chore/domain/DefaultRooms.kt chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt chore/src/main/kotlin/sallim/chore/api/ChoreDefinitionController.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt chore/src/test/kotlin/sallim/chore/domain/ChoreDefinitionTest.kt chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt chore/src/test/kotlin/sallim/chore/application/CleanlinessServiceTest.kt chore/src/test/kotlin/sallim/chore/application/RoomServiceTest.kt chore/src/test/kotlin/sallim/chore/api/ChoreDefinitionControllerTest.kt chore/src/test/kotlin/sallim/chore/api/ChoreInstanceSchedulerTest.kt chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt
git rm chore/src/main/kotlin/sallim/chore/domain/RecurrencePolicy.kt chore/src/test/kotlin/sallim/chore/domain/RecurrencePolicyTest.kt
git commit -m "refactor: RecurrencePolicy를 chore에서 common으로 이전"
```

---

### Task 2: `CalendarEvent` 도메인 모델

**Files:**
- Modify: `calendar/build.gradle.kts` (지금 완전히 빈 파일)
- Create: `calendar/src/main/kotlin/sallim/calendar/domain/CalendarEventId.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/domain/MemberId.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/domain/CalendarEvent.kt`
- Test: `calendar/src/test/kotlin/sallim/calendar/domain/CalendarEventTest.kt`

**Interfaces:**
- Consumes: `sallim.common.domain.RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`(Task 1), `sallim.common.domain.Identifier`(기존)
- Produces: `class CalendarEvent(id, title, startAt, memberId, memo, recurrence) { fun occurrencesIn(from: LocalDate, to: LocalDate): List<LocalDateTime> }` — 이 서브프로젝트의 마지막 태스크라 이후 소비자 없음. 다음 서브프로젝트(영속성)가 이 클래스를 그대로 저장 대상으로 쓴다.

- [ ] **Step 1: `calendar/build.gradle.kts` 채우기**

`calendar/build.gradle.kts` (지금 빈 파일, 아래 내용으로 채움 — `chore/build.gradle.kts`가 `common` 의존하는 것과 동일 패턴, Kotlin 플러그인/Kotest는 루트 `build.gradle.kts`의 `subprojects {}`가 이미 모든 서브프로젝트에 자동 적용하므로 여기선 프로젝트 의존성만 추가):

```kotlin
dependencies {
    implementation(project(":common"))
}
```

- [ ] **Step 2: `CalendarEventId`, `MemberId` 작성**

순수 ID 래퍼라 별도 테스트 없이 바로 작성한다 (chore의 `RoomId`/`MemberId` 등과 동일 스타일 — 이미 검증된 패턴).

`calendar/src/main/kotlin/sallim/calendar/domain/CalendarEventId.kt`:
```kotlin
package sallim.calendar.domain

import sallim.common.domain.Identifier
import java.util.UUID

class CalendarEventId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): CalendarEventId = CalendarEventId(UUID.randomUUID())
    }
}
```

`calendar/src/main/kotlin/sallim/calendar/domain/MemberId.kt`:
```kotlin
package sallim.calendar.domain

import sallim.common.domain.Identifier
import java.util.UUID

class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`calendar/src/test/kotlin/sallim/calendar/domain/CalendarEventTest.kt`:
```kotlin
package sallim.calendar.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventTest : FunSpec({
    fun event(startAt: LocalDateTime, recurrence: sallim.common.domain.RecurrencePolicy? = null, title: String = "일정") =
        CalendarEvent(CalendarEventId.generate(), title, startAt, MemberId.generate(), null, recurrence)

    test("제목이 공백이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            event(LocalDateTime.of(2026, 8, 20, 15, 0), title = " ")
        }
    }

    test("단일 일정은 조회 범위 안에 있으면 자기 자신 하나를 반환한다") {
        val startAt = LocalDateTime.of(2026, 8, 20, 15, 0)
        val single = event(startAt)

        val result = single.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first() shouldBe startAt
    }

    test("단일 일정은 조회 범위 밖이면 빈 목록을 반환한다") {
        val single = event(LocalDateTime.of(2026, 9, 1, 15, 0))

        val result = single.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result.shouldBeEmpty()
    }

    test("Daily 반복은 범위 안의 모든 날짜에 같은 시각으로 나타난다") {
        val startAt = LocalDateTime.of(2026, 8, 18, 9, 30)
        val recurring = event(startAt, Daily)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 22))

        result shouldHaveSize 3
        result shouldBe listOf(
            LocalDateTime.of(2026, 8, 20, 9, 30),
            LocalDateTime.of(2026, 8, 21, 9, 30),
            LocalDateTime.of(2026, 8, 22, 9, 30)
        )
    }

    test("WeeklyNTimes 반복도 계산된 간격으로 범위 안에 나타난다") {
        val startAt = LocalDateTime.of(2026, 8, 1, 18, 0)
        val recurring = event(startAt, WeeklyNTimes(2))  // nextOccurrence는 7/2=3일 간격

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10))

        result shouldBe listOf(
            LocalDateTime.of(2026, 8, 1, 18, 0),
            LocalDateTime.of(2026, 8, 4, 18, 0),
            LocalDateTime.of(2026, 8, 7, 18, 0),
            LocalDateTime.of(2026, 8, 10, 18, 0)
        )
    }

    test("Monthly 반복도 계산된 간격으로 범위 안에 나타난다") {
        val startAt = LocalDateTime.of(2026, 6, 15, 12, 0)
        val recurring = event(startAt, Monthly)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first() shouldBe LocalDateTime.of(2026, 8, 15, 12, 0)
    }

    test("반복 일정도 시작일이 조회 범위보다 늦으면 빈 목록을 반환한다") {
        val recurring = event(LocalDateTime.of(2026, 9, 1, 9, 0), Daily)

        val result = recurring.occurrencesIn(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result.shouldBeEmpty()
    }
})
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :calendar:compileTestKotlin`
Expected: FAIL — `CalendarEvent`가 없어 컴파일 에러

- [ ] **Step 5: `CalendarEvent` 구현**

`calendar/src/main/kotlin/sallim/calendar/domain/CalendarEvent.kt`:
```kotlin
package sallim.calendar.domain

import sallim.common.domain.RecurrencePolicy
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEvent(
    val id: CalendarEventId,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: MemberId,
    val memo: String?,
    val recurrence: RecurrencePolicy?
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    fun occurrencesIn(from: LocalDate, to: LocalDate): List<LocalDateTime> {
        if (recurrence == null) {
            val date = startAt.toLocalDate()
            return if (date in from..to) listOf(startAt) else emptyList()
        }
        val result = mutableListOf<LocalDateTime>()
        var date = startAt.toLocalDate()
        while (!date.isAfter(to)) {
            if (!date.isBefore(from)) result += date.atTime(startAt.toLocalTime())
            date = recurrence.nextOccurrence(date)
        }
        return result
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :calendar:test`
Expected: PASS (7개 테스트 모두 통과, Docker 불필요 — 순수 도메인 로직)

- [ ] **Step 7: 전체 저장소 컴파일 검증**

Run: `./gradlew compileKotlin compileTestKotlin`
Expected: `BUILD SUCCESSFUL` — 모든 모듈이 여전히 컴파일된다 (Docker 불필요, 컴파일만).

- [ ] **Step 8: Commit**

```bash
git add calendar/build.gradle.kts calendar/src/main/kotlin/sallim/calendar/domain/CalendarEventId.kt calendar/src/main/kotlin/sallim/calendar/domain/MemberId.kt calendar/src/main/kotlin/sallim/calendar/domain/CalendarEvent.kt calendar/src/test/kotlin/sallim/calendar/domain/CalendarEventTest.kt
git commit -m "feat: CalendarEvent 도메인 모델 + occurrencesIn 반복 계산"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- `RecurrencePolicy`를 `common`으로 이전 → Task 1 (로직 무변경, 13개 chore 파일 import 수정 확인 완료 — 실제 코드베이스를 grep해서 정확한 파일 목록과 사용 심볼을 확인했음)
- `CalendarEvent` 하나로 정의+반복 통합, 인스턴스 테이블/스케줄러 없음 → Task 2
- `occurrencesIn`으로 즉석 계산, 종료일 없음 → Task 2 (`CalendarEvent.occurrencesIn`)
- calendar가 `MemberId` 자체 복제 → Task 2 (`sallim.calendar.domain.MemberId`, household/chore 참조 없음)
- `calendar/build.gradle.kts` 배선 → Task 2 Step 1
- 영속성/API는 범위 밖 → 어느 태스크도 이런 파일 안 만듦

**Placeholder scan:** 전 단계 실제 코드/커맨드 포함. TBD/TODO 없음. Task 1의 13개 파일 변경 목록은 막연한 "import 경로 고치기"가 아니라 각 파일이 실제로 쓰는 심볼(grep으로 확인)과 정확한 최종 import 문을 명시.

**Type consistency:** `RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`(Task1, `sallim.common.domain`) → `CalendarEvent.recurrence: RecurrencePolicy?`(Task2, 동일 타입 재사용) → `occurrencesIn`이 `recurrence.nextOccurrence(date)`를 호출(Task1의 시그니처 `nextOccurrence(after: LocalDate): LocalDate`와 정확히 일치). `Identifier<UUID>`(기존 common) → `CalendarEventId`/`MemberId`(Task2, 상속) 시그니처 일치.
