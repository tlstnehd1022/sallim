# Calendar 영속성 + API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `CalendarEvent` 도메인 모델에 JPA 영속성과 REST API(조회/CRUD)를 추가한다.

**Architecture:** chore-persistence/chore-api가 확립한 hexagonal 어댑터 패턴(domain 포트 → JPA 어댑터 → application 서비스 → REST 컨트롤러)을 그대로 따르되, `CalendarEvent`가 단일 엔티티라 영속성+API를 한 서브프로젝트(3개 태스크)로 묶는다.

**Tech Stack:** Kotlin, Spring Boot 3.x, Spring Data JPA, Flyway, MySQL(Testcontainers), Kotest.

**Spec:** `docs/superpowers/specs/2026-09-04-calendar-persistence-api-design.md`

## Global Constraints

- 영속성+API를 한 서브프로젝트(한 브랜치)로 묶는다 — chore처럼 나누지 않는다.
- 조회 API(`GET /api/calendar-events`) 응답은 반복을 펼친 개별 발생 목록이며, 각 항목은 `eventId`(정의 공유) + `occurredAt`(발생별로 다름)로 식별한다.
- `RecurrencePolicy` ↔ DB 컬럼(`recurrenceType`, `recurrenceTimes`) 변환 로직은 `ChoreDefinitionRepositoryAdapter`의 것을 복제한다 — import는 하지 않는다(컨텍스트 간 직접 참조 금지, CLAUDE.md).
- 조회 시 DB에서 `startAt <= to`인 이벤트만 미리 필터링한다 (`findByStartAtLessThanEqual`) — `occurrencesIn`은 그 결과에만 적용한다.
- `RecurrenceDto`는 calendar API 계층에 로컬로 복제한다 — chore api의 것을 import하지 않는다.
- Flyway 마이그레이션 경로는 별도 설정 불필요 — `bootstrap`의 기본 `classpath:db/migration` 스캔이 `calendar/src/main/resources/db/migration/`을 자동으로 잡는다.
- `bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`는 이미 `@EnableJpaRepositories(basePackages=["sallim"])` + `@EntityScan(basePackages=["sallim"])`로 전체 `sallim` 패키지를 스캔하므로 이번 서브프로젝트에서 **수정하지 않는다**.
- `CalendarEvent.recurrence`는 nullable(단일 일정)이다 — chore의 `ChoreDefinition.recurrence`(non-null)와 달리 컬럼 변환 함수가 null을 처리해야 한다.
- 테스트의 `LocalDateTime` 값은 전부 고정값(`LocalDateTime.of(...)`)을 쓴다 — `LocalDateTime.now()`는 쓰지 않는다(Monthly 반복 테스트가 날짜 의존성으로 겪었던 것과 같은 종류의 flakiness를 애초에 차단).

---

### Task 1: Calendar 모듈 Gradle 설정 + 영속성 계층

**Files:**
- Modify: `calendar/build.gradle.kts`
- Create: `calendar/src/main/kotlin/sallim/calendar/domain/CalendarEventRepository.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/infrastructure/persistence/CalendarEventEntity.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/infrastructure/persistence/CalendarEventJpaRepository.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/infrastructure/persistence/CalendarEventRepositoryAdapter.kt`
- Create: `calendar/src/main/resources/db/migration/V1__create_calendar_event_table.sql`
- Test: `calendar/src/test/kotlin/sallim/calendar/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`
- Test: `calendar/src/test/kotlin/sallim/calendar/infrastructure/persistence/CalendarEventRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `sallim.calendar.domain.CalendarEvent`, `CalendarEventId`, `MemberId` (기존, `calendar-domain` 서브프로젝트에서 완성됨), `sallim.common.domain.{RecurrencePolicy,Daily,WeeklyNTimes,Monthly,Identifier}` (기존)
- Produces: `sallim.calendar.domain.CalendarEventRepository`(도메인 포트) — Task 2가 이걸 주입받는다. 시그니처: `save(event: CalendarEvent): CalendarEvent`, `findById(id: CalendarEventId): CalendarEvent?`, `findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent>`, `deleteById(id: CalendarEventId)`.

- [ ] **Step 1: `calendar/build.gradle.kts`를 chore의 것과 동일한 구조로 채운다**

```kotlin
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
    // chore/build.gradle.kts와 동일한 이유(로컬 Docker 엔진과 testcontainers 1.19.8 충돌) —
    // 이 모듈도 Testcontainers MySQL로 CalendarEventRepositoryAdapterTest를 띄우므로 동일 오버라이드가 필요하다.
    dependencies {
        dependencySet("org.testcontainers:${libs.versions.testcontainers.get()}") {
            entry("testcontainers")
            entry("junit-jupiter")
            entry("mysql")
            entry("jdbc")
            entry("database-commons")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
```

- [ ] **Step 2: 도메인 포트를 작성한다 — `sallim/calendar/domain/CalendarEventRepository.kt`**

```kotlin
package sallim.calendar.domain

import java.time.LocalDateTime

interface CalendarEventRepository {
    fun save(event: CalendarEvent): CalendarEvent
    fun findById(id: CalendarEventId): CalendarEvent?
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent>
    fun deleteById(id: CalendarEventId)
}
```

- [ ] **Step 3: `AbstractMySqlIntegrationTest`를 chore의 것과 동일하게 복제한다 — `sallim/calendar/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`**

```kotlin
package sallim.calendar.infrastructure.persistence

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

class KMySqlContainer(imageName: String) : MySQLContainer<KMySqlContainer>(DockerImageName.parse(imageName))

abstract class AbstractMySqlIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: KMySqlContainer = KMySqlContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
```

- [ ] **Step 4: 실패하는 테스트를 작성한다 — `sallim/calendar/infrastructure/persistence/CalendarEventRepositoryAdapterTest.kt`**

```kotlin
package sallim.calendar.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CalendarEventRepositoryAdapter::class)
class CalendarEventRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: CalendarEventRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `반복 없는 일정을 저장하고 조회하면 recurrence가 null로 돌아온다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
            MemberId.generate(), "케이크 사기", null
        )

        adapter.save(event)
        em.flush()
        em.clear()

        val found = adapter.findById(event.id)
        found.shouldNotBeNull()
        found.title shouldBe "생일"
        found.startAt shouldBe LocalDateTime.of(2026, 9, 10, 14, 0)
        found.memo shouldBe "케이크 사기"
        found.recurrence shouldBe null
    }

    @Test
    fun `반복 있는 일정을 저장하고 조회하면 recurrence가 복원된다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "운동", LocalDateTime.of(2026, 9, 1, 7, 0),
            MemberId.generate(), null, WeeklyNTimes(3)
        )

        adapter.save(event)
        em.flush()
        em.clear()

        val found = adapter.findById(event.id)
        found.shouldNotBeNull()
        found.recurrence shouldBe WeeklyNTimes(3)
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(CalendarEventId.generate()).shouldBeNull()
    }

    @Test
    fun `findByStartAtLessThanEqual은 startAt이 to보다 늦은 이벤트를 제외한다`() {
        val before = CalendarEvent(
            CalendarEventId.generate(), "이전", LocalDateTime.of(2026, 9, 10, 9, 0),
            MemberId.generate(), null, null
        )
        val boundary = CalendarEvent(
            CalendarEventId.generate(), "경계", LocalDateTime.of(2026, 9, 15, 23, 59),
            MemberId.generate(), null, null
        )
        val after = CalendarEvent(
            CalendarEventId.generate(), "이후", LocalDateTime.of(2026, 9, 20, 0, 0),
            MemberId.generate(), null, null
        )
        adapter.save(before)
        adapter.save(boundary)
        adapter.save(after)
        em.flush()
        em.clear()

        val found = adapter.findByStartAtLessThanEqual(LocalDateTime.of(2026, 9, 15, 23, 59))

        found shouldHaveSize 2
        found.map { it.title }.toSet() shouldBe setOf("이전", "경계")
    }

    @Test
    fun `삭제하면 findById 결과가 null이 된다`() {
        val event = CalendarEvent(
            CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
            MemberId.generate(), null, null
        )
        adapter.save(event)
        em.flush()
        em.clear()

        adapter.deleteById(event.id)
        em.flush()
        em.clear()

        adapter.findById(event.id).shouldBeNull()
    }
}
```

- [ ] **Step 5: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.infrastructure.persistence.CalendarEventRepositoryAdapterTest"`
Expected: FAIL — `CalendarEventEntity`/`CalendarEventJpaRepository`/`CalendarEventRepositoryAdapter`가 없어 컴파일 에러.

- [ ] **Step 6: `CalendarEventEntity`를 작성한다 — `sallim/calendar/infrastructure/persistence/CalendarEventEntity.kt`**

```kotlin
package sallim.calendar.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "calendar_event")
class CalendarEventEntity(
    @Id
    val id: String,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: String,
    val memo: String?,
    val recurrenceType: String?,
    val recurrenceTimes: Int?
)
```

- [ ] **Step 7: `CalendarEventJpaRepository`를 작성한다 — `sallim/calendar/infrastructure/persistence/CalendarEventJpaRepository.kt`**

```kotlin
package sallim.calendar.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface CalendarEventJpaRepository : JpaRepository<CalendarEventEntity, String> {
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEventEntity>
}
```

- [ ] **Step 8: `CalendarEventRepositoryAdapter`를 작성한다 — `sallim/calendar/infrastructure/persistence/CalendarEventRepositoryAdapter.kt`**

```kotlin
package sallim.calendar.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDateTime
import java.util.UUID

@Repository
class CalendarEventRepositoryAdapter(
    private val jpaRepository: CalendarEventJpaRepository
) : CalendarEventRepository {

    override fun save(event: CalendarEvent): CalendarEvent {
        val (recurrenceType, recurrenceTimes) = event.recurrence.toColumns()
        jpaRepository.save(
            CalendarEventEntity(
                id = event.id.value.toString(),
                title = event.title,
                startAt = event.startAt,
                memberId = event.memberId.value.toString(),
                memo = event.memo,
                recurrenceType = recurrenceType,
                recurrenceTimes = recurrenceTimes
            )
        )
        return event
    }

    override fun findById(id: CalendarEventId): CalendarEvent? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent> =
        jpaRepository.findByStartAtLessThanEqual(to).map { it.toDomain() }

    override fun deleteById(id: CalendarEventId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun CalendarEventEntity.toDomain(): CalendarEvent = CalendarEvent(
        id = CalendarEventId(UUID.fromString(id)),
        title = title,
        startAt = startAt,
        memberId = MemberId(UUID.fromString(memberId)),
        memo = memo,
        recurrence = toRecurrencePolicy(recurrenceType, recurrenceTimes)
    )

    private fun RecurrencePolicy?.toColumns(): Pair<String?, Int?> = when (this) {
        null -> null to null
        is Daily -> "DAILY" to null
        is WeeklyNTimes -> "WEEKLY_N_TIMES" to times
        is Monthly -> "MONTHLY" to null
    }

    private fun toRecurrencePolicy(type: String?, times: Int?): RecurrencePolicy? = when (type) {
        null -> null
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "WEEKLY_N_TIMES requires recurrenceTimes" })
        "MONTHLY" -> Monthly
        else -> error("unknown recurrence type: $type")
    }
}
```

- [ ] **Step 9: Flyway 마이그레이션을 작성한다 — `calendar/src/main/resources/db/migration/V1__create_calendar_event_table.sql`**

```sql
CREATE TABLE calendar_event (
    id CHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    member_id CHAR(36) NOT NULL,
    memo TEXT,
    recurrence_type VARCHAR(20),
    recurrence_times INT
);
```

- [ ] **Step 10: 테스트 실행 — 통과 확인 (Docker 필요)**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.infrastructure.persistence.CalendarEventRepositoryAdapterTest"`
Expected: PASS (Docker가 없는 환경이면 Testcontainers가 컨테이너를 못 띄워 이 스텝은 실패한다 — 이 세션은 Docker 미설치 환경이므로, 컴파일 성공만 `./gradlew :calendar:compileTestKotlin`으로 확인하고 실제 통과 여부는 사용자가 Docker 있는 환경에서 검증)

- [ ] **Step 11: Commit**

```bash
git add calendar/build.gradle.kts calendar/src/main/kotlin/sallim/calendar/domain/CalendarEventRepository.kt calendar/src/main/kotlin/sallim/calendar/infrastructure calendar/src/main/resources/db/migration calendar/src/test/kotlin/sallim/calendar/infrastructure
git commit -m "feat: CalendarEvent JPA 영속성 계층 추가"
```

---

### Task 2: Application 계층 (`CalendarEventService`)

**Files:**
- Create: `calendar/src/main/kotlin/sallim/calendar/application/NotFoundException.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/application/CalendarEventService.kt`
- Test: `calendar/src/test/kotlin/sallim/calendar/application/FakeCalendarEventRepository.kt`
- Test: `calendar/src/test/kotlin/sallim/calendar/application/CalendarEventServiceTest.kt`

**Interfaces:**
- Consumes: `sallim.calendar.domain.CalendarEventRepository`(Task 1에서 만든 포트, 시그니처는 Task 1의 "Produces" 참고), `CalendarEvent`, `CalendarEventId`, `MemberId`, `sallim.common.domain.RecurrencePolicy`
- Produces: `sallim.calendar.application.CalendarEventService` — `occurrencesIn(from: LocalDate, to: LocalDate): List<Pair<CalendarEvent, LocalDateTime>>`, `create(title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?): CalendarEvent`, `update(id: CalendarEventId, title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?): CalendarEvent`, `delete(id: CalendarEventId)`. `sallim.calendar.application.NotFoundException(message: String) : RuntimeException` — Task 3의 API 예외 핸들러가 이걸 잡는다.

- [ ] **Step 1: `FakeCalendarEventRepository`를 작성한다 — `sallim/calendar/application/FakeCalendarEventRepository.kt`**

```kotlin
package sallim.calendar.application

import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import java.time.LocalDateTime

class FakeCalendarEventRepository : CalendarEventRepository {
    private val store = mutableMapOf<CalendarEventId, CalendarEvent>()

    override fun save(event: CalendarEvent): CalendarEvent {
        store[event.id] = event
        return event
    }

    override fun findById(id: CalendarEventId): CalendarEvent? = store[id]

    override fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent> =
        store.values.filter { !it.startAt.isAfter(to) }

    override fun deleteById(id: CalendarEventId) {
        store.remove(id)
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 작성한다 — `sallim/calendar/application/CalendarEventServiceTest.kt`**

```kotlin
package sallim.calendar.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import java.time.LocalDate
import java.time.LocalDateTime

class CalendarEventServiceTest : FunSpec({
    test("단일 일정을 생성하고 범위 안에서 조회하면 자기 자신 하나가 나온다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val member = MemberId.generate()

        val created = service.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), member, "케이크 사기", null
        )

        val result = service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        result shouldHaveSize 1
        result.first().first.id shouldBe created.id
        result.first().second shouldBe LocalDateTime.of(2026, 9, 10, 14, 0)
    }

    test("조회 범위 밖의 단일 일정은 나오지 않는다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), MemberId.generate(), null, null)

        val result = service.occurrencesIn(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        result shouldHaveSize 0
    }

    test("반복 일정은 범위 안에서 여러 번 나온다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        service.create("운동", LocalDateTime.of(2026, 9, 1, 7, 0), MemberId.generate(), null, Daily)

        val result = service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5))
        result shouldHaveSize 5
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<IllegalArgumentException> {
            service.occurrencesIn(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1))
        }
    }

    test("to가 비현실적으로 먼 미래면 IllegalArgumentException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<IllegalArgumentException> {
            service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.MAX)
        }
    }

    test("존재하는 일정을 수정하면 값이 갱신된다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val member = MemberId.generate()
        val created = service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), member, null, null)

        val updated = service.update(
            created.id, "생일파티", LocalDateTime.of(2026, 9, 11, 18, 0), member, "장소 예약", null
        )

        updated.title shouldBe "생일파티"
        updated.startAt shouldBe LocalDateTime.of(2026, 9, 11, 18, 0)
        updated.memo shouldBe "장소 예약"
    }

    test("존재하지 않는 일정을 수정하면 NotFoundException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<NotFoundException> {
            service.update(
                CalendarEventId.generate(), "생일", LocalDateTime.of(2026, 9, 10, 14, 0),
                MemberId.generate(), null, null
            )
        }
    }

    test("존재하는 일정을 삭제하면 이후 조회에서 사라진다") {
        val service = CalendarEventService(FakeCalendarEventRepository())
        val created = service.create("생일", LocalDateTime.of(2026, 9, 10, 14, 0), MemberId.generate(), null, null)

        service.delete(created.id)

        service.occurrencesIn(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)) shouldHaveSize 0
    }

    test("존재하지 않는 일정을 삭제하면 NotFoundException") {
        val service = CalendarEventService(FakeCalendarEventRepository())

        shouldThrow<NotFoundException> {
            service.delete(CalendarEventId.generate())
        }
    }
})
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.application.CalendarEventServiceTest"`
Expected: FAIL — `CalendarEventService`/`NotFoundException`이 없어 컴파일 에러.

- [ ] **Step 4: `NotFoundException`을 작성한다 — `sallim/calendar/application/NotFoundException.kt`**

```kotlin
package sallim.calendar.application

class NotFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 5: `CalendarEventService`를 작성한다 — `sallim/calendar/application/CalendarEventService.kt`**

```kotlin
package sallim.calendar.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.CalendarEventRepository
import sallim.calendar.domain.MemberId
import sallim.common.domain.RecurrencePolicy
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CalendarEventService(private val repository: CalendarEventRepository) {

    @Transactional(readOnly = true)
    fun occurrencesIn(from: LocalDate, to: LocalDate): List<Pair<CalendarEvent, LocalDateTime>> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
        val toDateTime = to.plusDays(1).atStartOfDay().minusNanos(1)
        return repository.findByStartAtLessThanEqual(toDateTime)
            .flatMap { event -> event.occurrencesIn(from, to).map { event to it } }
            .sortedBy { it.second }
    }

    @Transactional
    fun create(
        title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?
    ): CalendarEvent =
        repository.save(CalendarEvent(CalendarEventId.generate(), title, startAt, memberId, memo, recurrence))

    @Transactional
    fun update(
        id: CalendarEventId, title: String, startAt: LocalDateTime, memberId: MemberId,
        memo: String?, recurrence: RecurrencePolicy?
    ): CalendarEvent {
        repository.findById(id) ?: throw NotFoundException("calendar event not found: $id")
        return repository.save(CalendarEvent(id, title, startAt, memberId, memo, recurrence))
    }

    @Transactional
    fun delete(id: CalendarEventId) {
        repository.findById(id) ?: throw NotFoundException("calendar event not found: $id")
        repository.deleteById(id)
    }
}
```

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.application.CalendarEventServiceTest"`
Expected: PASS (Docker 불필요 — Fake 리포지토리만 사용)

- [ ] **Step 7: Commit**

```bash
git add calendar/src/main/kotlin/sallim/calendar/application calendar/src/test/kotlin/sallim/calendar/application
git commit -m "feat: CalendarEventService 애플리케이션 계층 추가"
```

---

### Task 3: API 계층 (`CalendarEventController`)

**Files:**
- Create: `calendar/src/main/kotlin/sallim/calendar/api/CalendarEventController.kt`
- Create: `calendar/src/main/kotlin/sallim/calendar/api/ApiExceptionHandler.kt`
- Test: `calendar/src/test/kotlin/sallim/calendar/api/CalendarEventControllerTest.kt`

**Interfaces:**
- Consumes: `sallim.calendar.application.CalendarEventService`/`NotFoundException`(Task 2, 시그니처는 Task 2의 "Produces" 참고), `sallim.calendar.domain.{CalendarEvent,CalendarEventId,MemberId}`, `sallim.common.domain.{RecurrencePolicy,Daily,WeeklyNTimes,Monthly}`
- Produces: `GET/POST/PUT/DELETE /api/calendar-events` — 이 서브프로젝트의 최종 산출물, 이후 태스크 없음.

- [ ] **Step 1: 실패하는 테스트를 작성한다 — `sallim/calendar/api/CalendarEventControllerTest.kt`**

```kotlin
package sallim.calendar.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.calendar.application.CalendarEventService
import sallim.calendar.application.FakeCalendarEventRepository
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(CalendarEventController::class)
@Import(CalendarEventControllerTest.TestConfig::class)
class CalendarEventControllerTest {

    @TestConfiguration
    class TestConfig {
        private val repository = FakeCalendarEventRepository()

        @Bean
        fun calendarEventService(): CalendarEventService = CalendarEventService(repository)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var calendarEventService: CalendarEventService

    @Test
    fun `반복 없는 일정을 생성하면 201을 반환한다`() {
        mockMvc.perform(
            post("/api/calendar-events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), UUID.randomUUID(), "케이크 사기", null
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("생일"))
            .andExpect(jsonPath("$.recurrence").doesNotExist())
    }

    @Test
    fun `반복 있는 일정을 생성하면 recurrence가 응답에 포함된다`() {
        mockMvc.perform(
            post("/api/calendar-events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "운동", LocalDateTime.of(2026, 9, 1, 7, 0), UUID.randomUUID(), null,
                            RecurrenceDto("WEEKLY_N_TIMES", 3)
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.recurrence.type").value("WEEKLY_N_TIMES"))
            .andExpect(jsonPath("$.recurrence.times").value(3))
    }

    @Test
    fun `조회하면 발생 목록을 반환한다`() {
        val member = UUID.randomUUID()
        calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(member), null, null
        )

        mockMvc.perform(
            get("/api/calendar-events").param("from", "2026-09-01").param("to", "2026-09-30")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("생일"))
            .andExpect(jsonPath("$[0].memberId").value(member.toString()))
    }

    @Test
    fun `from 파라미터가 없으면 400`() {
        mockMvc.perform(get("/api/calendar-events").param("to", "2026-09-30"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `from이 to보다 늦으면 400`() {
        mockMvc.perform(get("/api/calendar-events").param("from", "2026-09-30").param("to", "2026-09-01"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `수정하면 200과 갱신된 값을 반환한다`() {
        val member = UUID.randomUUID()
        val created = calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(member), null, null
        )

        mockMvc.perform(
            put("/api/calendar-events/${created.id.value}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "생일파티", LocalDateTime.of(2026, 9, 11, 18, 0), member, "장소 예약", null
                        )
                    )
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("생일파티"))
            .andExpect(jsonPath("$.memo").value("장소 예약"))
    }

    @Test
    fun `존재하지 않는 일정을 수정하면 404를 반환한다`() {
        mockMvc.perform(
            put("/api/calendar-events/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest("생일", LocalDateTime.of(2026, 9, 10, 14, 0), UUID.randomUUID(), null, null)
                    )
                )
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제하면 204를 반환한다`() {
        val created = calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(UUID.randomUUID()), null, null
        )

        mockMvc.perform(delete("/api/calendar-events/${created.id.value}"))
            .andExpect(status().isNoContent)
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.api.CalendarEventControllerTest"`
Expected: FAIL — `CalendarEventController`/`CalendarEventRequest`/`RecurrenceDto` 등이 없어 컴파일 에러.

- [ ] **Step 3: `ApiExceptionHandler`를 작성한다 — `sallim/calendar/api/ApiExceptionHandler.kt`**

```kotlin
package sallim.calendar.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import sallim.calendar.application.NotFoundException

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = ResponseEntity.status(404).body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException) = ResponseEntity.status(400).body(mapOf("error" to e.message))

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? =
        ResponseEntity.status(statusCode).headers(headers).body(mapOf("error" to ex.message))
}
```

- [ ] **Step 4: `CalendarEventController`를 작성한다 — `sallim/calendar/api/CalendarEventController.kt`**

```kotlin
package sallim.calendar.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.calendar.application.CalendarEventService
import sallim.calendar.domain.CalendarEvent
import sallim.calendar.domain.CalendarEventId
import sallim.calendar.domain.MemberId
import sallim.common.domain.Daily
import sallim.common.domain.Monthly
import sallim.common.domain.RecurrencePolicy
import sallim.common.domain.WeeklyNTimes
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class RecurrenceDto(val type: String, val times: Int?)

data class CalendarEventRequest(
    val title: String,
    val startAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?,
    val recurrence: RecurrenceDto?
)

data class CalendarEventResponse(
    val id: UUID,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?,
    val recurrence: RecurrenceDto?
)

data class CalendarEventOccurrenceResponse(
    val eventId: UUID,
    val title: String,
    val occurredAt: LocalDateTime,
    val memberId: UUID,
    val memo: String?
)

@RestController
@RequestMapping("/api/calendar-events")
class CalendarEventController(private val service: CalendarEventService) {

    @GetMapping
    fun occurrencesIn(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): List<CalendarEventOccurrenceResponse> =
        service.occurrencesIn(from, to).map { (event, occurredAt) ->
            CalendarEventOccurrenceResponse(event.id.value, event.title, occurredAt, event.memberId.value, event.memo)
        }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CalendarEventRequest): CalendarEventResponse =
        service.create(
            request.title, request.startAt, MemberId(request.memberId), request.memo, request.recurrence?.toDomain()
        ).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: CalendarEventRequest): CalendarEventResponse =
        service.update(
            CalendarEventId(id), request.title, request.startAt, MemberId(request.memberId),
            request.memo, request.recurrence?.toDomain()
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        service.delete(CalendarEventId(id))
    }

    private fun CalendarEvent.toResponse() =
        CalendarEventResponse(id.value, title, startAt, memberId.value, memo, recurrence?.toDto())

    private fun RecurrenceDto.toDomain(): RecurrencePolicy = when (type) {
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "times is required for WEEKLY_N_TIMES" })
        "MONTHLY" -> Monthly
        else -> throw IllegalArgumentException("unknown recurrence type: $type")
    }

    private fun RecurrencePolicy.toDto(): RecurrenceDto = when (this) {
        is Daily -> RecurrenceDto("DAILY", null)
        is WeeklyNTimes -> RecurrenceDto("WEEKLY_N_TIMES", times)
        is Monthly -> RecurrenceDto("MONTHLY", null)
    }
}
```

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test --tests "sallim.calendar.api.CalendarEventControllerTest"`
Expected: PASS (Docker 불필요 — `@WebMvcTest` + Fake 서비스)

- [ ] **Step 6: 전체 calendar 모듈 테스트 + 컴파일 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :calendar:test :bootstrap:compileKotlin`
Expected: PASS (persistence 테스트는 Docker 없으면 스킵/실패할 수 있음 — Docker 없는 환경이면 `--tests` 필터로 Docker 불필요 테스트만 재확인하고, `:bootstrap:compileKotlin`으로 calendar 모듈이 bootstrap 전체 그래프에 문제없이 얹히는지 확인)

- [ ] **Step 7: Commit**

```bash
git add calendar/src/main/kotlin/sallim/calendar/api calendar/src/test/kotlin/sallim/calendar/api
git commit -m "feat: CalendarEvent REST API 추가"
```

## 다음 단계

이 플랜 완료 후 `superpowers:finishing-a-development-branch`로 머지/푸시.
