# Chore(집안일) 반복 인스턴스 스케줄러 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chore` 모듈에 반복 인스턴스 자동 생성을 추가한다 — `ChoreDefinition` 생성 즉시 오늘 인스턴스를 만들고, 매일 자정 배치가 각 정의의 `RecurrencePolicy`에 따라 놓친 날짜까지 소급 생성한다.

**Architecture:** 소급 보정 로직은 `ChoreInstanceService.generateDueInstances(...)`(application 계층)에 두고, `ChoreInstanceScheduler`(api 계층, `@Scheduled`)는 이 메서드를 부르는 얇은 배선만 담당한다. 중복 생성은 `chore_instance` 테이블의 새 유니크 제약(`chore_definition_id`, `scheduled_date`)이 DB 레벨에서 막는다.

**Tech Stack:** Kotlin(JDK 21) / Spring Boot 3.3.4 (`@Scheduled`/`@EnableScheduling`은 이미 있는 `spring-boot-starter-web` 전이 의존성으로 충족, 신규 라이브러리 없음) / Flyway / Kotest — 새 의존성 추가 없음

**Spec:** `docs/superpowers/specs/2026-08-24-chore-instance-scheduler-design.md`

## Global Constraints

- `ChoreDefinition` 생성 시 오늘 날짜 인스턴스를 즉시 하나 생성한다 (CLAUDE.md 관련 아님, 스펙의 핵심 결정) — 스케줄러는 "인스턴스가 하나도 없는 정의"를 다루지 않는다
- 소급 보정(catch-up)한다 — "오늘치만" 만들지 않는다 (스펙 "결정된 사항")
- `chore_instance(chore_definition_id, scheduled_date)`에 DB 유니크 제약 (스펙 "결정된 사항")
- 스케줄러(`ChoreInstanceScheduler`)는 `sallim.chore.api` 패키지 — CLAUDE.md의 4계층(domain/application/infrastructure/api)에 새 계층을 만들지 않는다
- 핵심 로직(소급 반복문)은 `ChoreInstanceService`에, 스케줄러는 `@Scheduled` 트리거 배선만
- `RecurrencePolicy.nextOccurrence` 자체의 계산 로직은 건드리지 않는다 (갭 #1은 범위 밖)

---

## File Structure

```
chore/src/main/kotlin/sallim/chore/application/
  ChoreDefinitionService.kt                                   (수정 — create()에 초기 인스턴스 생성 추가)
  ChoreInstanceService.kt                                     (수정 — generateDueInstances() 추가)
chore/src/main/kotlin/sallim/chore/api/
  ChoreInstanceScheduler.kt                                   (신규)
chore/src/main/resources/db/migration/
  V4__add_chore_instance_unique_constraint.sql                (신규)
chore/src/test/kotlin/sallim/chore/application/
  ChoreDefinitionServiceTest.kt                                (수정 — 인스턴스 동시생성 테스트 추가)
  ChoreInstanceServiceTest.kt                                  (수정 — generateDueInstances 테스트 추가)
chore/src/test/kotlin/sallim/chore/api/
  ChoreInstanceSchedulerTest.kt                                (신규)
chore/src/test/kotlin/sallim/chore/infrastructure/persistence/
  ChoreInstanceRepositoryAdapterTest.kt                        (수정 — 유니크 제약 위반 테스트 추가)
bootstrap/src/main/kotlin/sallim/bootstrap/
  SallimApplication.kt                                         (수정 — @EnableScheduling 추가)
```

---

### Task 1: `ChoreDefinitionService.create()` — 생성 즉시 오늘 인스턴스 생성

**Files:**
- Modify: `chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt` (수정)

**Interfaces:**
- Consumes: `ChoreInstance.schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance`(기존, `sallim.chore.domain.ChoreInstance`), `ChoreInstanceRepository.save(choreInstance: ChoreInstance): ChoreInstance`(기존, 이미 `ChoreDefinitionService`에 주입되어 있음 — `delete()`가 이미 사용 중)
- Produces: `ChoreDefinitionService.create(...)`가 반환하는 `ChoreDefinition`은 그대로지만, 부작용으로 `ChoreInstanceRepository`에 오늘 날짜 인스턴스가 하나 추가됨 — Task 3의 `ChoreInstanceSchedulerTest`가 이 부작용에 의존한다(정의를 만들면 인스턴스가 이미 하나 있다는 전제)

- [ ] **Step 1: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt`의 `import java.time.LocalDate` 줄 바로 아래, 마지막 `test(...)` 블록(`"정의를 삭제하면 그 정의의 인스턴스도 함께 삭제된다"`) 뒤에 새 테스트를 추가한다. 파일 전체를 아래로 교체:

```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import java.time.LocalDate

class ChoreDefinitionServiceTest : FunSpec({
    fun newService(): Triple<ChoreDefinitionService, FakeRoomRepository, Pair<FakeChoreDefinitionRepository, FakeChoreInstanceRepository>> {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        return Triple(ChoreDefinitionService(definitions, rooms, instances), rooms, definitions to instances)
    }

    test("존재하는 방에 할 일 정의를 생성할 수 있다") {
        val (service, rooms) = newService()
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        val definition = service.create(
            "청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상"
        )

        service.list() shouldHaveSize 1
        definition.roomId shouldBe room.id
    }

    test("존재하지 않는 방을 참조하면 IllegalArgumentException") {
        val (service) = newService()

        shouldThrow<IllegalArgumentException> {
            service.create("청소", RoomId.generate(), MemberId.generate(), Daily, listOf("단계1"), "영상")
        }
    }

    test("존재하지 않는 정의를 수정하면 NotFoundException") {
        val (service, rooms) = newService()
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        shouldThrow<NotFoundException> {
            service.update(ChoreDefinitionId.generate(), "청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        }
    }

    test("정의를 삭제하면 그 정의의 인스턴스도 함께 삭제된다") {
        val (service, rooms, defAndInst) = newService()
        val (definitions, instances) = defAndInst
        val roomService = RoomService(rooms, definitions, instances)
        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = service.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        instances.save(ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 20)))

        service.delete(definition.id)

        service.list() shouldHaveSize 0
        instances.findAll() shouldHaveSize 0
    }

    test("생성 시 오늘 날짜 인스턴스가 함께 생성된다") {
        val (service, rooms, defAndInst) = newService()
        val (_, instances) = defAndInst
        val roomService = RoomService(rooms, FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        val definition = service.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")

        val created = instances.findAll().filter { it.choreDefinitionId == definition.id }
        created shouldHaveSize 1
        created.first().scheduledDate shouldBe LocalDate.now()
    }
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` (JAVA_HOME이 PATH에 없으면 모든 gradle 실행 전에 필요) 후 `./gradlew :chore:test --tests "sallim.chore.application.ChoreDefinitionServiceTest"`
Expected: FAIL — `"생성 시 오늘 날짜 인스턴스가 함께 생성된다"`가 `created shouldHaveSize 1`에서 0을 받아 실패 (아직 `create()`가 인스턴스를 안 만듦)

- [ ] **Step 3: `ChoreDefinitionService.create()` 수정**

`chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt` 전체를 아래로 교체:

```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomRepository
import java.time.LocalDate

@Service
class ChoreDefinitionService(
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val roomRepository: RoomRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    @Transactional(readOnly = true)
    fun list(): List<ChoreDefinition> = choreDefinitionRepository.findAll()

    @Transactional
    fun create(
        label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = choreDefinitionRepository.save(
            ChoreDefinition(
                ChoreDefinitionId.generate(), roomId, label, assigneeId, recurrence, howToSteps, videoQuery
            )
        )
        choreInstanceRepository.save(ChoreInstance.schedule(definition.id, LocalDate.now()))
        return definition
    }

    @Transactional
    fun update(
        id: ChoreDefinitionId, label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = ChoreDefinition(id, roomId, label, assigneeId, recurrence, howToSteps, videoQuery)
        return choreDefinitionRepository.save(definition)
    }

    @Transactional
    fun delete(id: ChoreDefinitionId) {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        choreInstanceRepository.findAll()
            .filter { it.choreDefinitionId == id }
            .forEach { choreInstanceRepository.deleteById(it.id) }
        choreDefinitionRepository.deleteById(id)
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreDefinitionServiceTest"`
Expected: PASS (5개 테스트 모두 통과)

- [ ] **Step 5: 다른 서비스 테스트가 이 변경에 안 깨지는지 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.*"`
Expected: PASS — `RoomServiceTest`/`ChoreInstanceServiceTest`/`CleanlinessServiceTest`는 각자 독립된 페이크 리포지토리를 쓰므로 이 변경과 무관하게 그대로 통과해야 함

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt
git commit -m "feat: ChoreDefinition 생성 시 오늘 날짜 인스턴스 자동 생성"
```

---

### Task 2: `ChoreInstanceService.generateDueInstances()` + 유니크 제약

**Files:**
- Modify: `chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt`
- Create: `chore/src/main/resources/db/migration/V4__add_chore_instance_unique_constraint.sql`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt` (수정)
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt` (수정)

**Interfaces:**
- Consumes: `RecurrencePolicy.nextOccurrence(after: LocalDate): LocalDate`(기존), `ChoreDefinition`(기존, `id`/`recurrence` 필드), `ChoreInstanceRepository.findAll()`/`save(...)`(기존)
- Produces: `ChoreInstanceService.generateDueInstances(definitions: List<ChoreDefinition>, today: LocalDate): List<ChoreInstance>` — Task 3의 `ChoreInstanceScheduler`가 이 시그니처 그대로 호출한다

- [ ] **Step 1: 실패하는 테스트 작성 (application 계층)**

`chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt` 전체를 아래로 교체:

```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes
import java.time.LocalDate

class ChoreInstanceServiceTest : FunSpec({
    fun choreDefinition(recurrence: RecurrencePolicy): ChoreDefinition = ChoreDefinition(
        ChoreDefinitionId.generate(), RoomId.generate(), "청소", MemberId.generate(), recurrence, listOf("단계1"), "영상"
    )

    test("날짜로 필터링해 조회한다") {
        val instances = FakeChoreInstanceRepository()
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21)))
        val service = ChoreInstanceService(instances)

        service.listByDate(LocalDate.of(2026, 8, 20)) shouldHaveSize 1
    }

    test("완료 처리하면 저장된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()

        val completed = service.complete(instance.id, member)

        completed.completed shouldBe true
        instances.findById(instance.id)!!.completed shouldBe true
    }

    test("존재하지 않는 인스턴스를 완료 처리하면 NotFoundException") {
        val service = ChoreInstanceService(FakeChoreInstanceRepository())

        shouldThrow<NotFoundException> { service.complete(ChoreInstanceId.generate(), MemberId.generate()) }
    }

    test("이미 완료된 인스턴스를 다시 완료 처리하면 IllegalStateException") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()
        service.complete(instance.id, member)

        shouldThrow<IllegalStateException> { service.complete(instance.id, member) }
    }

    test("최근 인스턴스로부터 오늘까지 매일 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(3)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 3
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(2), today.minusDays(1), today)
    }

    test("WeeklyNTimes도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(WeeklyNTimes(2))  // nextOccurrence는 7/2=3일 간격
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(7)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(4), today.minusDays(1))
    }

    test("Monthly도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Monthly)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusMonths(2)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusMonths(1), today)
    }

    test("이미 오늘까지 인스턴스가 있으면 아무것도 생성하지 않는다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today))

        service.generateDueInstances(listOf(definition), today).shouldBeEmpty()
    }

    test("인스턴스가 하나도 없는 정의는 건너뛴다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)

        service.generateDueInstances(listOf(definition), LocalDate.now()).shouldBeEmpty()
    }
})
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: FAIL — `generateDueInstances`가 없어 컴파일 에러

- [ ] **Step 3: `ChoreInstanceService.generateDueInstances()` 구현**

`chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt` 전체를 아래로 교체:

```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.time.LocalDate

@Service
class ChoreInstanceService(private val choreInstanceRepository: ChoreInstanceRepository) {
    @Transactional(readOnly = true)
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    @Transactional
    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        return choreInstanceRepository.save(instance)
    }

    @Transactional
    fun generateDueInstances(definitions: List<ChoreDefinition>, today: LocalDate): List<ChoreInstance> {
        val allInstances = choreInstanceRepository.findAll()
        return definitions.flatMap { definition ->
            val latest = allInstances
                .filter { it.choreDefinitionId == definition.id }
                .maxByOrNull { it.scheduledDate } ?: return@flatMap emptyList()

            val created = mutableListOf<ChoreInstance>()
            var next = definition.recurrence.nextOccurrence(latest.scheduledDate)
            while (!next.isAfter(today)) {
                created += choreInstanceRepository.save(ChoreInstance.schedule(definition.id, next))
                next = definition.recurrence.nextOccurrence(next)
            }
            created
        }
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: PASS (10개 테스트 모두 통과)

- [ ] **Step 5: Flyway 마이그레이션 작성**

`chore/src/main/resources/db/migration/V4__add_chore_instance_unique_constraint.sql`:
```sql
ALTER TABLE chore_instance
    ADD CONSTRAINT uq_chore_instance_definition_date UNIQUE (chore_definition_id, scheduled_date);
```

- [ ] **Step 6: 유니크 제약 실패하는 테스트 작성 (persistence 계층)**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt`의 `import java.time.LocalDate` 줄 위에 두 줄 추가하고, 마지막 `@Test` 블록 뒤에 새 테스트를 추가한다. 파일 전체를 아래로 교체:

```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreInstanceRepositoryAdapter::class)
class ChoreInstanceRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreInstanceRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `미완료 인스턴스를 저장하고 다시 읽으면 값이 같다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))

        adapter.save(instance)
        em.flush()
        em.clear()
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.id shouldBe instance.id
        found.choreDefinitionId shouldBe instance.choreDefinitionId
        found.scheduledDate shouldBe instance.scheduledDate
        found.completed shouldBe false
        found.completedBy shouldBe null
        found.completedAt shouldBe null
    }

    @Test
    fun `완료된 인스턴스를 저장 후 다시 읽으면 상태는 보존되고 이벤트는 재발행되지 않는다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        val member = MemberId.generate()
        instance.complete(member)

        adapter.save(instance)
        em.flush()
        em.clear()
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.completed shouldBe true
        found.completedBy shouldBe member
        found.completedAt shouldBe instance.completedAt
        found.domainEvents.shouldBeEmpty()
    }

    @Test
    fun `여러 인스턴스를 저장하면 findAll로 전부 조회된다`() {
        val a = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        val b = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21))
        adapter.save(a)
        adapter.save(b)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 2
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        adapter.save(instance)
        em.flush()
        em.clear()

        adapter.deleteById(instance.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }

    @Test
    fun `같은 정의와 날짜로 두 번 저장하면 유니크 제약 위반 예외가 난다`() {
        val definitionId = ChoreDefinitionId.generate()
        val date = LocalDate.of(2026, 8, 20)
        adapter.save(ChoreInstance.schedule(definitionId, date))
        em.flush()

        shouldThrow<DataIntegrityViolationException> {
            adapter.save(ChoreInstance.schedule(definitionId, date))
            em.flush()
        }
    }
}
```

- [ ] **Step 7: 테스트 실행 → 실패 확인 (컴파일은 되지만 제약이 없어 예외가 안 남, Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreInstanceRepositoryAdapterTest"`
Expected: FAIL — 새 테스트만 실패(제약이 아직 없어 두 번째 save가 예외 없이 성공). 실패 시 가장 먼저 Docker Desktop이 실행 중인지 확인.

- [ ] **Step 8: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

`V4` 마이그레이션이 Flyway 기본 스캔 경로에 있으므로 별도 설정 없이 자동 적용된다.

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreInstanceRepositoryAdapterTest"`
Expected: PASS (5개 테스트 모두 통과)

- [ ] **Step 9: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt chore/src/main/resources/db/migration/V4__add_chore_instance_unique_constraint.sql chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt
git commit -m "feat: 반복 인스턴스 소급 생성 로직 + 중복 방지 유니크 제약"
```

---

### Task 3: `ChoreInstanceScheduler` + `bootstrap` 자정 배치 활성화

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/api/ChoreInstanceScheduler.kt`
- Modify: `bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/ChoreInstanceSchedulerTest.kt` (신규)

**Interfaces:**
- Consumes: `ChoreDefinitionService.list(): List<ChoreDefinition>`(기존), `ChoreInstanceService.generateDueInstances(definitions: List<ChoreDefinition>, today: LocalDate): List<ChoreInstance>`(Task 2)
- Produces: 이 태스크가 계획의 마지막이라 이후 소비자 없음. 전체 저장소 빌드로 마무리.

- [ ] **Step 1: 실패하는 테스트 작성**

이 스케줄러는 두 서비스의 배선만 하므로, 목킹 라이브러리 없이 페이크 리포지토리 위에 실제 서비스를 올려서 종단 간(end-to-end) 동작으로 검증한다 — 프로젝트에 이미 자리잡은 패턴(`RoomServiceTest` 등)과 동일.

`chore/src/test/kotlin/sallim/chore/api/ChoreInstanceSchedulerTest.kt`:
```kotlin
package sallim.chore.api

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.ChoreInstanceService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreInstanceSchedulerTest : FunSpec({
    test("실행하면 모든 정의를 조회해 소급 인스턴스를 생성한다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        val definitionService = ChoreDefinitionService(definitions, rooms, instances)
        val instanceService = ChoreInstanceService(instances)
        val scheduler = ChoreInstanceScheduler(definitionService, instanceService)

        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = definitionService.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")
        val today = LocalDate.now()
        // create()가 이미 오늘 인스턴스를 만들어뒀으니, 스케줄러가 소급할 게 있도록 지우고 3일 전 인스턴스로 되돌린다
        instances.deleteById(instances.findAll().first { it.choreDefinitionId == definition.id }.id)
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(3)))

        scheduler.generateDueInstances()

        instances.findAll().filter { it.choreDefinitionId == definition.id } shouldHaveSize 4  // -3(기존) + -2,-1,0(소급)
    }
})
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.api.ChoreInstanceSchedulerTest"`
Expected: FAIL — `ChoreInstanceScheduler`가 없어 컴파일 에러

- [ ] **Step 3: `ChoreInstanceScheduler` 구현**

`chore/src/main/kotlin/sallim/chore/api/ChoreInstanceScheduler.kt`:
```kotlin
package sallim.chore.api

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.ChoreInstanceService
import java.time.LocalDate

@Component
class ChoreInstanceScheduler(
    private val choreDefinitionService: ChoreDefinitionService,
    private val choreInstanceService: ChoreInstanceService
) {
    @Scheduled(cron = "0 0 0 * * *")
    fun generateDueInstances() {
        choreInstanceService.generateDueInstances(choreDefinitionService.list(), LocalDate.now())
    }
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreInstanceSchedulerTest"`
Expected: PASS

- [ ] **Step 5: `bootstrap`에 `@EnableScheduling` 추가**

`bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt` 전체를 아래로 교체:
```kotlin
package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableScheduling

// @SpringBootApplication's scanBasePackages only extends @ComponentScan — it does not
// move where @EnableAutoConfiguration registers its "auto-configuration base package"
// (AutoConfigurationPackages), which JpaRepositoriesAutoConfiguration/entity scanning use and which
// otherwise defaults to this class's own package (sallim.bootstrap). chore's JPA repositories/entities
// live under sallim.chore.infrastructure.persistence, so without these two explicit annotations Spring
// Data JPA never finds them. Standard Spring Boot multi-module fix, not scope creep.
@SpringBootApplication(scanBasePackages = ["sallim"])
@EnableJpaRepositories(basePackages = ["sallim"])
@EntityScan(basePackages = ["sallim"])
@EnableScheduling
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
```
(`ChoreInstanceScheduler`의 `@Component`는 `sallim` 하위라 기존 컴포넌트 스캔 범위에 이미 포함됨 — 별도 스캔 설정 불필요)

- [ ] **Step 6: bootstrap 부팅 검증 (Docker Desktop 필요)**

Run: `./gradlew :bootstrap:test`
Expected: PASS — `SallimApplicationTests.contextLoads()`가 `ChoreInstanceScheduler` 빈까지 포함해 전체 컨텍스트를 정상 로드

- [ ] **Step 7: chore 모듈 전체 + 루트 빌드 검증 (Docker Desktop 필요)**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1~3에서 작성한 모든 테스트 통과 — application/api 테스트 + persistence 통합 테스트)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/api/ChoreInstanceScheduler.kt chore/src/test/kotlin/sallim/chore/api/ChoreInstanceSchedulerTest.kt bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt
git commit -m "feat: 자정 배치로 반복 인스턴스 자동 생성 (ChoreInstanceScheduler)"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- 정의 생성 즉시 오늘 인스턴스 생성 → Task 1
- 소급 보정(catch-up) → Task 2 `generateDueInstances`
- 유니크 제약으로 중복 방지 → Task 2 `V4` 마이그레이션 + persistence 테스트
- 스케줄러는 `api` 패키지, 얇은 배선만 → Task 3
- 주기 변경 시 별도 처리 불필요 → `generateDueInstances`가 항상 `definition.recurrence`(현재 값)로 계산하므로 코드 변경 없이 자연히 충족, 별도 태스크 불필요

**Placeholder scan:** 전 단계 실제 코드/SQL/커맨드 포함. TBD/TODO 없음.

**Type consistency:** `ChoreInstance.schedule`(기존, Task1/2/3 공통 사용) → `ChoreDefinitionService.create()`(Task1, `ChoreInstanceRepository.save` 직접 호출) → `ChoreInstanceService.generateDueInstances(definitions: List<ChoreDefinition>, today: LocalDate): List<ChoreInstance>`(Task2) → `ChoreInstanceScheduler.generateDueInstances()`(Task3, 파라미터 없이 `choreDefinitionService.list()`+`LocalDate.now()`로 호출) 순서로 시그니처 일치.
