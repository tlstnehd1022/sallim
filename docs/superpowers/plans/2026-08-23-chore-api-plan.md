# Chore API 레이어 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chore` 모듈에 리포지토리 포트 확장 + `application`(유스케이스 서비스) + `api`(REST 컨트롤러) 레이어를 얹고, `bootstrap`을 실제 MySQL 데이터소스로 부팅 가능하게 만든다.

**Architecture:** 기존 `domain`/`infrastructure.persistence` 위에 `application`(서비스, Docker 불필요 — 페이크 리포지토리로 테스트) → `api`(REST 컨트롤러, Docker 불필요 — `@WebMvcTest`+페이크 서비스로 테스트) 순으로 얇게 얹는다. 마지막 태스크에서 `bootstrap`의 컴포넌트 스캔 제외 필터를 걷어내고 실제 datasource를 연결한다.

**Tech Stack:** Kotlin/Spring Boot 3.3.4 · Spring MVC · Spring Data JPA · Kotest(서비스 단위 테스트) · JUnit5 + MockMvc(컨트롤러) · Testcontainers-MySQL 1.21.4(리포지토리 어댑터 확장 + bootstrap 부팅 테스트)

**Spec:** `docs/superpowers/specs/2026-08-21-chore-api-design.md` (및 `docs/superpowers/specs/2026-08-20-chore-persistence-design.md`, `sallim-master-spec.md` 8장)

## Global Constraints

- `domain` 패키지는 Spring/JPA 의존 금지 — `application`/`api`/`infrastructure`는 자유 (CLAUDE.md)
- YAGNI — 유스케이스별 클래스 분리 없이 애그리거트당 서비스 1개. Mockk 등 목킹 라이브러리 새로 추가 금지, 페이크 리포지토리로 대체
- 인스턴스 자동 생성(스케줄러)·완료 취소·시드 데이터 DB 적재·household 실연동·이벤트 발행·인증 — 전부 이번 범위 밖
- household/멤버 식별: 인증 없이 단일 household 고정, `assigneeId`는 요청 바디의 opaque UUID를 검증 없이 그대로 사용
- PUT은 항상 전체 교체(PATCH 없음). 방/할일 삭제는 하위 리소스까지 cascade. 삭제 확인 UX는 클라이언트 책임
- 에러 응답은 `{"error": "메시지"}` 하나로 통일 (RFC 7807 아님)
- **Docker 필요 태스크: Task 1(리포지토리 어댑터 확장), Task 4(bootstrap 실부팅)** — 이 머신은 Docker Desktop 실행 중이며 `DOCKER_HOST=tcp://localhost:2375`로 노출돼 있음(TCP 무TLS 노출 설정 켜둔 상태). Task 2/3(application/api 레이어)은 페이크 리포지토리/`@WebMvcTest`만 쓰므로 Docker 불필요.
- `gradle/libs.versions.toml`의 `testcontainers = "1.21.4"` 오버라이드 패턴(각 모듈 `build.gradle.kts`의 `dependencyManagement { dependencies { dependencySet(...) } }`)을 그대로 재사용 — Spring Boot BOM이 관리하는 1.19.8은 이 머신의 최신 Docker 엔진과 `client version too old`로 충돌한다 (`chore/build.gradle.kts`에 이미 적용됨, `bootstrap/build.gradle.kts`에도 동일하게 추가해야 함)

---

## File Structure

```
chore/
  src/main/kotlin/sallim/chore/domain/
    RoomRepository.kt              (수정: findById/deleteById 추가)
    ChoreDefinitionRepository.kt   (수정: findById/deleteById 추가)
    ChoreInstanceRepository.kt     (수정: findAll/deleteById 추가)
  src/main/kotlin/sallim/chore/infrastructure/persistence/
    RoomRepositoryAdapter.kt              (수정)
    ChoreDefinitionRepositoryAdapter.kt   (수정)
    ChoreInstanceRepositoryAdapter.kt     (수정)
  src/test/kotlin/sallim/chore/infrastructure/persistence/
    RoomRepositoryAdapterTest.kt              (수정: 케이스 추가)
    ChoreDefinitionRepositoryAdapterTest.kt   (수정: 케이스 추가)
    ChoreInstanceRepositoryAdapterTest.kt     (수정: 케이스 추가)
  src/main/kotlin/sallim/chore/application/
    NotFoundException.kt
    RoomService.kt
    ChoreDefinitionService.kt
    ChoreInstanceService.kt
    CleanlinessService.kt          (+ RoomCleanliness)
  src/test/kotlin/sallim/chore/application/
    FakeRoomRepository.kt
    FakeChoreDefinitionRepository.kt
    FakeChoreInstanceRepository.kt
    RoomServiceTest.kt
    ChoreDefinitionServiceTest.kt
    ChoreInstanceServiceTest.kt
    CleanlinessServiceTest.kt
  src/main/kotlin/sallim/chore/api/
    RoomController.kt              (+ RoomRequest/RoomResponse)
    ChoreDefinitionController.kt   (+ DTOs)
    ChoreInstanceController.kt     (+ DTOs)
    CleanlinessController.kt       (+ CleanlinessResponse)
    ApiExceptionHandler.kt
  src/test/kotlin/sallim/chore/api/
    RoomControllerTest.kt
    ChoreDefinitionControllerTest.kt
    ChoreInstanceControllerTest.kt
    CleanlinessControllerTest.kt
  build.gradle.kts                 (수정: spring-boot-starter-web 추가)
bootstrap/
  src/main/kotlin/sallim/bootstrap/SallimApplication.kt   (수정: 원래대로 복원)
  src/main/resources/application.yml                       (수정: 실 datasource)
  src/test/kotlin/sallim/bootstrap/
    AbstractMySqlIntegrationTest.kt   (신규)
    SallimApplicationTests.kt         (수정)
  build.gradle.kts                   (수정: testcontainers 의존성 + BOM 오버라이드)
gradle/libs.versions.toml            (수정: spring-boot-starter-web 카탈로그 항목 추가)
```

---

### Task 1: 리포지토리 포트 확장 + JPA 어댑터 (Docker 필요)

**Files:**
- Modify: `chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapter.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapter.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapterTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: 기존 `RoomJpaRepository`/`ChoreDefinitionJpaRepository`/`ChoreInstanceJpaRepository`(모두 `JpaRepository<Entity, String>`), 기존 엔티티 `toDomain()` 변환 로직
- Produces: `RoomRepository.findById(RoomId): Pair<Room, RoomPlacement>?`, `.deleteById(RoomId)`; `ChoreDefinitionRepository.findById(ChoreDefinitionId): ChoreDefinition?`, `.deleteById(ChoreDefinitionId)`; `ChoreInstanceRepository.findAll(): List<ChoreInstance>`, `.deleteById(ChoreInstanceId)` — Task 2의 세 서비스가 이 메서드들을 그대로 호출한다.

- [ ] **Step 1: RoomRepository 포트에 메서드 추가**

`chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt` 전체를 다음으로 교체:
```kotlin
package sallim.chore.domain

interface RoomRepository {
    fun save(room: Room, placement: RoomPlacement): Room
    fun findAll(): List<Pair<Room, RoomPlacement>>
    fun findById(id: RoomId): Pair<Room, RoomPlacement>?
    fun deleteById(id: RoomId)
}
```

- [ ] **Step 2: RoomRepositoryAdapter에 findById/deleteById 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapterTest.kt`의 `import` 블록에 `io.kotest.matchers.nulls.shouldNotBeNull`을 추가하고, 클래스 마지막 `}` 직전에 다음 3개 테스트를 추가:
```kotlin
    @Test
    fun `저장한 방을 id로 조회하면 값이 같다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        adapter.save(room, placement)
        em.flush()
        em.clear()

        val found = adapter.findById(room.id)

        found.shouldNotBeNull()
        found.first.id shouldBe room.id
        found.second shouldBe placement
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(RoomId.generate()) shouldBe null
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        adapter.save(room, placement)
        em.flush()
        em.clear()

        adapter.deleteById(room.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }
```

- [ ] **Step 3: 테스트 실행 → 컴파일 실패 확인**

Run (저장소 루트에서, Docker Desktop이 떠 있어야 함):
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :chore:compileTestKotlin
```
Expected: FAIL — `Class 'RoomRepositoryAdapter' is not abstract and does not implement abstract member findById/deleteById` (아직 어댑터에 구현 안 함)

- [ ] **Step 4: RoomRepositoryAdapter 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapter.kt`의 클래스 본문(`findAll()` 뒤, 마지막 `}` 앞)에 추가:
```kotlin
    override fun findById(id: RoomId): Pair<Room, RoomPlacement>? =
        jpaRepository.findById(id.value.toString()).map { entity ->
            val roomId = RoomId(UUID.fromString(entity.id))
            Room(roomId, entity.name) to RoomPlacement(roomId, entity.x, entity.y, entity.w, entity.h, entity.z)
        }.orElse(null)

    override fun deleteById(id: RoomId) {
        jpaRepository.deleteById(id.value.toString())
    }
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run:
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.RoomRepositoryAdapterTest"
```
Expected: PASS (5개 테스트: 기존 2개 + 신규 3개)

- [ ] **Step 6: ChoreDefinitionRepository 포트에 메서드 추가**

`chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt` 전체 교체:
```kotlin
package sallim.chore.domain

interface ChoreDefinitionRepository {
    fun save(choreDefinition: ChoreDefinition): ChoreDefinition
    fun findAll(): List<ChoreDefinition>
    fun findById(id: ChoreDefinitionId): ChoreDefinition?
    fun deleteById(id: ChoreDefinitionId)
}
```

- [ ] **Step 7: ChoreDefinitionRepositoryAdapter에 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt`는 이미 `private fun choreDefinition(recurrence, steps): ChoreDefinition` 헬퍼(기본값 `Daily`/`listOf("헹구기")`, `label`은 항상 "설거지")를 갖고 있다 — 그대로 재사용한다. `import` 블록에 `io.kotest.matchers.collections.shouldHaveSize`와 `io.kotest.matchers.nulls.shouldNotBeNull`을 추가하고, 클래스 마지막 `}` 직전에 추가:
```kotlin
    @Test
    fun `저장한 정의를 id로 조회하면 값이 같다`() {
        val definition = choreDefinition()
        adapter.save(definition)
        em.flush()
        em.clear()

        val found = adapter.findById(definition.id)

        found.shouldNotBeNull()
        found.id shouldBe definition.id
        found.label shouldBe definition.label
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(ChoreDefinitionId.generate()) shouldBe null
    }

    @Test
    fun `삭제하면 findAll에서 사라진다`() {
        val definition = choreDefinition()
        adapter.save(definition)
        em.flush()
        em.clear()

        adapter.deleteById(definition.id)
        em.flush()
        em.clear()

        adapter.findAll() shouldHaveSize 0
    }
```

- [ ] **Step 8: ChoreDefinitionRepositoryAdapter 구현**

클래스 본문(`findAll()` 뒤, `private fun toDomain()` 앞 또는 뒤 아무 곳)에 추가:
```kotlin
    override fun findById(id: ChoreDefinitionId): ChoreDefinition? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun deleteById(id: ChoreDefinitionId) {
        jpaRepository.deleteById(id.value.toString())
    }
```

- [ ] **Step 9: 테스트 실행 → 통과 확인**

Run:
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreDefinitionRepositoryAdapterTest"
```
Expected: PASS

- [ ] **Step 10: ChoreInstanceRepository 포트에 메서드 추가**

`chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt` 전체 교체:
```kotlin
package sallim.chore.domain

interface ChoreInstanceRepository {
    fun save(choreInstance: ChoreInstance): ChoreInstance
    fun findById(id: ChoreInstanceId): ChoreInstance?
    fun findAll(): List<ChoreInstance>
    fun deleteById(id: ChoreInstanceId)
}
```

- [ ] **Step 11: ChoreInstanceRepositoryAdapter에 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt` 클래스 마지막 `}` 직전에 추가:
```kotlin
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
```
`import io.kotest.matchers.collections.shouldHaveSize`가 없다면 추가.

- [ ] **Step 12: ChoreInstanceRepositoryAdapter 구현**

클래스 본문(`findById()` 뒤)에 추가:
```kotlin
    override fun findAll(): List<ChoreInstance> =
        jpaRepository.findAll().map { it.toDomain() }

    override fun deleteById(id: ChoreInstanceId) {
        jpaRepository.deleteById(id.value.toString())
    }
```

- [ ] **Step 13: chore 모듈 전체 테스트로 통과 확인**

Run:
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :chore:test
```
Expected: PASS (기존 34개 + 신규 8개 = 42개 전체 통과)

- [ ] **Step 14: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure chore/src/test/kotlin/sallim/chore/infrastructure
git commit -m "feat: chore 리포지토리 포트에 findById/deleteById 확장 (API 레이어 준비)"
```

---

### Task 2: application 레이어 — 유스케이스 서비스 (Docker 불필요)

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/application/NotFoundException.kt`
- Create: `chore/src/main/kotlin/sallim/chore/application/RoomService.kt`
- Create: `chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt`
- Create: `chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt`
- Create: `chore/src/main/kotlin/sallim/chore/application/CleanlinessService.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/FakeRoomRepository.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/FakeChoreDefinitionRepository.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/FakeChoreInstanceRepository.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/RoomServiceTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/CleanlinessServiceTest.kt`

**Interfaces:**
- Consumes: Task 1의 `RoomRepository`/`ChoreDefinitionRepository`/`ChoreInstanceRepository` 포트(findById/deleteById 포함), `domain`의 `Room`/`RoomPlacement`/`FloorPlan`/`ChoreDefinition`/`ChoreInstance`/`CleanlinessScore`/각 Id 타입/`RecurrencePolicy`
- Produces: `RoomService{list, create, update, delete}`, `ChoreDefinitionService{list, create, update, delete}`, `ChoreInstanceService{listByDate, complete}`, `CleanlinessService.scoresForAllRooms()`, `RoomCleanliness(roomId, score)`, `NotFoundException` — Task 3의 4개 컨트롤러가 이 서비스들을 생성자로 주입받는다.

- [ ] **Step 1: NotFoundException 작성**

`chore/src/main/kotlin/sallim/chore/application/NotFoundException.kt`:
```kotlin
package sallim.chore.application

class NotFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 2: 페이크 리포지토리 3종 작성**

`chore/src/test/kotlin/sallim/chore/application/FakeRoomRepository.kt`:
```kotlin
package sallim.chore.application

import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository

class FakeRoomRepository : RoomRepository {
    private val store = mutableMapOf<RoomId, Pair<Room, RoomPlacement>>()

    override fun save(room: Room, placement: RoomPlacement): Room {
        store[room.id] = room to placement
        return room
    }

    override fun findAll(): List<Pair<Room, RoomPlacement>> = store.values.toList()

    override fun findById(id: RoomId): Pair<Room, RoomPlacement>? = store[id]

    override fun deleteById(id: RoomId) {
        store.remove(id)
    }
}
```

`chore/src/test/kotlin/sallim/chore/application/FakeChoreDefinitionRepository.kt`:
```kotlin
package sallim.chore.application

import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository

class FakeChoreDefinitionRepository : ChoreDefinitionRepository {
    private val store = mutableMapOf<ChoreDefinitionId, ChoreDefinition>()

    override fun save(choreDefinition: ChoreDefinition): ChoreDefinition {
        store[choreDefinition.id] = choreDefinition
        return choreDefinition
    }

    override fun findAll(): List<ChoreDefinition> = store.values.toList()

    override fun findById(id: ChoreDefinitionId): ChoreDefinition? = store[id]

    override fun deleteById(id: ChoreDefinitionId) {
        store.remove(id)
    }
}
```

`chore/src/test/kotlin/sallim/chore/application/FakeChoreInstanceRepository.kt`:
```kotlin
package sallim.chore.application

import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository

class FakeChoreInstanceRepository : ChoreInstanceRepository {
    private val store = mutableMapOf<ChoreInstanceId, ChoreInstance>()

    override fun save(choreInstance: ChoreInstance): ChoreInstance {
        store[choreInstance.id] = choreInstance
        return choreInstance
    }

    override fun findById(id: ChoreInstanceId): ChoreInstance? = store[id]

    override fun findAll(): List<ChoreInstance> = store.values.toList()

    override fun deleteById(id: ChoreInstanceId) {
        store.remove(id)
    }
}
```

- [ ] **Step 3: RoomService 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/RoomServiceTest.kt`:
```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.RoomId
import java.time.LocalDate

class RoomServiceTest : FunSpec({
    fun newService(): Triple<RoomService, FakeRoomRepository, Pair<FakeChoreDefinitionRepository, FakeChoreInstanceRepository>> {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        return Triple(RoomService(rooms, definitions, instances), rooms, definitions to instances)
    }

    test("방을 생성하면 조회된다") {
        val (service) = newService()

        val room = service.create("거실", 26, 38, 74, 50, 1)

        service.list() shouldHaveSize 1
        service.list().first().first.id shouldBe room.id
    }

    test("범위를 벗어난 좌표로 생성하면 IllegalArgumentException") {
        val (service) = newService()

        shouldThrow<IllegalArgumentException> { service.create("거실", 95, 0, 50, 10, 1) }
    }

    test("존재하지 않는 방을 수정하면 NotFoundException") {
        val (service) = newService()

        shouldThrow<NotFoundException> { service.update(RoomId.generate(), "거실", 0, 0, 10, 10, 1) }
    }

    test("방을 삭제하면 그 방의 할 일 정의와 인스턴스도 함께 삭제된다") {
        val (service, rooms, defAndInst) = newService()
        val (definitions, instances) = defAndInst
        val room = service.create("거실", 26, 38, 74, 50, 1)
        val definition = ChoreDefinition(
            ChoreDefinitionId.generate(), room.id, "청소", MemberId.generate(), Daily, listOf("단계1"), "영상"
        )
        definitions.save(definition)
        val instance = ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 20))
        instances.save(instance)

        service.delete(room.id)

        rooms.findAll() shouldHaveSize 0
        definitions.findAll() shouldHaveSize 0
        instances.findAll() shouldHaveSize 0
    }
})
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.RoomServiceTest"`
Expected: FAIL — `RoomService`/`FakeRoomRepository` 등을 찾을 수 없음 (컴파일 에러). Docker 불필요.

- [ ] **Step 5: RoomService 구현**

`chore/src/main/kotlin/sallim/chore/application/RoomService.kt`:
```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.FloorPlan
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository

@Service
class RoomService(
    private val roomRepository: RoomRepository,
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun list(): List<Pair<Room, RoomPlacement>> = roomRepository.findAll()

    fun create(name: String, x: Int, y: Int, w: Int, h: Int, z: Int): Room {
        val room = Room(RoomId.generate(), name)
        val placement = RoomPlacement(room.id, x, y, w, h, z)
        FloorPlan.of(listOf(placement))
        return roomRepository.save(room, placement)
    }

    fun update(id: RoomId, name: String, x: Int, y: Int, w: Int, h: Int, z: Int): Room {
        roomRepository.findById(id) ?: throw NotFoundException("room not found: $id")
        val room = Room(id, name)
        val placement = RoomPlacement(id, x, y, w, h, z)
        FloorPlan.of(listOf(placement))
        return roomRepository.save(room, placement)
    }

    fun delete(id: RoomId) {
        roomRepository.findById(id) ?: throw NotFoundException("room not found: $id")
        val definitionIds = choreDefinitionRepository.findAll()
            .filter { it.roomId == id }
            .map { it.id }
        choreInstanceRepository.findAll()
            .filter { it.choreDefinitionId in definitionIds }
            .forEach { choreInstanceRepository.deleteById(it.id) }
        definitionIds.forEach { choreDefinitionRepository.deleteById(it) }
        roomRepository.deleteById(id)
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.RoomServiceTest"`
Expected: PASS

- [ ] **Step 7: ChoreDefinitionService 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/ChoreDefinitionServiceTest.kt`:
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
})
```

- [ ] **Step 8: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreDefinitionServiceTest"`
Expected: FAIL — `ChoreDefinitionService`를 찾을 수 없음

- [ ] **Step 9: ChoreDefinitionService 구현**

`chore/src/main/kotlin/sallim/chore/application/ChoreDefinitionService.kt`:
```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomRepository

@Service
class ChoreDefinitionService(
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val roomRepository: RoomRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun list(): List<ChoreDefinition> = choreDefinitionRepository.findAll()

    fun create(
        label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = ChoreDefinition(
            ChoreDefinitionId.generate(), roomId, label, assigneeId, recurrence, howToSteps, videoQuery
        )
        return choreDefinitionRepository.save(definition)
    }

    fun update(
        id: ChoreDefinitionId, label: String, roomId: RoomId, assigneeId: MemberId,
        recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
    ): ChoreDefinition {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
        val definition = ChoreDefinition(id, roomId, label, assigneeId, recurrence, howToSteps, videoQuery)
        return choreDefinitionRepository.save(definition)
    }

    fun delete(id: ChoreDefinitionId) {
        choreDefinitionRepository.findById(id) ?: throw NotFoundException("chore definition not found: $id")
        choreInstanceRepository.findAll()
            .filter { it.choreDefinitionId == id }
            .forEach { choreInstanceRepository.deleteById(it.id) }
        choreDefinitionRepository.deleteById(id)
    }
}
```

- [ ] **Step 10: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreDefinitionServiceTest"`
Expected: PASS

- [ ] **Step 11: ChoreInstanceService 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt`:
```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.LocalDate

class ChoreInstanceServiceTest : FunSpec({
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
})
```

- [ ] **Step 12: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: FAIL — `ChoreInstanceService`를 찾을 수 없음

- [ ] **Step 13: ChoreInstanceService 구현**

`chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt`:
```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.time.LocalDate

@Service
class ChoreInstanceService(private val choreInstanceRepository: ChoreInstanceRepository) {
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        return choreInstanceRepository.save(instance)
    }
}
```

- [ ] **Step 14: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: PASS

- [ ] **Step 15: CleanlinessService 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/CleanlinessServiceTest.kt`:
```kotlin
package sallim.chore.application

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import java.time.LocalDate

class CleanlinessServiceTest : FunSpec({
    test("방마다 하나의 청결도 점수를 계산한다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        val room = roomService.create("거실", 26, 38, 74, 50, 1)
        val definition = ChoreDefinition(
            sallim.chore.domain.ChoreDefinitionId.generate(), room.id, "청소",
            MemberId.generate(), Daily, listOf("단계1"), "영상"
        )
        definitions.save(definition)
        instances.save(ChoreInstance.schedule(definition.id, LocalDate.of(2026, 8, 10)))
        val service = CleanlinessService(rooms, definitions, instances)

        val scores = service.scoresForAllRooms(referenceDate = LocalDate.of(2026, 8, 20))

        scores shouldHaveSize 1
        scores.first().roomId shouldBe room.id
        (scores.first().score > 0) shouldBe true
    }

    test("할 일이 없는 방은 청결도 0이다") {
        val rooms = FakeRoomRepository()
        val definitions = FakeChoreDefinitionRepository()
        val instances = FakeChoreInstanceRepository()
        val roomService = RoomService(rooms, definitions, instances)
        roomService.create("거실", 26, 38, 74, 50, 1)
        val service = CleanlinessService(rooms, definitions, instances)

        val scores = service.scoresForAllRooms(referenceDate = LocalDate.of(2026, 8, 20))

        scores.first().score shouldBe 0.0
    }
})
```

- [ ] **Step 16: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.CleanlinessServiceTest"`
Expected: FAIL — `CleanlinessService`를 찾을 수 없음

- [ ] **Step 17: CleanlinessService 구현**

`chore/src/main/kotlin/sallim/chore/application/CleanlinessService.kt`:
```kotlin
package sallim.chore.application

import org.springframework.stereotype.Service
import sallim.chore.domain.CleanlinessScore
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomRepository
import java.time.LocalDate

@Service
class CleanlinessService(
    private val roomRepository: RoomRepository,
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun scoresForAllRooms(referenceDate: LocalDate = LocalDate.now()): List<RoomCleanliness> {
        val definitionsByRoom = choreDefinitionRepository.findAll().groupBy { it.roomId }
        val instancesByDefinition = choreInstanceRepository.findAll().groupBy { it.choreDefinitionId }
        return roomRepository.findAll().map { (room, _) ->
            val instances = (definitionsByRoom[room.id] ?: emptyList())
                .flatMap { instancesByDefinition[it.id] ?: emptyList() }
            RoomCleanliness(room.id, CleanlinessScore.compute(instances, referenceDate))
        }
    }
}

data class RoomCleanliness(val roomId: RoomId, val score: Double)
```

- [ ] **Step 18: 전체 application 테스트 + chore 모듈 전체로 통과 확인**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1의 42개 + Task 2의 13개 = 55개 전체 통과). 이 커맨드는 `chore:test`에 어댑터 테스트(Docker 필요)도 섞여 있으므로 `DOCKER_HOST=tcp://localhost:2375` 환경변수를 유지한 채 실행할 것.

- [ ] **Step 19: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/application chore/src/test/kotlin/sallim/chore/application
git commit -m "feat: chore application 레이어 — Room/ChoreDefinition/ChoreInstance/Cleanliness 서비스"
```

---

### Task 3: api 레이어 — REST 컨트롤러 (Docker 불필요)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `chore/build.gradle.kts`
- Create: `chore/src/main/kotlin/sallim/chore/api/RoomController.kt`
- Create: `chore/src/main/kotlin/sallim/chore/api/ChoreDefinitionController.kt`
- Create: `chore/src/main/kotlin/sallim/chore/api/ChoreInstanceController.kt`
- Create: `chore/src/main/kotlin/sallim/chore/api/CleanlinessController.kt`
- Create: `chore/src/main/kotlin/sallim/chore/api/ApiExceptionHandler.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/RoomControllerTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/ChoreDefinitionControllerTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/ChoreInstanceControllerTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/api/CleanlinessControllerTest.kt`

**Interfaces:**
- Consumes: Task 2의 4개 서비스 + `RoomCleanliness`/`NotFoundException`, Task 2의 페이크 리포지토리(컨트롤러 테스트에서 서비스를 페이크 리포지토리로 직접 구성해 주입)
- Produces: `GET/POST /api/rooms`, `PUT/DELETE /api/rooms/{id}`, `GET/POST /api/chore-definitions`, `PUT/DELETE /api/chore-definitions/{id}`, `GET /api/chore-instances?date=`, `POST /api/chore-instances/{id}/complete`, `GET /api/cleanliness` — bootstrap이 실부팅되면(Task 4) 이 엔드포인트들이 그대로 뜬다.

- [ ] **Step 1: 버전 카탈로그에 spring-boot-starter-web 추가**

`gradle/libs.versions.toml`의 `[libraries]` 섹션에서 `spring-boot-starter-data-jpa` 줄 아래에 추가:
```toml
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
```

- [ ] **Step 2: chore 모듈에 web starter 의존성 추가**

`chore/build.gradle.kts`의 `dependencies { }` 블록에서 `implementation(libs.spring.boot.starter.data.jpa)` 아래 줄에 추가:
```kotlin
    implementation(libs.spring.boot.starter.web)
```

- [ ] **Step 3: RoomController 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/api/RoomControllerTest.kt`:
```kotlin
package sallim.chore.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
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
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService

@WebMvcTest(RoomController::class)
@Import(RoomControllerTest.TestConfig::class, ApiExceptionHandler::class)
class RoomControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    class TestConfig {
        @Bean
        fun roomService(): RoomService =
            RoomService(FakeRoomRepository(), FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var roomService: RoomService

    @Test
    fun `방을 생성하고 목록에서 조회한다`() {
        mockMvc.perform(
            post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RoomRequest("거실", 26, 38, 74, 50, 1)))
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("거실"))

        mockMvc.perform(get("/api/rooms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Test
    fun `존재하지 않는 방을 수정하면 404와 에러 바디를 반환한다`() {
        mockMvc.perform(
            put("/api/rooms/${java.util.UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RoomRequest("거실", 0, 0, 10, 10, 1)))
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `방을 삭제하면 204를 반환한다`() {
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(delete("/api/rooms/${room.id.value}"))
            .andExpect(status().isNoContent)
    }
}
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.RoomControllerTest"` (Docker 불필요, `DOCKER_HOST` 안 씌워도 됨)
Expected: FAIL — `RoomController`/`RoomRequest`/`ApiExceptionHandler`를 찾을 수 없음

- [ ] **Step 5: ApiExceptionHandler 구현**

`chore/src/main/kotlin/sallim/chore/api/ApiExceptionHandler.kt`:
```kotlin
package sallim.chore.api

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import sallim.chore.application.NotFoundException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = ResponseEntity.status(404).body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(e: IllegalArgumentException) = ResponseEntity.status(400).body(mapOf("error" to e.message))

    @ExceptionHandler(IllegalStateException::class)
    fun conflict(e: IllegalStateException) = ResponseEntity.status(409).body(mapOf("error" to e.message))
}
```

- [ ] **Step 6: RoomController 구현**

`chore/src/main/kotlin/sallim/chore/api/RoomController.kt`:
```kotlin
package sallim.chore.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.RoomService
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import java.util.UUID

data class RoomRequest(val name: String, val x: Int, val y: Int, val w: Int, val h: Int, val z: Int)
data class RoomResponse(val id: UUID, val name: String, val x: Int, val y: Int, val w: Int, val h: Int, val z: Int)

@RestController
@RequestMapping("/api/rooms")
class RoomController(private val roomService: RoomService) {

    @GetMapping
    fun list(): List<RoomResponse> = roomService.list().map { (room, placement) -> room.toResponse(placement) }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: RoomRequest): RoomResponse {
        val room = roomService.create(request.name, request.x, request.y, request.w, request.h, request.z)
        return room.toResponse(RoomPlacement(room.id, request.x, request.y, request.w, request.h, request.z))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: RoomRequest): RoomResponse {
        val room = roomService.update(RoomId(id), request.name, request.x, request.y, request.w, request.h, request.z)
        return room.toResponse(RoomPlacement(room.id, request.x, request.y, request.w, request.h, request.z))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        roomService.delete(RoomId(id))
    }

    private fun Room.toResponse(placement: RoomPlacement) =
        RoomResponse(id.value, name, placement.x, placement.y, placement.w, placement.h, placement.z)
}
```

- [ ] **Step 7: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.RoomControllerTest"`
Expected: PASS

- [ ] **Step 8: ChoreDefinitionController 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/api/ChoreDefinitionControllerTest.kt`:
```kotlin
package sallim.chore.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService

@WebMvcTest(ChoreDefinitionController::class)
@Import(ChoreDefinitionControllerTest.TestConfig::class, ApiExceptionHandler::class)
class ChoreDefinitionControllerTest {

    @TestConfiguration
    class TestConfig {
        private val rooms = FakeRoomRepository()
        private val definitions = FakeChoreDefinitionRepository()
        private val instances = FakeChoreInstanceRepository()

        @Bean
        fun roomServiceForSetup(): RoomService = RoomService(rooms, definitions, instances)

        @Bean
        fun choreDefinitionService(): ChoreDefinitionService = ChoreDefinitionService(definitions, rooms, instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var roomServiceForSetup: RoomService

    @Test
    fun `존재하지 않는 방으로 할 일 정의를 생성하면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/chore-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                            RecurrenceDto("DAILY", null), listOf("단계1"), "영상"
                        )
                    )
                )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `존재하는 방으로 할 일 정의를 생성하면 201을 반환한다`() {
        val room = roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(
            post("/api/chore-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소", room.id.value, java.util.UUID.randomUUID(),
                            RecurrenceDto("WEEKLY_N_TIMES", 3), listOf("단계1"), "영상"
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.label").value("청소"))
            .andExpect(jsonPath("$.recurrence.type").value("WEEKLY_N_TIMES"))
            .andExpect(jsonPath("$.recurrence.times").value(3))
    }
}
```

- [ ] **Step 9: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreDefinitionControllerTest"`
Expected: FAIL — `ChoreDefinitionController`/`ChoreDefinitionRequest`/`RecurrenceDto`를 찾을 수 없음

- [ ] **Step 10: ChoreDefinitionController 구현**

`chore/src/main/kotlin/sallim/chore/api/ChoreDefinitionController.kt`:
```kotlin
package sallim.chore.api

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes
import java.util.UUID

data class RecurrenceDto(val type: String, val times: Int?)

data class ChoreDefinitionRequest(
    val label: String,
    val roomId: UUID,
    val assigneeId: UUID,
    val recurrence: RecurrenceDto,
    val howToSteps: List<String>,
    val videoQuery: String
)

data class ChoreDefinitionResponse(
    val id: UUID,
    val roomId: UUID,
    val label: String,
    val assigneeId: UUID,
    val recurrence: RecurrenceDto,
    val howToSteps: List<String>,
    val videoQuery: String
)

@RestController
@RequestMapping("/api/chore-definitions")
class ChoreDefinitionController(private val choreDefinitionService: ChoreDefinitionService) {

    @GetMapping
    fun list(): List<ChoreDefinitionResponse> = choreDefinitionService.list().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: ChoreDefinitionRequest): ChoreDefinitionResponse =
        choreDefinitionService.create(
            request.label, RoomId(request.roomId), MemberId(request.assigneeId),
            request.recurrence.toDomain(), request.howToSteps, request.videoQuery
        ).toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: ChoreDefinitionRequest): ChoreDefinitionResponse =
        choreDefinitionService.update(
            ChoreDefinitionId(id), request.label, RoomId(request.roomId), MemberId(request.assigneeId),
            request.recurrence.toDomain(), request.howToSteps, request.videoQuery
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        choreDefinitionService.delete(ChoreDefinitionId(id))
    }

    private fun ChoreDefinition.toResponse() =
        ChoreDefinitionResponse(id.value, roomId.value, label, assigneeId.value, recurrence.toDto(), howToSteps, videoQuery)

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

- [ ] **Step 11: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreDefinitionControllerTest"`
Expected: PASS

- [ ] **Step 12: ChoreInstanceController 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/api/ChoreInstanceControllerTest.kt`:
```kotlin
package sallim.chore.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreInstanceService
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(ChoreInstanceController::class)
@Import(ChoreInstanceControllerTest.TestConfig::class, ApiExceptionHandler::class)
class ChoreInstanceControllerTest {

    @TestConfiguration
    class TestConfig {
        val instances = FakeChoreInstanceRepository()

        @Bean
        fun choreInstanceService(): ChoreInstanceService = ChoreInstanceService(instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var testConfig: TestConfig

    @Test
    fun `date 파라미터 없이 조회하면 400을 반환한다`() {
        mockMvc.perform(get("/api/chore-instances")).andExpect(status().isBadRequest)
    }

    @Test
    fun `날짜로 조회하면 200과 목록을 반환한다`() {
        testConfig.instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))

        mockMvc.perform(get("/api/chore-instances").param("date", "2026-08-20"))
            .andExpect(status().isOk)
    }

    @Test
    fun `완료 처리하면 200을 반환한다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        testConfig.instances.save(instance)

        mockMvc.perform(
            post("/api/chore-instances/${instance.id.value}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        ).andExpect(status().isOk)
    }

    @Test
    fun `존재하지 않는 인스턴스를 완료 처리하면 404를 반환한다`() {
        mockMvc.perform(
            post("/api/chore-instances/${UUID.randomUUID()}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        ).andExpect(status().isNotFound)
    }
}
```

- [ ] **Step 13: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreInstanceControllerTest"`
Expected: FAIL — `ChoreInstanceController`/`CompleteRequest`를 찾을 수 없음

- [ ] **Step 14: ChoreInstanceController 구현**

`chore/src/main/kotlin/sallim/chore/api/ChoreInstanceController.kt`:
```kotlin
package sallim.chore.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.ChoreInstanceService
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CompleteRequest(val completedBy: UUID)

data class ChoreInstanceResponse(
    val id: UUID,
    val choreDefinitionId: UUID,
    val scheduledDate: LocalDate,
    val completed: Boolean,
    val completedBy: UUID?,
    val completedAt: Instant?
)

@RestController
@RequestMapping("/api/chore-instances")
class ChoreInstanceController(private val choreInstanceService: ChoreInstanceService) {

    @GetMapping
    fun listByDate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate): List<ChoreInstanceResponse> =
        choreInstanceService.listByDate(date).map { it.toResponse() }

    @PostMapping("/{id}/complete")
    fun complete(@PathVariable id: UUID, @RequestBody request: CompleteRequest): ChoreInstanceResponse =
        choreInstanceService.complete(ChoreInstanceId(id), MemberId(request.completedBy)).toResponse()

    private fun ChoreInstance.toResponse() =
        ChoreInstanceResponse(id.value, choreDefinitionId.value, scheduledDate, completed, completedBy?.value, completedAt)
}
```

`date` 쿼리 파라미터가 아예 없으면 Spring MVC가 필수 `@RequestParam`(기본값 `required = true`) 누락으로 자동 400을 반환한다 — 별도 검증 코드 불필요.

- [ ] **Step 15: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.ChoreInstanceControllerTest"`
Expected: PASS

- [ ] **Step 16: CleanlinessController 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/api/CleanlinessControllerTest.kt`:
```kotlin
package sallim.chore.api

import org.hamcrest.Matchers.hasSize
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
import sallim.chore.application.CleanlinessService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService

@WebMvcTest(CleanlinessController::class)
@Import(CleanlinessControllerTest.TestConfig::class)
class CleanlinessControllerTest {

    @TestConfiguration
    class TestConfig {
        private val rooms = FakeRoomRepository()
        private val definitions = FakeChoreDefinitionRepository()
        private val instances = FakeChoreInstanceRepository()

        @Bean
        fun roomServiceForSetup(): RoomService = RoomService(rooms, definitions, instances)

        @Bean
        fun cleanlinessService(): CleanlinessService = CleanlinessService(rooms, definitions, instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var roomServiceForSetup: RoomService

    @Test
    fun `방 개수만큼 점수를 반환한다`() {
        roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(get("/api/cleanliness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }
}
```

- [ ] **Step 17: 테스트 실행 → 실패 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.CleanlinessControllerTest"`
Expected: FAIL — `CleanlinessController`를 찾을 수 없음

- [ ] **Step 18: CleanlinessController 구현**

`chore/src/main/kotlin/sallim/chore/api/CleanlinessController.kt`:
```kotlin
package sallim.chore.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sallim.chore.application.CleanlinessService
import sallim.chore.application.RoomCleanliness
import java.util.UUID

data class CleanlinessResponse(val roomId: UUID, val score: Double)

@RestController
@RequestMapping("/api/cleanliness")
class CleanlinessController(private val cleanlinessService: CleanlinessService) {

    @GetMapping
    fun list(): List<CleanlinessResponse> =
        cleanlinessService.scoresForAllRooms().map { it.toResponse() }

    private fun RoomCleanliness.toResponse() = CleanlinessResponse(roomId.value, score)
}
```

- [ ] **Step 19: 전체 chore 모듈 테스트로 통과 확인**

Run:
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :chore:test
```
Expected: PASS (Task 1+2의 55개 + Task 3의 13개 = 68개 전체 통과)

- [ ] **Step 20: Commit**

```bash
git add gradle/libs.versions.toml chore/build.gradle.kts chore/src/main/kotlin/sallim/chore/api chore/src/test/kotlin/sallim/chore/api
git commit -m "feat: chore api 레이어 — Room/ChoreDefinition/ChoreInstance/Cleanliness REST 컨트롤러"
```

---

### Task 4: bootstrap 실부팅 (Docker 필요)

**Files:**
- Modify: `bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`
- Modify: `bootstrap/src/main/resources/application.yml`
- Modify: `bootstrap/build.gradle.kts`
- Create: `bootstrap/src/test/kotlin/sallim/bootstrap/AbstractMySqlIntegrationTest.kt`
- Modify: `bootstrap/src/test/kotlin/sallim/bootstrap/SallimApplicationTests.kt`

**Interfaces:**
- Consumes: Task 1~3에서 완성된 `chore` 모듈 전체(도메인/영속성/application/api), `common`/`household`(기존 그대로)
- Produces: 실제로 REST 요청을 받을 수 있는 `sallim` 애플리케이션. 이후 서브프로젝트(캘린더/가계부, 프런트 연동)가 이 위에서 진행한다.

- [ ] **Step 1: bootstrap에 testcontainers 의존성 + BOM 오버라이드 추가**

`bootstrap/build.gradle.kts` 전체를 다음으로 교체:
```kotlin
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    // chore/build.gradle.kts와 동일한 이유(로컬 Docker 엔진과 testcontainers 1.19.8 충돌) —
    // 이 모듈도 자체 datasource로 SallimApplicationTests를 띄우므로 동일 오버라이드가 필요하다.
    dependencies {
        dependencySet("org.testcontainers:${libs.versions.testcontainers.get()}") {
            entry("testcontainers")
            entry("junit-jupiter")
            entry("mysql")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":household"))
    implementation(project(":chore"))
    implementation(project(":calendar"))
    implementation(project(":ledger"))
    implementation(libs.spring.boot.starter)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
```

`org.springframework.boot` 플러그인이 이미 Spring Boot BOM을 자동 임포트하므로(`chore`와 달리 `imports { mavenBom(...) }`가 필요 없음) `dependencyManagement { dependencies { ... } }`만 추가하면 된다.

- [ ] **Step 2: SallimApplication을 원래 형태로 복원**

`bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt` 전체를 다음으로 교체:
```kotlin
package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["sallim"])
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
```

- [ ] **Step 3: application.yml에 실제 datasource 추가**

`bootstrap/src/main/resources/application.yml` 전체를 다음으로 교체:
```yaml
spring:
  application:
    name: sallim
  datasource:
    url: jdbc:mysql://localhost:3306/sallim
    username: sallim
    password: sallim
```

- [ ] **Step 4: AbstractMySqlIntegrationTest 작성**

`chore`의 것과 동일한 패턴을 `bootstrap` 테스트 소스셋에도 그대로 둔다(모듈 간 테스트 코드는 공유되지 않으므로 중복이 맞다 — 공유 테스트 픽스처 모듈을 새로 만들 정도의 규모가 아니다).

`bootstrap/src/test/kotlin/sallim/bootstrap/AbstractMySqlIntegrationTest.kt`:
```kotlin
package sallim.bootstrap

import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

class BootstrapMySqlContainer(imageName: String) : MySQLContainer<BootstrapMySqlContainer>(DockerImageName.parse(imageName))

abstract class AbstractMySqlIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: BootstrapMySqlContainer = BootstrapMySqlContainer("mysql:8.0").apply { start() }

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

- [ ] **Step 5: SallimApplicationTests가 실제 컨텍스트를 띄우도록 수정**

`bootstrap/src/test/kotlin/sallim/bootstrap/SallimApplicationTests.kt` 전체를 다음으로 교체:
```kotlin
package sallim.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SallimApplicationTests : AbstractMySqlIntegrationTest() {

    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 6: 부팅 검증**

Run (Docker Desktop이 떠 있어야 함):
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew :bootstrap:test --tests "sallim.bootstrap.SallimApplicationTests"
```
Expected: PASS — 실제 MySQL 컨테이너 위에서 `chore`의 Flyway 마이그레이션 3개가 실행되고 전체 Spring 컨텍스트(도메인 서비스 4개 + 컨트롤러 4개 + JPA 리포지토리 3개)가 정상 기동한다.

이 단계에서 흔한 실패와 대응:
- `Failed to configure a DataSource` 계열 에러가 나면 Step 1의 `bootstrap/build.gradle.kts` 의존성 블록이 정확히 반영됐는지, `application.yml`의 `spring.datasource.*` 키가 오타 없는지 확인
- `command version 1.32 is too old` 계열 에러가 다시 나면 `dependencyManagement` 오버라이드가 빠진 것 — Step 1을 다시 확인
- Flyway 마이그레이션 관련 에러가 나면 `chore`가 `implementation(project(":chore"))`로 걸려 있어 `chore/src/main/resources/db/migration/*.sql`이 bootstrap 런타임 클래스패스에 포함되는지 확인(별도 설정 불필요, 프로젝트 의존성으로 자동 포함됨)

- [ ] **Step 7: 실제 기동으로 REST 엔드포인트 하나 수동 확인 (선택, Docker+로컬 MySQL 필요)**

이 단계는 `bootstrap:test`가 이미 컨텍스트 로드를 검증하므로 필수는 아니지만, 실제 HTTP 응답까지 보고 싶다면:
```bash
export DOCKER_HOST="tcp://localhost:2375"
docker run -d --name sallim-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=sallim -e MYSQL_USER=sallim -e MYSQL_PASSWORD=sallim -p 3306:3306 mysql:8.0
# MySQL 부팅 대기 후 (수십 초)
./gradlew :bootstrap:bootRun &
sleep 20
curl -s http://localhost:8080/api/rooms
docker stop sallim-mysql && docker rm sallim-mysql
```
Expected: `curl` 응답이 `[]` (빈 배열, 아직 방을 안 만들었으므로)

- [ ] **Step 8: 전체 저장소 빌드로 최종 확인**

Run:
```bash
export DOCKER_HOST="tcp://localhost:2375"
./gradlew build
```
Expected: `BUILD SUCCESSFUL` — 모든 모듈(bootstrap/common/household/chore/calendar/ledger) 전체 테스트 통과

- [ ] **Step 9: Commit**

```bash
git add bootstrap
git commit -m "feat: bootstrap 실부팅 — 실제 MySQL datasource 연결, chore API 컴포넌트 스캔 활성화"
```

---

## 다음 단계

- 캘린더/가계부 도메인 (여전히 빈 모듈)
- 반복 인스턴스 자동 생성 스케줄러 (마스터 스펙 8장 7번)
- 완료 취소(uncomplete), 시드 데이터 DB 적재, household 실연동
- 도메인 이벤트 → Kafka (마스터 스펙 8장 8번)
- 모바일 클라이언트가 이 API를 실제로 호출하도록 연동 (지금까지는 목업 데이터만 사용)

## Self-Review

**Spec coverage:** 설계 문서의 리포지토리 포트 확장(Task 1), application 4개 서비스(Task 2), api 4개 컨트롤러 + 예외 핸들러(Task 3), bootstrap 복원(Task 4) 전부 태스크로 커버됨. "테스트는 페이크 리포지토리로, Docker 없이"(Task 2/3), "이번이 유일하게 Docker가 필요한 bootstrap 테스트"(Task 4) 결정이 Global Constraints와 각 태스크 헤더에 명시됨.

**Placeholder scan:** 전 스텝 실제 코드/커맨드. Step 7(수동 curl 확인)만 "선택"으로 표시했는데, 이는 `bootstrap:test`가 이미 컨텍스트 로드로 같은 걸 자동 검증하기 때문— 회피용 TBD가 아니라 의도적으로 낮춘 우선순위임을 명시했다.

**Type consistency:** `RoomService`/`ChoreDefinitionService`/`ChoreInstanceService`/`CleanlinessService`의 생성자 시그니처가 Task 2(정의)와 Task 3(컨트롤러 주입)에서 동일. `RecurrenceDto`/`RoomCleanliness`/`NotFoundException`도 정의된 곳과 사용되는 곳에서 이름·필드 일치. `FakeRoomRepository` 등은 Task 2에서 `src/test`에 정의되고 Task 3의 컨트롤러 테스트가 같은 모듈의 테스트 소스셋에서 그대로 import해 재사용(Gradle 기본 설정상 동일 모듈의 test 소스셋은 서로 다른 패키지라도 컴파일 클래스패스를 공유하므로 별도 설정 불필요).
