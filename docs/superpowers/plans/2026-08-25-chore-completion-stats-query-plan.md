# Chore(집안일) 완료 통계 조회(CQRS) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chore_completion_record` fact 테이블 위에 조회 전용 REST API를 추가한다 — 기간(`from`/`to`)을 주면 멤버별 완료 개수를 반환.

**Architecture:** 커맨드 포트(`ChoreCompletionRecordRepository`, Kafka 소비자 전용)와 별도로 쿼리 포트(`ChoreCompletionStatsQuery`)를 두어 CQRS를 분리한다. 같은 테이블/같은 Spring Data JPA 인터페이스(`ChoreCompletionRecordJpaRepository`)를 재사용하되, GROUP BY 쿼리 하나로 멤버별 집계를 계산하는 어댑터만 새로 추가한다.

**Tech Stack:** Kotlin(JDK 21) / Spring Boot 3.3.4 / Spring Data JPA(파생 프로젝션) — 신규 의존성 없음

**Spec:** `docs/superpowers/specs/2026-08-25-chore-completion-stats-query-design.md`

## Global Constraints

- 커맨드 포트(`ChoreCompletionRecordRepository`)는 건드리지 않는다 — 새 쿼리 포트만 추가
- 기간은 클라이언트가 명시적 `from`/`to`(LocalDate)로 보낸다 — 서버가 "이번 주"/"이번 달" 같은 프리셋을 해석하지 않는다
- 날짜 → `Instant` 경계 변환은 `ZoneId.of("Asia/Seoul")` 고정 (스케줄러/API 서브프로젝트에서 이미 확립된 컨벤션)
- `to` 날짜는 그 날 전체(00:00 ~ 다음날 00:00 직전)를 포함
- `from > to`면 `IllegalArgumentException`(→ 기존 `ApiExceptionHandler`가 400으로 매핑)
- 방/집안일 정의별 통계, 프리셋 기간 계산은 범위 밖

---

## File Structure

```
chore/src/main/kotlin/sallim/chore/domain/
  ChoreCompletionStatsQuery.kt                                   (신규 — 포트 + MemberCompletionCount)
chore/src/main/kotlin/sallim/chore/infrastructure/persistence/
  ChoreCompletionRecordJpaRepository.kt                          (수정 — GROUP BY 쿼리 + MemberCountProjection 추가)
  JpaChoreCompletionStatsQuery.kt                                (신규 — 쿼리 어댑터)
chore/src/test/kotlin/sallim/chore/infrastructure/persistence/
  JpaChoreCompletionStatsQueryTest.kt                            (신규)
chore/src/main/kotlin/sallim/chore/application/
  ChoreStatsService.kt                                           (신규)
chore/src/test/kotlin/sallim/chore/application/
  FakeChoreCompletionStatsQuery.kt                               (신규)
  ChoreStatsServiceTest.kt                                       (신규)
chore/src/main/kotlin/sallim/chore/api/
  ChoreStatsController.kt                                        (신규)
chore/src/test/kotlin/sallim/chore/api/
  ChoreStatsControllerTest.kt                                    (신규)
```

---

### Task 1: 쿼리 포트 + JPA 어댑터 (GROUP BY 집계)

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionStatsQuery.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQuery.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQueryTest.kt`

**Interfaces:**
- Consumes: `ChoreCompletionRecordRepository.save(...)`(기존, 테스트 데이터 세팅에 사용), `MemberId`(기존)
- Produces: `interface ChoreCompletionStatsQuery { fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> }`, `data class MemberCompletionCount(val memberId: MemberId, val count: Long)` — Task 2의 `ChoreStatsService`가 이 포트를 그대로 주입받아 쓴다.

- [ ] **Step 1: 쿼리 포트 작성**

순수 인터페이스라 별도 테스트 없이 바로 작성한다 (기존 리포지토리 포트들과 동일 스타일).

`chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionStatsQuery.kt`:
```kotlin
package sallim.chore.domain

import java.time.LocalDate

interface ChoreCompletionStatsQuery {
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount>
}

data class MemberCompletionCount(val memberId: MemberId, val count: Long)
```

- [ ] **Step 2: `ChoreCompletionRecordJpaRepository`에 GROUP BY 쿼리 추가**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt` 전체를 아래로 교체:

```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String> {
    fun existsByChoreInstanceId(choreInstanceId: String): Boolean

    @Query(
        "SELECT r.completedBy AS memberId, COUNT(r) AS count " +
            "FROM ChoreCompletionRecordEntity r " +
            "WHERE r.completedAt >= :from AND r.completedAt < :to " +
            "GROUP BY r.completedBy"
    )
    fun countByMemberBetween(@Param("from") from: Instant, @Param("to") to: Instant): List<MemberCountProjection>
}

interface MemberCountProjection {
    val memberId: String
    val count: Long
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQueryTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.LocalDate
import java.time.ZoneId

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaChoreCompletionRecordRepository::class, JpaChoreCompletionStatsQuery::class)
class JpaChoreCompletionStatsQueryTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var recordRepository: JpaChoreCompletionRecordRepository

    @Autowired
    lateinit var statsQuery: JpaChoreCompletionStatsQuery

    @Autowired
    lateinit var em: TestEntityManager

    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `기간 안의 기록만 멤버별로 집계된다`() {
        val member1 = MemberId.generate()
        val member2 = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()

        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 8, 10).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 8, 15).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member2, LocalDate.of(2026, 8, 12).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member1, LocalDate.of(2026, 7, 31).atStartOfDay(zone).toInstant())
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member2, LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 2
        result.first { it.memberId == member1 }.count shouldBe 2L
        result.first { it.memberId == member2 }.count shouldBe 1L
    }

    @Test
    fun `to 날짜 당일 기록도 포함된다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 8, 31).atTime(23, 0).atZone(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first().count shouldBe 1L
    }

    @Test
    fun `범위 밖에만 기록이 있으면 빈 목록을 반환한다`() {
        val member = MemberId.generate()
        val definitionId = ChoreDefinitionId.generate()
        recordRepository.save(ChoreInstanceId.generate(), definitionId, member, LocalDate.of(2026, 9, 1).atStartOfDay(zone).toInstant())
        em.flush()
        em.clear()

        val result = statsQuery.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 0
    }
}
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:compileTestKotlin`
Expected: FAIL — `JpaChoreCompletionStatsQuery`가 없어 컴파일 에러

- [ ] **Step 5: `JpaChoreCompletionStatsQuery` 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQuery.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class JpaChoreCompletionStatsQuery(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionStatsQuery {
    override fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        val zone = ZoneId.of("Asia/Seoul")
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(zone).toInstant()
        return jpaRepository.countByMemberBetween(fromInstant, toInstant)
            .map { MemberCompletionCount(MemberId(UUID.fromString(it.memberId)), it.count) }
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.JpaChoreCompletionStatsQueryTest"`
Expected: PASS (3개 테스트 모두 통과). 실패 시 가장 먼저 Docker Desktop이 실행 중인지 확인.

- [ ] **Step 7: 기존 완료 기록 테스트가 안 깨지는지 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.JpaChoreCompletionRecordRepositoryTest"`
Expected: PASS — `ChoreCompletionRecordJpaRepository`에 메서드만 추가했고 기존 `save`/`existsByChoreInstanceId` 동작은 변경 없음.

- [ ] **Step 8: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionStatsQuery.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQuery.kt chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionStatsQueryTest.kt
git commit -m "feat: 완료 통계 쿼리 포트 + JPA GROUP BY 어댑터"
```

---

### Task 2: `ChoreStatsService`

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/application/ChoreStatsService.kt`
- Create: `chore/src/test/kotlin/sallim/chore/application/FakeChoreCompletionStatsQuery.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreStatsServiceTest.kt`

**Interfaces:**
- Consumes: `ChoreCompletionStatsQuery.countByMember(from, to)`(Task 1)
- Produces: `class ChoreStatsService(query: ChoreCompletionStatsQuery) { fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> }` — Task 3의 `ChoreStatsController`가 이 서비스를 주입받아 쓴다.

- [ ] **Step 1: 페이크 작성**

`chore/src/test/kotlin/sallim/chore/application/FakeChoreCompletionStatsQuery.kt`:
```kotlin
package sallim.chore.application

import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate

class FakeChoreCompletionStatsQuery : ChoreCompletionStatsQuery {
    var result: List<MemberCompletionCount> = emptyList()
    var lastFrom: LocalDate? = null
    var lastTo: LocalDate? = null

    override fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        lastFrom = from
        lastTo = to
        return result
    }
}
```

- [ ] **Step 2: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/ChoreStatsServiceTest.kt`:
```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreStatsServiceTest : FunSpec({
    test("쿼리 포트 결과를 그대로 반환한다") {
        val query = FakeChoreCompletionStatsQuery()
        val member = MemberId.generate()
        query.result = listOf(MemberCompletionCount(member, 3L))
        val service = ChoreStatsService(query)

        val result = service.countByMember(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        result shouldHaveSize 1
        result.first().memberId shouldBe member
        result.first().count shouldBe 3L
        query.lastFrom shouldBe LocalDate.of(2026, 8, 1)
        query.lastTo shouldBe LocalDate.of(2026, 8, 31)
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = ChoreStatsService(FakeChoreCompletionStatsQuery())

        shouldThrow<IllegalArgumentException> {
            service.countByMember(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1))
        }
    }
})
```

- [ ] **Step 3: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.application.ChoreStatsServiceTest"`
Expected: FAIL — `ChoreStatsService`가 없어 컴파일 에러

- [ ] **Step 4: `ChoreStatsService` 구현**

`chore/src/main/kotlin/sallim/chore/application/ChoreStatsService.kt`:
```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate

@Service
class ChoreStatsService(private val query: ChoreCompletionStatsQuery) {
    @Transactional(readOnly = true)
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        return query.countByMember(from, to)
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreStatsServiceTest"`
Expected: PASS (2개 테스트 모두 통과, Docker 불필요)

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/application/ChoreStatsService.kt chore/src/test/kotlin/sallim/chore/application/FakeChoreCompletionStatsQuery.kt chore/src/test/kotlin/sallim/chore/application/ChoreStatsServiceTest.kt
git commit -m "feat: ChoreStatsService — 멤버별 완료 개수 조회"
```

---

### Task 3: `ChoreStatsController` + 최종 빌드 검증

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/api/ChoreStatsController.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/ChoreStatsControllerTest.kt`

**Interfaces:**
- Consumes: `ChoreStatsService.countByMember(from, to)`(Task 2)
- Produces: 이 태스크가 계획의 마지막이라 이후 소비자 없음. `./gradlew build` 전체 검증으로 마무리.

- [ ] **Step 1: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/api/ChoreStatsControllerTest.kt`:
```kotlin
package sallim.chore.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreStatsService
import sallim.chore.application.FakeChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId

@WebMvcTest(ChoreStatsController::class)
@Import(ChoreStatsControllerTest.TestConfig::class)
class ChoreStatsControllerTest {

    @TestConfiguration
    class TestConfig {
        val query = FakeChoreCompletionStatsQuery()

        @Bean
        fun choreStatsService(): ChoreStatsService = ChoreStatsService(query)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var testConfig: TestConfig

    @Test
    fun `기간과 함께 요청하면 멤버별 개수를 반환한다`() {
        val member = MemberId.generate()
        testConfig.query.result = listOf(MemberCompletionCount(member, 5L))

        mockMvc.perform(get("/api/chore-stats").param("from", "2026-08-01").param("to", "2026-08-31"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].memberId").value(member.value.toString()))
            .andExpect(jsonPath("$[0].count").value(5))
    }

    @Test
    fun `from 파라미터가 없으면 400`() {
        mockMvc.perform(get("/api/chore-stats").param("to", "2026-08-31"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `from이 to보다 늦으면 400`() {
        mockMvc.perform(get("/api/chore-stats").param("from", "2026-08-31").param("to", "2026-08-01"))
            .andExpect(status().isBadRequest)
    }
}
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.api.ChoreStatsControllerTest"`
Expected: FAIL — `ChoreStatsController`가 없어 컴파일 에러

- [ ] **Step 3: `ChoreStatsController` 구현**

`chore/src/main/kotlin/sallim/chore/api/ChoreStatsController.kt`:
```kotlin
package sallim.chore.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreStatsService
import sallim.chore.domain.MemberCompletionCount
import java.time.LocalDate
import java.util.UUID

data class MemberCompletionCountResponse(val memberId: UUID, val count: Long)

@RestController
@RequestMapping("/api/chore-stats")
class ChoreStatsController(private val statsService: ChoreStatsService) {

    @GetMapping
    fun countByMember(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): List<MemberCompletionCountResponse> =
        statsService.countByMember(from, to).map { it.toResponse() }

    private fun MemberCompletionCount.toResponse() = MemberCompletionCountResponse(memberId.value, count)
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreStatsControllerTest"`
Expected: PASS (3개 테스트 모두 통과, Docker 불필요)

- [ ] **Step 5: chore 모듈 전체 + 루트 빌드 검증 (Docker Desktop 필요)**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1~3에서 작성한 모든 테스트 통과 — application/api 테스트 + persistence 통합 테스트)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/api/ChoreStatsController.kt chore/src/test/kotlin/sallim/chore/api/ChoreStatsControllerTest.kt
git commit -m "feat: 완료 통계 조회 REST API"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- 커맨드/쿼리 포트 분리 → Task 1(`ChoreCompletionStatsQuery` 신규, `ChoreCompletionRecordRepository` 무변경)
- GROUP BY 집계 쿼리 → Task 1
- Asia/Seoul 날짜 경계 변환, `to` 날짜 포함 → Task 1 (`JpaChoreCompletionStatsQuery`)
- `from > to` 400 → Task 2 (`ChoreStatsService`의 `require`)
- REST API + 파라미터 누락 400 → Task 3
- 방/정의별 통계, 프리셋 기간 계산 범위 밖 → 어느 태스크도 이런 기능 안 만듦

**Placeholder scan:** 전 단계 실제 코드/커맨드 포함. TBD/TODO 없음.

**Type consistency:** `ChoreCompletionStatsQuery.countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount>`(Task1) → `ChoreStatsService.countByMember(from, to)`(Task2, 동일 시그니처로 위임) → `ChoreStatsController.countByMember(from, to)`(Task3, HTTP 파라미터를 그대로 서비스에 전달) 순서로 각 태스크의 Produces가 다음 태스크의 Consumes와 시그니처 일치. `MemberCompletionCount(memberId: MemberId, count: Long)`은 Task1에서 정의되고 Task2/3에서 그대로 사용.
