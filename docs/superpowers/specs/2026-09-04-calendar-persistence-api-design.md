# Calendar(캘린더) 영속성 + REST API — 설계

> 2026-09-04 · sallim-master-spec.md 8장 구현순서 10번 "캘린더 도메인"의 이어지는 서브프로젝트
> 이전 서브프로젝트: `2026-08-31-calendar-domain-design.md` (`CalendarEvent` 도메인 모델 + `occurrencesIn` 완료, 영속성/API는 그때 범위에서 제외됨)

## 범위

- `CalendarEvent`용 JPA 영속성 + REST API를 **한 서브프로젝트로** 구현. chore는 정의(ChoreDefinition)/인스턴스(ChoreInstance)가 분리돼 있어 영속성과 API를 chore-persistence/chore-api 두 서브프로젝트로 나눴지만, `CalendarEvent`는 정의+반복을 한 엔티티로 갖는 단순한 구조라 나눌 이유가 없다.
- 조회 API는 기간(`from`/`to`, `LocalDate`)을 받아 반복 규칙을 펼친 **개별 발생 목록**(eventId + occurredAt)을 반환 — `occurrencesIn`을 그대로 사용
- CRUD(생성/수정/삭제)는 이벤트 "정의" 단위 — chore의 `ChoreDefinitionController`와 동일한 패턴

**이번 서브프로젝트에서 제외:**
- 개별 회차 취소/수정 — `calendar-domain` 설계에서 이미 범위 밖으로 결정됨(반복 일정은 무한히 계속되는 것으로 취급)
- chore 인스턴스와의 서버측 통합 — 클라이언트가 `/api/chore-instances`와 `/api/calendar-events`를 각각 불러 화면에서 합침(이미 결정됨, CLAUDE.md의 컨텍스트 간 직접 참조 금지 원칙)
- household 실제 Member로 `memberId` 검증 — chore도 `assigneeId`를 검증하지 않는 것과 동일한 패턴(Room만 존재 검증, Member는 로컬 opaque ID로 신뢰)
- ledger 정기 결제일의 이벤트 구독 표시 — ledger 도메인 자체가 아직 없음

## 결정된 사항

- **영속성+API를 한 서브프로젝트로 묶는다.** `CalendarEvent`는 단일 엔티티라 리뷰 단위가 크지 않음.
- **조회 응답은 `eventId` + `occurredAt`으로 개별 발생을 식별한다.** 하나의 `CalendarEvent`(정의)가 반복으로 여러 발생을 낳으므로, 각 발생은 같은 `eventId`를 공유하고 `occurredAt`(`LocalDateTime`)만 다르다. 발생마다 별도 ID를 부여하는 것은 지금 요구사항(개별 회차 취소/수정 불가)에 없는 추측성 확장이라 배제.
- **`RecurrencePolicy`는 컬럼 2개(`recurrenceType`, `recurrenceTimes`)로 저장한다.** `ChoreDefinitionRepositoryAdapter`가 이미 쓰는 직렬화 방식을 그대로 재사용(로직 복제, import는 불가 — chore/calendar 컨텍스트 간 직접 참조 금지).
- **조회 시 DB에서 `startAt <= to`인 이벤트만 미리 거른다.** 조회 범위(`to`) 이후에 시작하는 이벤트는 `occurrencesIn`이 항상 빈 목록을 반환하므로, 파생 쿼리 한 줄로 불필요한 로딩을 막는다 — 그 이상의 DB 레벨 반복 계산 최적화는 지금 요구사항(가구 규모 2명, 이벤트 수 적음)에 과함.
- **`RecurrenceDto`는 calendar API 계층에 로컬로 복제한다.** chore api의 `RecurrenceDto`를 import하지 않음 — 컨텍스트 간 참조 금지 원칙 그대로 적용(계산 로직도 없는 순수 DTO라 복제 비용이 낮음).
- **Flyway 마이그레이션 경로는 별도 설정 불필요.** `bootstrap`에 `flyway.locations` 커스텀 설정이 없어 기본 `classpath:db/migration`으로 전체 모듈을 스캔한다 — `calendar/src/main/resources/db/migration/V1__...sql`만 추가하면 자동으로 잡힌다(확인 완료).

## 영속성 계층

### `CalendarEventEntity` (신규, `sallim.calendar.infrastructure.persistence`)

```kotlin
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

### `CalendarEventJpaRepository` (신규)

```kotlin
interface CalendarEventJpaRepository : JpaRepository<CalendarEventEntity, String> {
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEventEntity>
}
```

### `CalendarEventRepository` (신규 도메인 포트, `sallim.calendar.domain`)

```kotlin
interface CalendarEventRepository {
    fun save(event: CalendarEvent): CalendarEvent
    fun findById(id: CalendarEventId): CalendarEvent?
    fun findByStartAtLessThanEqual(to: LocalDateTime): List<CalendarEvent>
    fun deleteById(id: CalendarEventId)
}
```

메서드 이름은 JPA 파생 쿼리(`findByStartAtLessThanEqual`)와 동일하게 맞춘다 — "Before"는 보통 배타적 상한을 뜻해 `to` 포함 여부가 헷갈릴 수 있어 피한다.

### `CalendarEventRepositoryAdapter` (신규, `sallim.calendar.infrastructure.persistence`)

`ChoreDefinitionRepositoryAdapter`와 동일한 구조 — `RecurrencePolicy` ↔ 컬럼 변환 로직을 그대로 복제(로직 변경 없음, import 대상만 `sallim.common.domain.*`).

### `V1__create_calendar_event_table.sql` (신규, `calendar/src/main/resources/db/migration`)

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

## 애플리케이션 계층 (`sallim.calendar.application`, 신규)

```kotlin
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
    fun create(title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?): CalendarEvent =
        repository.save(CalendarEvent(CalendarEventId.generate(), title, startAt, memberId, memo, recurrence))

    @Transactional
    fun update(id: CalendarEventId, title: String, startAt: LocalDateTime, memberId: MemberId, memo: String?, recurrence: RecurrencePolicy?): CalendarEvent {
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

`from`/`to` 검증은 `ChoreStatsService`와 동일한 두 개의 `require` — 이미 확립된 컨벤션.

## API 계층 (`sallim.calendar.api`, 신규)

```kotlin
data class RecurrenceDto(val type: String, val times: Int?)

data class CalendarEventRequest(
    val title: String, val startAt: LocalDateTime, val memberId: UUID,
    val memo: String?, val recurrence: RecurrenceDto?
)

data class CalendarEventResponse(
    val id: UUID, val title: String, val startAt: LocalDateTime, val memberId: UUID,
    val memo: String?, val recurrence: RecurrenceDto?
)

data class CalendarEventOccurrenceResponse(
    val eventId: UUID, val title: String, val occurredAt: LocalDateTime,
    val memberId: UUID, val memo: String?
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
        service.create(request.title, request.startAt, MemberId(request.memberId), request.memo, request.recurrence?.toDomain())
            .toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: CalendarEventRequest): CalendarEventResponse =
        service.update(CalendarEventId(id), request.title, request.startAt, MemberId(request.memberId), request.memo, request.recurrence?.toDomain())
            .toResponse()

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

`NotFoundException`/`ApiExceptionHandler`는 chore에 이미 있는 것과 동일한 필요 — calendar 모듈에 동일한 얇은 복제본을 둔다(컨텍스트 간 참조 금지 원칙, chore의 `MemberId` 로컬 복제와 같은 이유).

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/calendar-events?from=2026-09-01&to=2026-09-30` | `200 [{eventId, title, occurredAt, memberId, memo}]` / `400`(파라미터 누락 또는 `from > to`) |
| POST | `/api/calendar-events` | `201 CalendarEventResponse` |
| PUT | `/api/calendar-events/{id}` | `200 CalendarEventResponse` / `404` |
| DELETE | `/api/calendar-events/{id}` | `204` / `404` |

## 테스트 전략

- `CalendarEventRepositoryAdapterTest`: chore의 `RoomRepositoryAdapterTest` 패턴(Testcontainers MySQL, `saveAndFlush`) — 저장/조회, `findByStartAtLessThanEqual` 경계값(startAt == to, startAt > to). **Docker 필요.**
- `CalendarEventServiceTest`: 페이크 `CalendarEventRepository` 주입, `from > to`/비정상 연도 시 `IllegalArgumentException`, 존재하지 않는 id로 update/delete 시 `NotFoundException`, 반복 이벤트가 여러 발생으로 펼쳐지는지. Docker 불필요.
- `CalendarEventControllerTest`: `@WebMvcTest` + 페이크 서비스, CRUD 왕복 + 조회 응답 검증(200/201/204/404/400). Docker 불필요.

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
