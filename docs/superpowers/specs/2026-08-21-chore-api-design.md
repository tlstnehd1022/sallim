# Chore(집안일) API 레이어 — 설계

> 2026-08-21 · sallim-master-spec.md 8장 구현순서 6번 "API 레이어 (Spring Boot REST)"
> 이전 서브프로젝트: `2026-08-20-chore-persistence-design.md` (Room/ChoreDefinition/ChoreInstance JPA 영속성 완료, 단 이 PC의 Docker 고장으로 Testcontainers 통합 테스트 3개는 아직 실제 MySQL 검증 전)

## 범위

- `chore` 모듈에 `application`/`api` 레이어 추가 — 리포지토리 포트 위에 유스케이스 서비스 + REST 컨트롤러
- 대상 애그리거트: `Room`, `ChoreDefinition`, `ChoreInstance` + 이 셋을 조인하는 청결도(Cleanliness) 조회
- 세 리포지토리 포트에 `findById`/`deleteById` 등 API가 요구하는 메서드 추가, JPA 어댑터도 함께 구현
- `bootstrap`을 실제로 부팅 가능하게 만든다 — persistence 서브프로젝트 최종 리뷰에서 임시로 걸어둔 컴포넌트 스캔 제외 필터(`SallimApplication.kt`의 `ponytail:` 주석 참조)를 걷어내고, 진짜 datasource를 연결

**이번 서브프로젝트에서 제외** (다음 서브프로젝트로):
- 반복 인스턴스 자동 생성(자정 배치 스케줄러, 마스터 스펙 8장 7번) — `ChoreInstance`는 API로 생성하지 않고, 이미 존재하는 인스턴스만 조회/완료 처리한다. 스케줄러가 없는 동안은 인스턴스가 비어 있을 수 있음 (알려진 제약, 다음 서브프로젝트가 채움)
- 완료 취소(uncomplete) — 도메인에 해당 메서드가 없고, 이번 범위에도 추가하지 않음
- `DefaultRooms` 시드 데이터 DB 적재, household 실연동(`assigneeId` 검증 등), 도메인 이벤트 → Kafka, CQRS — 이전 설계 문서와 동일하게 범위 밖
- 인증/인가 — 이번에도 없음. 단일 household 고정으로 동작 (아래 결정 사항 참조)

## 결정된 사항

- **호출 범위: 얇게 간다.** 기존 도메인/영속성 위에 CRUD + 완료 처리 REST만 얹는다. 인스턴스 자동 생성은 스케줄러 서브프로젝트로 명확히 미룸 — API 안에 "조회 시 즉석 생성" 같은 임시 로직을 넣지 않는다(마스터 스펙 8장이 6번/7번을 분리해둔 의도를 그대로 따름).
- **household/멤버 식별: 인증 없이 단일 household 고정.** 마스터 스펙이 "본인용 우선"이라 명시하고 있고 인증 계층이 아예 없음. `assigneeId`는 요청 바디로 받은 opaque UUID를 그대로 씀(household 실연동은 범위 밖이므로 존재 검증 안 함).
- **`application` 계층은 애그리거트당 서비스 1개.** `RoomService`/`ChoreDefinitionService`/`ChoreInstanceService` + 셋을 조인하는 `CleanlinessService`. 유스케이스별 클래스(`CreateRoomUseCase` 등)는 지금 규모(단순 CRUD + 완료 처리)엔 과잉설계라 기각 — 나중에 유스케이스 로직이 복잡해지면(예: 7번 스케줄러) 그때 개별 클래스로 승격.
- **방 삭제는 cascade, 확인은 클라이언트 책임.** `Room` 삭제 시 그 방을 참조하는 `ChoreDefinition`과 그 정의의 `ChoreInstance`를 서버가 함께 지운다(cascade). "정말 지우시겠습니까" 확인은 API가 아니라 클라이언트가 자체 UI로 처리 — 클라이언트는 이미 화면에 방별 집안일 목록을 갖고 있어 별도 "미리보기" 엔드포인트가 필요 없다. `ChoreDefinition` 삭제도 같은 이유로 그 정의의 `ChoreInstance`를 cascade 삭제한다.
- **PUT은 항상 전체 교체.** 부분 수정(PATCH)은 지원하지 않는다 — 클라이언트가 항상 리소스 전체 필드를 보낸다. `Room`/`RoomPlacement`/`ChoreDefinition`이 불변 값이라 "기존 값 읽어서 일부만 덮어쓰기"보다 훨씬 단순하다.
- **`ChoreInstanceRepository`에 `findAll()`만 추가**(`findByScheduledDate` 같은 전용 쿼리는 안 만듦). 날짜별 조회든 청결도 계산이든 서비스 레이어에서 메모리 필터링 — 가구 하나 분량 데이터라 전용 쿼리 메서드를 늘릴 이유가 없다.
- **청결도 기준일은 항상 서버의 오늘.** 과거 날짜를 파라미터로 받지 않는다 — 화면에 쓸 데가 없다.
- **에러 응답은 `{"error": "메시지"}` 하나로 통일.** RFC 7807 Problem Details 같은 정식 포맷은 이 규모(개인용 앱)엔 과함.
- **테스트는 페이크 리포지토리로, Docker 없이.** `application`/`api` 계층 테스트는 인메모리 페이크 리포지토리(`MutableMap` 기반)로 작성 — Mockk 등 목킹 라이브러리를 새로 끌어오지 않는다(리포지토리 포트가 2~4개 메서드로 작아서 페이크 작성 비용이 낮음). 컨트롤러는 `@WebMvcTest` + 페이크 서비스 주입으로 `MockMvc` 테스트. 이 서브프로젝트의 자동화 테스트는 통째로 Docker가 필요 없다.
- **`bootstrap`을 진짜로 되살린다.** persistence 서브프로젝트 최종 리뷰에서 "chore의 JPA 영속성 빈을 아직 아무도 안 쓴다"는 이유로 `SallimApplication`의 컴포넌트 스캔에서 `infrastructure.persistence` 패키지를 임시로 제외해뒀다(`SallimApplication.kt`의 `ponytail:` 주석이 "application/api 레이어가 생기면 되돌리라"고 명시). 이번이 바로 그 서브프로젝트이므로:
  - `SallimApplication.kt`를 원래의 단순한 `@SpringBootApplication(scanBasePackages = ["sallim"])`로 되돌린다
  - `bootstrap/src/main/resources/application.yml`에 실제 `spring.datasource.*`(MySQL, 로컬 개발 기준 `localhost:3306`)를 추가한다
  - **부작용:** `SallimApplicationTests.contextLoads()`가 다시 실제 DataSource/JPA 컨텍스트를 띄우게 되므로, `chore`의 `AbstractMySqlIntegrationTest`와 같은 싱글턴 Testcontainers-MySQL 패턴을 `bootstrap` 테스트에도 적용해야 한다(`@DynamicPropertySource`로 `spring.datasource.*` 주입). 즉 `:bootstrap:test`도 Docker가 필요해진다 — 다만 이건 이미 `chore` 통합 테스트에 걸려 있던 요구사항을 그대로 넓히는 것뿐이라 새로운 제약은 아니다.

## 리포지토리 포트 확장 (`sallim.chore.domain`)

기존 파일 수정, 신규 메서드만 추가(기존 메서드 시그니처는 그대로):

```kotlin
// RoomRepository.kt
interface RoomRepository {
    fun save(room: Room, placement: RoomPlacement): Room
    fun findAll(): List<Pair<Room, RoomPlacement>>
    fun findById(id: RoomId): Pair<Room, RoomPlacement>?   // 신규
    fun deleteById(id: RoomId)                              // 신규
}

// ChoreDefinitionRepository.kt
interface ChoreDefinitionRepository {
    fun save(choreDefinition: ChoreDefinition): ChoreDefinition
    fun findAll(): List<ChoreDefinition>
    fun findById(id: ChoreDefinitionId): ChoreDefinition?   // 신규
    fun deleteById(id: ChoreDefinitionId)                    // 신규
}

// ChoreInstanceRepository.kt
interface ChoreInstanceRepository {
    fun save(choreInstance: ChoreInstance): ChoreInstance
    fun findById(id: ChoreInstanceId): ChoreInstance?
    fun findAll(): List<ChoreInstance>                       // 신규
    fun deleteById(id: ChoreInstanceId)                      // 신규
}
```

각 JPA 어댑터(`RoomRepositoryAdapter`/`ChoreDefinitionRepositoryAdapter`/`ChoreInstanceRepositoryAdapter`)에 대응 메서드 구현 추가 — `findById`는 `JpaRepository.findById(id).map { it.toDomain() }`, `deleteById`는 `JpaRepository.deleteById(id.value.toString())` 그대로 위임.

## `application` 계층 (`sallim.chore.application`, 신규)

### `NotFoundException`

```kotlin
class NotFoundException(message: String) : RuntimeException(message)
```
서비스가 `findById`로 못 찾았을 때 던짐 — `ApiExceptionHandler`가 404로 매핑.

### `RoomService`

```kotlin
class RoomService(
    private val roomRepository: RoomRepository,
    private val choreDefinitionRepository: ChoreDefinitionRepository,
    private val choreInstanceRepository: ChoreInstanceRepository
) {
    fun list(): List<Pair<Room, RoomPlacement>> = roomRepository.findAll()

    fun create(name: String, x: Int, y: Int, w: Int, h: Int, z: Int): Room {
        val room = Room(RoomId.generate(), name)
        val placement = RoomPlacement(room.id, x, y, w, h, z)
        FloorPlan.of(listOf(placement))  // 자기 자신의 x/y/w/h 범위만 검증, 실패 시 IllegalArgumentException → 400
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

### `ChoreDefinitionService`

```kotlin
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

`roomId` 존재 확인 실패는 `IllegalArgumentException`(400)으로 던진다 — 못 찾은 대상이 요청 자체의 리소스(`ChoreDefinition`)가 아니라 요청이 참조한 값이므로 404가 아니라 400이 맞다.

### `ChoreInstanceService`

```kotlin
class ChoreInstanceService(private val choreInstanceRepository: ChoreInstanceRepository) {
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)  // 이미 완료면 IllegalStateException → 409
        return choreInstanceRepository.save(instance)
    }
}
```

### `CleanlinessService`

세 애그리거트를 조인하므로 어느 한쪽 서비스에 붙이지 않고 별도로 둔다.

```kotlin
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

## `api` 레이어 (`sallim.chore.api`, 신규)

REST 컨트롤러 + 요청/응답 DTO를 같은 파일에 둔다(DTO가 작은 데이터 클래스라 굳이 분리하지 않음). DTO ↔ 도메인 변환은 컨트롤러가 담당(서비스는 도메인 타입만 다룸).

### `RoomController`

| Method | Path | Body | 응답 |
|---|---|---|---|
| GET | `/api/rooms` | - | `200 [{id, name, x, y, w, h, z}]` |
| POST | `/api/rooms` | `{name, x, y, w, h, z}` | `201 {id, name, x, y, w, h, z}` |
| PUT | `/api/rooms/{id}` | `{name, x, y, w, h, z}` | `200 {...}` / `404` |
| DELETE | `/api/rooms/{id}` | - | `204` / `404` |

### `ChoreDefinitionController`

| Method | Path | Body | 응답 |
|---|---|---|---|
| GET | `/api/chore-definitions` | - | `200 [{id, roomId, label, assigneeId, recurrence, howToSteps, videoQuery}]` |
| POST | `/api/chore-definitions` | 위 필드(id 제외) | `201 {...}` / `400`(roomId 없음) |
| PUT | `/api/chore-definitions/{id}` | 위 필드(id 제외) | `200 {...}` / `404` / `400` |
| DELETE | `/api/chore-definitions/{id}` | - | `204` / `404` |

`recurrence` JSON 표현은 persistence 어댑터의 컬럼 매핑과 같은 컨벤션: `{"type": "DAILY"}` / `{"type": "WEEKLY_N_TIMES", "times": 3}` / `{"type": "MONTHLY"}`.

### `ChoreInstanceController`

| Method | Path | Body | 응답 |
|---|---|---|---|
| GET | `/api/chore-instances?date=2026-08-21` | - | `200 [{id, choreDefinitionId, scheduledDate, completed, completedBy, completedAt}]` / `400`(`date` 파라미터 누락 — 기본값 없이 필수) |
| POST | `/api/chore-instances/{id}/complete` | `{completedBy}` | `200 {...}` / `404` / `409`(이미 완료) |

### `CleanlinessController`

| Method | Path | Body | 응답 |
|---|---|---|---|
| GET | `/api/cleanliness` | - | `200 [{roomId, score}]` |

### `ApiExceptionHandler`

```kotlin
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

## `bootstrap` 배선 변경

- `SallimApplication.kt`를 `@SpringBootApplication(scanBasePackages = ["sallim"])` 하나로 되돌림(3개로 쪼개둔 메타 애노테이션 + exclude 필터 제거)
- `bootstrap/src/main/resources/application.yml`에 추가:
  ```yaml
  spring:
    application:
      name: sallim
    datasource:
      url: jdbc:mysql://localhost:3306/sallim
      username: sallim
      password: sallim
  ```
  (로컬 개발 기준 값 — 실제 로컬 MySQL 기동 방법은 이 서브프로젝트의 구현 계획에서 docker-compose 또는 `docker run`으로 정리)
- `SallimApplicationTests`에 `chore`의 `AbstractMySqlIntegrationTest`와 같은 싱글턴 Testcontainers-MySQL 패턴 적용 — `contextLoads()`가 실제 DataSource를 필요로 하게 되므로

## 테스트 전략

- `RoomServiceTest`/`ChoreDefinitionServiceTest`/`ChoreInstanceServiceTest`/`CleanlinessServiceTest`: 각 리포지토리 포트의 인메모리 페이크(`FakeRoomRepository` 등, `MutableMap` 기반) 주입 — Docker 불필요
- `RoomControllerTest`/`ChoreDefinitionControllerTest`/`ChoreInstanceControllerTest`/`CleanlinessControllerTest`: `@WebMvcTest` + 페이크 서비스 빈 주입, `MockMvc`로 HTTP 왕복 검증(상태 코드 + JSON 바디) — Docker 불필요
- `SallimApplicationTests`: 위에서 결정한 대로 Testcontainers-MySQL 필요(Docker 필요) — 이 서브프로젝트에서 유일하게 Docker가 필요한 테스트
- 이전 서브프로젝트에서 미검증인 `RoomRepositoryAdapterTest`/`ChoreDefinitionRepositoryAdapterTest`/`ChoreInstanceRepositoryAdapterTest`(Testcontainers)는 이번 서브프로젝트 범위 밖이지만, `bootstrap`이 실제 datasource로 부팅되는 김에 **같은 Docker 가능 환경에서 함께 실행해 검증**하는 걸 구현 계획의 첫 태스크로 넣는다(지금 이 PC가 아니라 Docker 정상인 환경에서)

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환. 첫 태스크로 "Docker 환경에서 기존 persistence 통합 테스트 3개 먼저 그린 확인"을 넣어, 이 서브프로젝트가 검증 안 된 코드 위에 쌓이지 않게 한다.
