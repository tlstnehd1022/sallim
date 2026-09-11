# Ledger(가계부) 영속성 + REST API — 설계

> 2026-09-11 · sallim-master-spec.md 8장 구현순서 11번 "가계부 도메인"의 이어지는 서브프로젝트
> 이전 서브프로젝트: `2026-09-09-ledger-domain-design.md` (`Transaction` 도메인 모델 완료, 영속성/API는 그때 범위에서 제외됨)

## 범위

- `Transaction`용 JPA 영속성 + REST API를 **한 서브프로젝트로** 구현 — `Transaction`은 단일 엔티티라 리뷰 단위가 크지 않음(calendar-persistence-api와 동일한 판단)
- 목록 조회 API는 기간(`from`/`to`, 필수) + 가구원(`memberId`, 선택) + 카테고리(`category`, 선택) 필터를 받는다
- CRUD(생성/수정/삭제)는 `Transaction` 단위 — calendar의 `CalendarEventController`와 동일한 패턴

**이번 서브프로젝트에서 제외:**
- 정산/분담 계산, "공정성 시각화" 통계 집계 — `ledger-domain` 설계에서 이미 범위 밖으로 결정됨. `Transaction`은 지출 사실만 기록/조회한다
- 정기 지출(구독, `RecurrencePolicy` 재사용) — 마찬가지로 `ledger-domain`에서 범위 밖으로 결정됨
- household 실제 Member로 `memberId` 검증 — chore/calendar와 동일한 패턴(Member 존재 검증 없이 로컬 opaque ID로 신뢰)
- 다중 통화, 소득(income) 기록 — `ledger-domain` 설계에서 이미 범위 밖

## 결정된 사항

- **영속성+API를 한 서브프로젝트로 묶는다.** `Transaction`은 단일 엔티티, 반복 로직도 없어 calendar보다도 단순.
- **`from`/`to`는 필수다.** chore-stats/calendar가 이미 확립한 컨벤션 — 클라이언트가 명시적 범위(`LocalDateTime`)를 보내고, `from > to`나 비현실적 연도는 `require`로 400 처리. "전체 조회"가 필요하면 클라이언트가 넓은 범위를 보낸다.
- **`memberId`/`category` 필터는 DB 쿼리가 아니라 애플리케이션 계층에서 인메모리로 거른다.** 가구 규모(2명 고정)와 거래 건수가 작은 규모에서 QueryDSL/`Specification` 같은 동적 쿼리 도구를 도입할 이유가 없다(YAGNI) — `findByOccurredAtBetween(from, to)` 하나만 DB에 위임하고, 나머지 필터는 서비스 계층에서 `.filter { }`로 처리한다.
- **`Transaction`은 `RecurrencePolicy` 변환이 필요 없다.** calendar/chore와 달리 반복이 없는 단발성 사실 기록이라 엔티티가 더 단순하다(컬럼 변환 로직 자체가 없음).
- **테이블명은 `transactions`(복수형)로 한다.** `transaction`은 MySQL 예약어에 근접해 혼동을 피하려는 `ledger-domain` 최종 리뷰의 권고를 반영.
- **Flyway 버전은 V7부터 시작한다.** 확인 결과 전체 모듈 공유 시퀀스의 현재 최고 버전은 calendar의 V6(`calendar/src/main/resources/db/migration/V6__create_calendar_event_table.sql`) — `bootstrap`이 `classpath:db/migration`을 전체 모듈 걸쳐 하나로 스캔하므로 반드시 그 다음 번호를 써야 한다(calendar가 이미 한 번 겪은 충돌).
- **`ApiExceptionHandler`는 처음부터 `LedgerApiExceptionHandler`로 이름 짓고 `basePackages` 스코프를 지정한다.** calendar-persistence-api 최종 리뷰에서 잡힌 실제 버그(같은 클래스 단순명 `ApiExceptionHandler`가 `chore`/`calendar` 사이에서 Spring 빈 이름 충돌 → `bootstrap` 부팅 실패)를 여기서는 재현하지 않기 위한 선제 조치.

## 영속성 계층

### `TransactionEntity` (신규, `sallim.ledger.infrastructure.persistence`)

```kotlin
@Entity
@Table(name = "transactions")
class TransactionEntity(
    @Id
    val id: String,
    val memberId: String,
    val amount: Long,
    val category: String,
    val memo: String?,
    val occurredAt: LocalDateTime
)
```

### `TransactionJpaRepository` (신규)

```kotlin
interface TransactionJpaRepository : JpaRepository<TransactionEntity, String> {
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<TransactionEntity>
}
```

### `TransactionRepository` (신규 도메인 포트, `sallim.ledger.domain`)

```kotlin
interface TransactionRepository {
    fun save(transaction: Transaction): Transaction
    fun findById(id: TransactionId): Transaction?
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction>
    fun deleteById(id: TransactionId)
}
```

### `TransactionRepositoryAdapter` (신규, `sallim.ledger.infrastructure.persistence`)

`RoomRepositoryAdapter`와 같은 구조(변환 로직 단순 — 컬럼 변환할 반복 규칙이 없어 chore/calendar 어댑터보다도 짧다).

### `V7__create_transactions_table.sql` (신규, `ledger/src/main/resources/db/migration`)

```sql
CREATE TABLE transactions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    member_id CHAR(36) NOT NULL,
    amount BIGINT NOT NULL,
    category VARCHAR(255) NOT NULL,
    memo TEXT,
    occurred_at DATETIME(6) NOT NULL
);
```

## 애플리케이션 계층 (`sallim.ledger.application`, 신규)

```kotlin
@Service
class TransactionService(private val repository: TransactionRepository) {
    @Transactional(readOnly = true)
    fun list(from: LocalDateTime, to: LocalDateTime, memberId: MemberId?, category: String?): List<Transaction> {
        require(!from.isAfter(to)) { "from must not be after to: $from > $to" }
        require(to.year < 9999) { "to must be a reasonable calendar year: $to" }
        return repository.findByOccurredAtBetween(from, to)
            .filter { memberId == null || it.memberId == memberId }
            .filter { category == null || it.category == category }
    }

    @Transactional
    fun create(memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime): Transaction =
        repository.save(Transaction(TransactionId.generate(), memberId, amount, category, memo, occurredAt))

    @Transactional
    fun update(
        id: TransactionId, memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime
    ): Transaction {
        repository.findById(id) ?: throw NotFoundException("transaction not found: $id")
        return repository.save(Transaction(id, memberId, amount, category, memo, occurredAt))
    }

    @Transactional
    fun delete(id: TransactionId) {
        repository.findById(id) ?: throw NotFoundException("transaction not found: $id")
        repository.deleteById(id)
    }
}
```

`from`/`to` 검증은 `ChoreStatsService`/`CalendarEventService`와 동일한 두 개의 `require` — 이미 확립된 컨벤션.

## API 계층 (`sallim.ledger.api`, 신규)

```kotlin
data class TransactionRequest(
    val memberId: UUID, val amount: Long, val category: String, val memo: String?, val occurredAt: LocalDateTime
)

data class TransactionResponse(
    val id: UUID, val memberId: UUID, val amount: Long, val category: String, val memo: String?, val occurredAt: LocalDateTime
)

@RestController
@RequestMapping("/api/transactions")
class TransactionController(private val service: TransactionService) {

    @GetMapping
    fun list(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: LocalDateTime,
        @RequestParam(required = false) memberId: UUID?,
        @RequestParam(required = false) category: String?
    ): List<TransactionResponse> =
        service.list(from, to, memberId?.let { MemberId(it) }, category).map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: TransactionRequest): TransactionResponse =
        service.create(MemberId(request.memberId), request.amount, request.category, request.memo, request.occurredAt)
            .toResponse()

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: TransactionRequest): TransactionResponse =
        service.update(
            TransactionId(id), MemberId(request.memberId), request.amount, request.category, request.memo, request.occurredAt
        ).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: UUID) {
        service.delete(TransactionId(id))
    }

    private fun Transaction.toResponse() =
        TransactionResponse(id.value, memberId.value, amount, category, memo, occurredAt)
}
```

### `LedgerApiExceptionHandler` (신규, `sallim.ledger.api`)

```kotlin
@RestControllerAdvice(basePackages = ["sallim.ledger"])
class LedgerApiExceptionHandler : ResponseEntityExceptionHandler() {
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

| Method | Path | 응답 |
|---|---|---|
| GET | `/api/transactions?from=2026-09-01T00:00:00&to=2026-09-30T23:59:59&memberId=&category=` | `200 [TransactionResponse]` / `400`(파라미터 누락, `from > to`) |
| POST | `/api/transactions` | `201 TransactionResponse` |
| PUT | `/api/transactions/{id}` | `200 TransactionResponse` / `404` |
| DELETE | `/api/transactions/{id}` | `204` / `404` |

## 테스트 전략

- `TransactionRepositoryAdapterTest`: `RoomRepositoryAdapterTest` 패턴(Testcontainers MySQL, `em.flush()`+`em.clear()`로 L1 캐시 우회) — 저장/조회, `findByOccurredAtBetween` 경계값(occurredAt == to, occurredAt == from). **Docker 필요.**
- `TransactionServiceTest`: 페이크 `TransactionRepository` 주입, `from > to`/비정상 연도 시 `IllegalArgumentException`, `memberId`/`category` 필터 조합, 존재하지 않는 id로 update/delete 시 `NotFoundException`. Docker 불필요.
- `TransactionControllerTest`: `@WebMvcTest` + 페이크 서비스, CRUD 왕복 + 필터 조합 조회 검증(200/201/204/404/400). Docker 불필요.

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
