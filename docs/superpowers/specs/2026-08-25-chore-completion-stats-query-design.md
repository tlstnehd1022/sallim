# Chore(집안일) 완료 통계 조회(CQRS) — 설계

> 2026-08-25 · sallim-master-spec.md 8장 구현순서 9번 "통계 조회 분리 (CQRS)"
> 이전 서브프로젝트: `2026-08-24-chore-completion-events-design.md` (완료 이벤트 → Kafka → `chore_completion_record` fact 테이블 적재 완료)

## 범위

- `chore_completion_record`(이전 서브프로젝트가 채워온 fact 테이블) 위에 조회 전용 REST API 하나 추가: 기간을 주면 멤버별 완료 개수를 반환
- 커맨드 측(`ChoreCompletionRecordRepository`, Kafka 소비자가 씀)과 물리적으로 같은 테이블을 보되, 도메인 포트는 별도로 분리 — 이게 이번 서브프로젝트가 보여주는 CQRS 분리
- 마스터 스펙 2장 "기록(통계)" 화면(멤버별 완료 개수, "공정성 시각화")에 대응

**이번 서브프로젝트에서 제외:**
- 방(Room)별 통계, 집안일 정의(ChoreDefinition)별 통계 — 마스터 스펙이 명시한 건 멤버별 공정성 지표뿐, 나머지는 추측성 확장
- "이번 주"/"이번 달" 같은 프리셋 기간 계산 — 서버는 클라이언트가 보낸 명시적 `from`/`to`만 받는다(아래 "결정된 사항" 참고)
- 가계부(ledger)의 "동일한 공정성 시각화 컨셉" 재사용 — ledger 도메인 자체가 아직 없음(마스터 스펙 8장 11번, 캘린더 이후)

## 결정된 사항

- **커맨드/쿼리 포트를 분리한다.** 기존 `ChoreCompletionRecordRepository`(쓰기 전용, Kafka 소비자만 사용)는 그대로 두고, 새 `ChoreCompletionStatsQuery` 포트를 추가해 통계 API만 이걸 쓴다. 물리적으로는 같은 `chore_completion_record` 테이블/같은 Spring Data JPA 인터페이스(`ChoreCompletionRecordJpaRepository`)를 재사용하지만, 도메인 포트와 어댑터는 커맨드용/쿼리용으로 나눈다 — CQRS는 별도 물리 저장소를 요구하지 않고 별도 모델/인터페이스면 충분하다.
- **기간은 클라이언트가 `from`/`to`를 명시적으로 보낸다.** "이번 주"/"이번 달"을 서버가 enum으로 해석하면 "주의 시작이 월요일이냐 일요일이냐" 같은 모호함이 생긴다. 클라이언트가 계산한 날짜 범위를 그대로 받는 얇은 API로 간다.
- **집계 쿼리는 GROUP BY 하나로.** 멤버 수가 2명 고정(마스터 스펙 9장 갭 #4)인 household 규모에서 N+1 카운트 쿼리를 돌릴 이유가 없다 — `SELECT completedBy, COUNT(*) ... GROUP BY completedBy` 하나로 끝낸다.
- **날짜 → Instant 경계는 Asia/Seoul 고정.** 스케줄러/API 서브프로젝트에서 이미 확립한 컨벤션 그대로. `to` 날짜는 그 날 전체(00:00~다음날 00:00 직전)를 포함한다.
- **`from > to`면 400.** 기존 `ApiExceptionHandler`가 `IllegalArgumentException` → 400으로 이미 매핑하므로, 서비스 계층에서 `require`로 검증.

## 읽기 포트 (`sallim.chore.domain`, 신규)

```kotlin
interface ChoreCompletionStatsQuery {
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount>
}

data class MemberCompletionCount(val memberId: MemberId, val count: Long)
```

## 인프라 레이어 변경

### `ChoreCompletionRecordJpaRepository`에 GROUP BY 쿼리 추가 (기존 파일 수정)

```kotlin
interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String> {
    fun existsByChoreInstanceId(choreInstanceId: String): Boolean

    @Query("SELECT r.completedBy AS memberId, COUNT(r) AS count FROM ChoreCompletionRecordEntity r WHERE r.completedAt >= :fromInclusive AND r.completedAt < :toExclusive GROUP BY r.completedBy")
    fun countByMemberCompletedAtInRange(fromInclusive: Instant, toExclusive: Instant): List<MemberCountProjection>
}

interface MemberCountProjection {
    val memberId: String
    val count: Long
}
```

### `JpaChoreCompletionStatsQuery` (신규, `sallim.chore.infrastructure.persistence`)

```kotlin
@Repository
class JpaChoreCompletionStatsQuery(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionStatsQuery {
    override fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        val zone = ZoneId.of("Asia/Seoul")
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = to.plusDays(1).atStartOfDay(zone).toInstant()
        return jpaRepository.countByMemberCompletedAtInRange(fromInstant, toInstant)
            .map { MemberCompletionCount(MemberId(UUID.fromString(it.memberId)), it.count) }
    }
}
```

## `application` 계층 (`sallim.chore.application`, 신규)

```kotlin
@Service
class ChoreStatsService(private val query: ChoreCompletionStatsQuery) {
    @Transactional(readOnly = true)
    fun countByMember(from: LocalDate, to: LocalDate): List<MemberCompletionCount> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        return query.countByMember(from, to)
    }
}
```

## `api` 계층 (`sallim.chore.api`, 신규)

기존 `ChoreInstanceController`와 같은 스타일 — 응답 DTO는 도메인 ID 래퍼가 아니라 원시 `UUID`를 쓴다.

```kotlin
data class MemberCompletionCountResponse(val memberId: UUID, val count: Long)

@RestController
@RequestMapping("/api/chore-stats")
class ChoreStatsController(private val statsService: ChoreStatsService) {
    @GetMapping
    fun countByMember(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate
    ): List<MemberCompletionCountResponse> =
        statsService.countByMember(from, to).map { MemberCompletionCountResponse(it.memberId.value, it.count) }
}
```

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/chore-stats?from=2026-08-01&to=2026-08-24` | `200 [{memberId, count}]` / `400`(파라미터 누락 또는 `from > to`) |

`from`/`to` 파라미터 누락은 Spring의 `MissingServletRequestParameterException`을 기존 `ApiExceptionHandler`의 `handleExceptionInternal` 오버라이드가 이미 `{"error": ...}` 400으로 매핑하므로 별도 처리 불필요.

완료 기록이 없는 멤버는 응답 배열에 아예 나타나지 않는다(`count=0`으로 채워 반환하지 않음) — GROUP BY 집계의 자연스러운 결과이며, 클라이언트가 멤버 목록과 이 응답을 직접 매핑할 때 유의해야 한다.

## 테스트 전략

- `JpaChoreCompletionStatsQueryTest`: 범위 안/밖 레코드가 정확히 갈리는지, 여러 멤버가 섞여 있을 때 멤버별로 정확히 집계되는지 — `RoomRepositoryAdapterTest` 등과 같은 Testcontainers-MySQL 패턴. **Docker 필요.**
- `ChoreStatsServiceTest`: 페이크 `ChoreCompletionStatsQuery` 주입, `from > to` 시 `IllegalArgumentException` 검증. Docker 불필요.
- `ChoreStatsControllerTest`: `@WebMvcTest` + 페이크 서비스, `MockMvc`로 HTTP 왕복 검증(200/400). Docker 불필요.

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
