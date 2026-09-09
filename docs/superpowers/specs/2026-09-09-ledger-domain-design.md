# Ledger(가계부) 도메인 모델 — 설계

> 2026-09-09 · sallim-master-spec.md 8장 구현순서 11번 "가계부 도메인" (6장 "상세 스펙 미작성 — 캘린더 구현 후 착수"의 착수 시점)
> calendar 도메인(10번)이 끝난 뒤 첫 착수 — ledger 모듈은 지금 빈 스캐폴딩뿐

## 범위

- `ledger` 바운디드 컨텍스트의 첫 서브프로젝트: `Transaction`(지출 기록) 도메인 모델(순수 Kotlin) + 단위 테스트만. 영속성/API는 다음 서브프로젝트들로 미룬다 — chore/calendar가 밟아온 것과 같은 순서
- 이번 거래 모델은 "누가 얼마를 냈다"는 **단순 지출 기록**만 다룬다

**이번 서브프로젝트에서 제외:**
- 분담 비율/정산 계산 — "누가 누구에게 얼마를 줘야 하는지"는 다음 서브프로젝트. `Transaction`은 지출 사실만 기록한다
- `Transaction` 영속성(JPA), REST API — 다음 서브프로젝트
- 정기 지출/구독(`RecurrencePolicy` 재사용) — chore가 `ChoreDefinition`→스케줄러를 나중 서브프로젝트로 미뤘던 것과 동일한 순서로, 단발성 거래 기록이 자리잡은 뒤 착수
- 소득(income) 기록 — 마스터 스펙이 명시한 건 "가구원별 지출 기록"뿐, 추측성 확장 배제
- 다중 통화 — 단일 통화(KRW) 가정
- 집안일 분담 통계와의 "공정성 시각화" 연결 — 정산/통계 서브프로젝트 이후

## 결정된 사항

- **`ledger`는 `MemberId`를 자체 복제한다.** chore/calendar가 이미 쓰는 패턴과 동일 — household의 진짜 `Member`를 직접 참조하지 않고, 컨텍스트 로컬 opaque UUID 타입으로 둔다.
- **금액은 `Long`(원 단위 정수)으로 둔다.** 단일 통화(KRW) 가정이고 원 단위는 소수점이 없으므로, `BigDecimal` 같은 정밀도 도구는 지금 요구사항에 과함(YAGNI) — 통화 다변화가 실제로 필요해지면 그때 바꾼다.
- **카테고리는 자유 문자열이다.** 마스터 스펙에 카테고리 목록 명시가 없어 `ChoreDefinition.label`처럼 값 검증 없는 자유 텍스트로 둔다 — 나중에 통계/필터링에 바로 쓸 수 있고 지금 비용은 거의 0.
- **지출 일시는 `LocalDateTime`까지 기록한다.** 하루 안에 여러 건 지출했을 때 순서를 구분할 수 있어야 한다는 요구로 확인됨.
- **`ledger/build.gradle.kts`에 `implementation(project(":common"))` 추가.** 지금 완전히 빈 파일이라 이번에 처음 채워진다. Kotlin 플러그인/Kotest는 루트 `build.gradle.kts`의 `subprojects {}` 블록이 모든 서브프로젝트에 이미 자동 적용하므로 별도 설정 불필요.

## 도메인 모델

### `sallim.ledger.domain` (신규)

```kotlin
// TransactionId.kt
class TransactionId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): TransactionId = TransactionId(UUID.randomUUID())
    }
}

// MemberId.kt — chore/calendar의 로컬 복제본과 동일 패턴
class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}

// Transaction.kt
class Transaction(
    val id: TransactionId,
    val memberId: MemberId,
    val amount: Long,
    val category: String,
    val memo: String?,
    val occurredAt: LocalDateTime
) {
    init {
        require(amount > 0) { "amount must be positive: $amount" }
        require(category.isNotBlank()) { "category must not be blank" }
    }
}
```

`ChoreDefinition`/`CalendarEvent`와 같은 스타일 — 일반 클래스 + `init` 검증, 계산 로직은 없음(단발성 사실 기록이라 `ChoreCompletionRecord`에 더 가까움).

## 테스트 전략

- `TransactionTest`: `amount <= 0`이면 `IllegalArgumentException`, `category` 공백이면 `IllegalArgumentException`, 정상 생성 시 필드값이 그대로 보존되는지 확인. 전부 Docker 불필요(순수 도메인 로직).

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
