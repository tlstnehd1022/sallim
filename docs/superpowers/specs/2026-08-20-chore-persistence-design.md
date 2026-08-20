# Chore(집안일) 영속성 — 설계

> 2026-08-20 · sallim-master-spec.md 8장 구현순서 5번 "영속성 (JPA + MySQL, Testcontainers 통합 테스트)"
> 이전 서브프로젝트: `2026-08-19-chore-domain-design.md` (chore 도메인 모델 + 시드 데이터 완료)

## 범위

- `chore` 모듈에 `Room`/`ChoreDefinition`/`ChoreInstance`를 위한 JPA 엔티티 + 리포지토리 어댑터 구현
- 도메인 레이어에 리포지토리 포트 3개 추가, `ChoreInstance`에 재구성(reconstitute) 팩토리 추가
- Flyway로 스키마 관리
- Testcontainers(MySQL) 기반 통합 테스트 — 애그리거트별 save→load 왕복이 도메인 불변식을 지키는지 검증

**이번 서브프로젝트에서 제외** (다음 서브프로젝트로):
- `application`/`api` 레이어 (유스케이스 오케스트레이션, REST 컨트롤러) — 실제 소비자가 생기는 다음 서브프로젝트에서
- `DefaultRooms` 시드 데이터를 DB에 실제 적재 — `ME`/`PARTNER`가 매 JVM 기동마다 랜덤 UUID라 지금 적재하면 재기동/재시딩마다 중복 데이터가 생김. household↔chore 실제 연동(진짜 `MemberId` 주입) 서브프로젝트에서
- QueryDSL 설정 — 지금은 save/findById/findAll만 필요. 복잡한 조회가 실제로 필요해지는 API 서브프로젝트에서 도입
- 자정 배치 스케줄러, 도메인 이벤트 → Kafka, CQRS 통계 조회, household ↔ chore 실제 연동 — 이전 설계 문서와 동일하게 범위 밖

## 결정된 사항

- 마이그레이션 도구: **Flyway** (SQL 파일 기반)
- 이번엔 엔티티/매핑/리포지토리 어댑터까지만 — 시드 데이터 DB 적재는 다음으로 미룸
- QueryDSL은 지금 설치하지 않음 — CLAUDE.md 기술스택엔 있지만 실제로 복잡한 쿼리가 필요해질 때(API 레이어) 도입
- 도메인 클래스에 JPA 애노테이션을 직접 붙이지 않는다 (CLAUDE.md: domain 패키지는 프레임워크 의존 금지) — 별도 `*Entity` 클래스 + 수동 매퍼로 변환
- UUID 기반 ID는 엔티티에서 `String`(문자열화된 UUID)으로 저장 — Hibernate 6의 UUID 컬럼 매핑 방식(다이얼렉트별 상이)에 기대지 않고, Flyway SQL의 `CHAR(36)`과 명확히 대응시키기 위함. 매퍼 경계에서 `UUID.toString()` / `UUID.fromString()`으로 변환

## 모듈/빌드 배선

- `chore/build.gradle.kts`에 `io.spring.dependency-management` 플러그인 **추가** (⚠️ `org.springframework.boot` 플러그인은 추가하지 않음 — chore는 라이브러리 모듈이지 부트 앱이 아니다). 이 플러그인이 Spring Boot BOM을 끌어와 아래 의존성들을 버전 없이 선언할 수 있게 한다:
  - `implementation("org.springframework.boot:spring-boot-starter-data-jpa")`
  - `runtimeOnly("com.mysql:mysql-connector-j")`
  - `implementation("org.flywaydb:flyway-core")`, `runtimeOnly("org.flywaydb:flyway-mysql")`
  - `testImplementation("org.springframework.boot:spring-boot-starter-test")` (이미 kotest만 있던 test 의존성에 Spring 테스트 슬라이스 추가)
  - `testImplementation("org.testcontainers:junit-jupiter")`, `testImplementation("org.testcontainers:mysql")`
- 위 의존성은 모두 `implementation`/`runtimeOnly`이므로 Gradle이 `bootstrap`의 런타임 클래스패스로 자동 전이 — `bootstrap/build.gradle.kts` 변경 불필요
- Flyway 마이그레이션 SQL은 `chore/src/main/resources/db/migration/`에 위치 — Flyway 기본 스캔 경로(`classpath:db/migration`)라 bootstrap 부팅 시 별도 설정 없이 자동 인식
- `bootstrap/src/main/resources/application.yml`은 이번에 건드리지 않는다 — 테스트는 Testcontainers가 `@DynamicPropertySource`로 접속 정보를 주입하므로 실제 로컬/운영 `spring.datasource` 설정이 필요 없다. 로컬 개발용 DB 기동(docker-compose 등)은 이번 범위 밖이라, 지금 값만 채워둔 채 검증 못 할 설정을 남기지 않는다 — 실제로 bootstrap을 기동해야 하는 서브프로젝트(API 레이어)에서 함께 정리

## 도메인 레이어 변경 (`sallim.chore.domain`)

기존 파일 수정 없음(로직 변경 없음), 신규 파일만 추가:

```kotlin
// RoomRepository.kt
interface RoomRepository {
    fun save(room: Room, placement: RoomPlacement): Room
    fun findAll(): List<Pair<Room, RoomPlacement>>
}

// ChoreDefinitionRepository.kt
interface ChoreDefinitionRepository {
    fun save(choreDefinition: ChoreDefinition): ChoreDefinition
    fun findAll(): List<ChoreDefinition>
}

// ChoreInstanceRepository.kt
interface ChoreInstanceRepository {
    fun save(choreInstance: ChoreInstance): ChoreInstance
    fun findById(id: ChoreInstanceId): ChoreInstance?
}
```

`ChoreInstance`에 재구성 팩토리 **추가** (기존 `schedule()`은 그대로 유지, private 생성자도 이미 필요한 파라미터를 다 받고 있어 로직 변경 없이 팩토리만 하나 늘어남):

```kotlin
companion object {
    fun schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance = ...  // 기존 그대로

    fun reconstitute(
        id: ChoreInstanceId,
        choreDefinitionId: ChoreDefinitionId,
        scheduledDate: LocalDate,
        completed: Boolean,
        completedBy: MemberId?,
        completedAt: Instant?
    ): ChoreInstance = ChoreInstance(id, choreDefinitionId, scheduledDate, completed, completedBy, completedAt)
}
```

`reconstitute`는 `registerEvent`를 호출하지 않으므로 이미 완료된 인스턴스를 DB에서 불러와도 `ChoreCompletedEvent`가 재발행되지 않는다 — 인프라 계층의 매퍼만 이 팩토리를 사용한다.

## 인프라 레이어 (`sallim.chore.infrastructure.persistence`, 신규)

### Room

`RoomEntity`(테이블 `room`): `id`(CHAR(36) PK), `name`(VARCHAR), `x`/`y`/`w`/`h`/`z`(INT) — `Room`과 `RoomPlacement`가 1:1이라 한 행에 평평하게 저장, 별도 FloorPlan 테이블 없음.

`RoomJpaRepository : JpaRepository<RoomEntity, String>`.

`RoomRepositoryAdapter : RoomRepository` — `save`는 `RoomEntity`로 변환 후 `RoomJpaRepository.save`, 반환값을 `Room`으로 역변환. `findAll`은 전체 조회 후 각 행을 `Pair(Room, RoomPlacement)`로 매핑 — `FloorPlan.of(pairs.map { it.second })` 조립은 어댑터가 아니라 이 포트를 호출하는 쪽(다음 서브프로젝트의 application 레이어)의 책임으로 남겨둔다 — 지금은 리포지토리가 조립까지 하지 않는다(YAGNI, 실제 호출부가 없다).

### ChoreDefinition

`ChoreDefinitionEntity`(테이블 `chore_definition`): `id`(CHAR(36) PK), `roomId`(CHAR(36)), `label`(VARCHAR), `assigneeId`(CHAR(36)), `recurrenceType`(VARCHAR — `DAILY`/`WEEKLY_N_TIMES`/`MONTHLY`), `recurrenceTimes`(INT, nullable — `WEEKLY_N_TIMES`일 때만), `videoQuery`(VARCHAR).

`howToSteps`는 `@ElementCollection` + `@OrderColumn`으로 별도 테이블 `chore_definition_step`(`chore_definition_id` FK, `step_order` INT, `step` TEXT)에 저장 — 순서 있는 문자열 목록이라 JPA 표준 패턴 그대로 사용.

`recurrence: RecurrencePolicy` ↔ (`recurrenceType`, `recurrenceTimes`) 변환은 매퍼의 `when` 분기:
```kotlin
fun RecurrencePolicy.toColumns(): Pair<String, Int?> = when (this) {
    is Daily -> "DAILY" to null
    is WeeklyNTimes -> "WEEKLY_N_TIMES" to times
    is Monthly -> "MONTHLY" to null
}
fun toRecurrencePolicy(type: String, times: Int?): RecurrencePolicy = when (type) {
    "DAILY" -> Daily
    "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times))
    "MONTHLY" -> Monthly
    else -> error("unknown recurrence type: $type")
}
```

`ChoreDefinitionJpaRepository : JpaRepository<ChoreDefinitionEntity, String>`.
`ChoreDefinitionRepositoryAdapter : ChoreDefinitionRepository` — 표준 save/findAll 위임 + 매핑.

### ChoreInstance

`ChoreInstanceEntity`(테이블 `chore_instance`): `id`(CHAR(36) PK), `choreDefinitionId`(CHAR(36)), `scheduledDate`(DATE), `completed`(BOOLEAN), `completedBy`(CHAR(36), nullable), `completedAt`(TIMESTAMP, nullable).

`ChoreInstanceJpaRepository : JpaRepository<ChoreInstanceEntity, String>`.
`ChoreInstanceRepositoryAdapter : ChoreInstanceRepository` — `save`는 엔티티 변환 후 저장, 반환값 역변환(`ChoreInstance.reconstitute(...)` 사용 — 저장된 인스턴스를 다시 도메인 객체로 만들 때는 항상 reconstitute, `schedule`이 아니다). `findById`도 동일하게 `reconstitute`로 역변환, 없으면 `null`.

## Flyway 마이그레이션 (`chore/src/main/resources/db/migration/`)

- `V1__create_room_table.sql` — `room` 테이블
- `V2__create_chore_definition_tables.sql` — `chore_definition` + `chore_definition_step` 테이블
- `V3__create_chore_instance_table.sql` — `chore_instance` 테이블

FK 제약은 이번 범위에서 걸지 않는다 — `chore_definition.room_id`, `chore_instance.chore_definition_id`는 raw 컬럼으로만 두고 참조 무결성은 애플리케이션 레이어 책임(household도 동일 패턴 없음, DDD에서 애그리거트 간 참조는 ID로만 하고 FK 제약을 강제하지 않는 것도 흔한 선택 — YAGNI, 지금 이 제약이 막아줄 실제 버그가 없다).

## 테스트 전략

`@DataJpaTest` + `@Testcontainers`(MySQL 컨테이너, `@DynamicPropertySource`로 `spring.datasource.*` 주입) + `@AutoConfigureTestDatabase(replace = NONE)`(내장 DB로 치환 방지, 실제 MySQL 컨테이너 사용) + 각 어댑터를 `@Import`.

`chore` 모듈에는 `@SpringBootApplication` 클래스가 없다(그건 `bootstrap`에만 있음) — `@DataJpaTest`가 슬라이스 설정을 찾으려면 패키지 트리 위로 올라가며 `@SpringBootConfiguration`을 찾는데, `chore` 모듈 테스트 소스에는 없으므로 그대로 두면 컨텍스트 로딩에 실패한다. `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/TestApplication.kt`에 테스트 전용 `@SpringBootApplication` 클래스를 하나 추가해 해결한다(운영 코드 아님, 테스트 컨텍스트 부트스트랩용).

애그리거트별 최소 1개씩:
- `RoomRepositoryAdapterTest` — 저장한 `Room`+`RoomPlacement`를 다시 읽으면 값이 같고, 여러 방을 저장 후 `findAll()` 결과로 `FloorPlan.of(...)`이 성공하는지(경계값 위반 없이 왕복)
- `ChoreDefinitionRepositoryAdapterTest` — `Daily`/`WeeklyNTimes(n)`/`Monthly` 세 종류 모두 저장→로드 왕복, `howToSteps` 순서 보존 확인
- `ChoreInstanceRepositoryAdapterTest` — 미완료 인스턴스 왕복, **완료된 인스턴스를 저장 후 다시 읽었을 때 `domainEvents`가 비어 있는지**(재구성이 이벤트를 재발행하지 않는다는 게 이번 설계의 핵심 불변식이므로 반드시 검증)

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환 후 진행.
