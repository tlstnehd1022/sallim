# Chore(집안일) 도메인 모델 — 설계

> 2026-08-19 · sallim-master-spec.md 16장 "즉시 착수" 마지막 항목 (집안일 도메인 모델 + 시드 데이터)
> 이전 서브프로젝트: `2026-08-14-foundation-scaffolding-design.md` (Gradle 스캐폴딩 + Household/Member 완료)

## 범위

- `chore` 모듈에 Chore(집안일) 도메인 모델 구현 (스펙 4.3장 표 그대로)
- 시드 데이터 포팅 (모바일 `seedRooms.ts`의 `DEFAULT_ROOMS` → Kotlin)
- 단위 테스트

**이번 서브프로젝트에서 제외** (다음 서브프로젝트로):
- JPA 영속성 구현체
- REST API 레이어
- 자정 배치 스케줄러 (반복 인스턴스 생성)
- 도메인 이벤트 → Kafka 발행
- CQRS 통계 조회
- household ↔ chore 실제 연동 배선 (애플리케이션 레이어에서 진짜 `MemberId` 주입)

## 결정된 사항

- 담당자(assignee): `chore` 모듈이 자체 `MemberId` 타입(household의 `MemberId`와 같은 UUID 값을 가리키지만 모듈 의존은 없음)을 정의해 참조한다. 모바일 목업의 `'나'|'짝꿍'` 문자열 고정 대신, 실제 가구원 확장(갭#4)에 대응 가능한 모델.
- 반복 규칙(RecurrencePolicy) 범위: 이번엔 모바일 목업과 동일한 기본 케이스만 (매일/주n회[주1회 포함]/매달). "매달 몇째 주 무슨 요일" 같은 세부 확장(갭#1)은 이번 범위 밖 — 별도 서브프로젝트에서.

## 모듈 의존성

`chore/build.gradle.kts` → `common`만 참조. `household`는 참조하지 않는다 (컨텍스트 간 직접 참조 금지, CLAUDE.md).

## 도메인 모델 (`sallim.chore.domain`)

| 타입 | 역할 |
|---|---|
| `RoomId`, `Room` | 엔티티: `id`, `name`만 — 좌표 없음 (스펙 4.3 설계 원칙) |
| `RoomPlacement`(roomId, x, y, w, h, z) | 값객체: 방 하나의 배치 |
| `FloorPlan` | 값객체: `RoomPlacement` 목록. `FloorPlan.of(placements)`가 스펙 4.1 규칙 검증 — `w ∈ [8, 100-x]`, `h ∈ [6, 100-y]` |
| `MemberId` | chore 모듈 자체 정의 UUID 참조 타입 (household의 것과 값은 같을 수 있으나 타입/모듈 의존은 독립) |
| `ChoreDefinitionId`, `ChoreDefinition` | 엔티티: `label`, `roomId`, `assigneeId: MemberId`, `recurrence: RecurrencePolicy`, `howToSteps: List<String>`, `videoQuery: String` |
| `ChoreInstanceId`, `ChoreInstance` | `AggregateRoot<ChoreInstanceId>`: `choreDefinitionId`, `scheduledDate`, `completed`, `completedBy: MemberId?`, `completedAt: Instant?`. `complete(memberId: MemberId)` → `ChoreCompletedEvent` 발행, 이미 완료된 경우 재완료 불가 |
| `RecurrencePolicy` | 전략 인터페이스: `nextOccurrence(after: LocalDate): LocalDate`. 구현체 3종: `Daily`, `WeeklyNTimes(times: Int)`(주1회=`times=1`), `Monthly` |
| `CleanlinessScore` | 값객체: `compute(instances: List<ChoreInstance>, referenceDate: LocalDate): Double` — 미완료 + `scheduledDate <= referenceDate`인 인스턴스만 `Σ(1 + 지연일수 × 0.15)` (모바일 `mobile/src/domain/cleanliness.ts`의 `DELAY_COEFFICIENT`와 동일 상수, 공식 일치) |
| `ChoreCompletedEvent` | 도메인 이벤트: `choreInstanceId`, `choreDefinitionId`, `completedBy: MemberId`, `occurredAt: Instant` |

`WeeklyNTimes(n).nextOccurrence`는 `7/n`일 균등 간격으로 계산하는 단순화 — 특정 요일 지정 불가. `ponytail:` 주석으로 한계와 업그레이드 지점(갭#1)을 코드에 남긴다.

## 시드 데이터

`DefaultRooms` (chore 모듈 내 팩토리 객체) — `mobile/src/domain/seedRooms.ts`의 `DEFAULT_ROOMS`를 그대로 Kotlin `Room`/`FloorPlan`/`ChoreDefinition` 객체로 포팅. 방 10개, 할 일(ChoreDefinition) 18개, 한국어 라벨·방법 3단계·유튜브 검색어 그대로 유지.

## 테스트

JUnit5 + Kotest.
- `FloorPlan.of()` 경계값 검증 (범위 밖 좌표 거부)
- `RecurrencePolicy` 3종 `nextOccurrence` 케이스
- `CleanlinessScore.compute()` — 모바일 `computeCleanliness()`와 동일한 입력에 동일한 결과가 나오는지 교차검증 케이스 포함
- `ChoreInstance.complete()` → `ChoreCompletedEvent` 발행 확인, 재완료 방지
- `DefaultRooms` — 방 10개, ChoreDefinition 18개 카운트 검증

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환 후 진행.
