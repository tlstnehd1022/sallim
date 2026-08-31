# Calendar(캘린더) 도메인 모델 — 설계

> 2026-08-31 · sallim-master-spec.md 8장 구현순서 10번 "캘린더 도메인" (5장 "상세 스펙 미작성 — 집안일 구현 후 착수"의 착수 시점)
> chore 도메인(4~9번)이 모두 끝난 뒤 첫 착수 — calendar 모듈은 지금 빈 스캐폴딩뿐

## 범위

- `calendar` 바운디드 컨텍스트의 첫 서브프로젝트: `CalendarEvent` 도메인 모델(순수 Kotlin) + 단위 테스트만. 영속성/API/스케줄러는 다음 서브프로젝트들로 미룬다 — chore가 4→5→6→7→8→9로 쌓아온 것과 같은 순서
- `RecurrencePolicy`(현재 `sallim.chore.domain`에 있음)를 `sallim.common.domain`으로 이전 — chore/calendar가 같은 반복 규칙을 공유(마스터 스펙 9장 갭 #1이 이미 이 방향을 암시)

**이번 서브프로젝트에서 제외:**
- `CalendarEvent` 영속성(JPA), REST API — 다음 서브프로젝트
- chore 인스턴스와의 화면 결합 — **서버가 하지 않는다.** 클라이언트가 `/api/chore-instances`와 (나중에 생길) `/api/calendar-events`를 각각 불러 화면에서 합친다. CLAUDE.md의 "컨텍스트 간 직접 참조 금지"를 가장 깔끔하게 지키는 방법이고, 지금 단계에서 서버 쪽 통합 로직이 전혀 필요 없다
- ledger의 정기 결제일 "이벤트 구독" 표시 — ledger 도메인 자체가 아직 없음(마스터 스펙 8장 11번, 캘린더 이후)
- 반복 일정의 종료일(until), 개별 회차 취소/수정 — 지금 요구사항에 없음. 반복 일정은 조회 범위 안에서 무한히 계속되는 것으로 취급
- 다중 참석자 — chore의 `assigneeId`처럼 단일 `memberId`만

## 결정된 사항

- **`RecurrencePolicy`를 `sallim.common.domain`으로 이전.** `Identifier`/`AggregateRoot`/`DomainEvent`처럼 이미 컨텍스트에 안 묶인 순수 개념들이 있는 자리이고, `RecurrencePolicy`도 Room/ChoreDefinition 같은 chore 전용 타입을 참조하지 않는 순수 시간 개념이라 자리가 맞는다. **실제 영향 범위 확인 결과 chore 안에서 14개 파일이 `RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`를 참조** — 전부 `sallim.chore.domain`과 같은 패키지라 지금까지 별도 import 없이 썼던 것들. 이전 후 그 14개 파일 전부에 `import sallim.common.domain.*` 추가 필요(로직 변경 없음, 컴파일러가 빠짐없이 잡아줌).
- **`CalendarEvent` 하나로 정의+반복을 같이 갖는다.** chore의 `ChoreDefinition`/`ChoreInstance` 분리는 "완료 여부"라는 회차별 상태를 저장해야 해서 필요했다. 캘린더 일정은 그런 상태가 없으므로(취소/개별 수정 요구사항 없음), 별도 인스턴스 테이블이나 스케줄러 없이 조회 시점에 `RecurrencePolicy.nextOccurrence`로 즉석 계산한다.
- **`calendar`는 `MemberId`를 자체 복제한다.** chore가 이미 쓰는 패턴과 동일 — household의 진짜 `Member`를 직접 참조하지 않고, 컨텍스트 로컬 opaque UUID 타입으로 둔다.
- **`calendar/build.gradle.kts`에 `implementation(project(":common"))` 추가.** 지금 완전히 빈 파일이라 이번에 처음 채워진다. Kotlin 플러그인/Kotest는 루트 `build.gradle.kts`의 `subprojects {}` 블록이 모든 서브프로젝트에 이미 자동 적용하므로 별도 설정 불필요.

## 도메인 모델

### `sallim.common.domain.RecurrencePolicy` (이전, 로직 변경 없음)

```kotlin
package sallim.common.domain

import java.time.LocalDate

sealed interface RecurrencePolicy {
    fun nextOccurrence(after: LocalDate): LocalDate
}

data object Daily : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusDays(1)
}

data class WeeklyNTimes(val times: Int) : RecurrencePolicy {
    init {
        require(times in 1..7) { "times must be within 1..7: $times" }
    }

    override fun nextOccurrence(after: LocalDate): LocalDate =
        after.plusDays((7 / times).toLong())
}

data object Monthly : RecurrencePolicy {
    override fun nextOccurrence(after: LocalDate): LocalDate = after.plusMonths(1)
}
```
기존 `ponytail:` 주석(요일 미지원 근사치 한계)도 그대로 옮긴다 — 로직도, 알려진 한계도 변경 없음.

### `sallim.calendar.domain` (신규)

```kotlin
// CalendarEventId.kt
class CalendarEventId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): CalendarEventId = CalendarEventId(UUID.randomUUID())
    }
}

// MemberId.kt — chore의 로컬 복제본과 동일 패턴
class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}

// CalendarEvent.kt
class CalendarEvent(
    val id: CalendarEventId,
    val title: String,
    val startAt: LocalDateTime,
    val memberId: MemberId,
    val memo: String?,
    val recurrence: RecurrencePolicy?  // null = 단일 일정
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
    }

    fun occurrencesIn(from: LocalDate, to: LocalDate): List<LocalDateTime> {
        if (recurrence == null) {
            val date = startAt.toLocalDate()
            return if (date in from..to) listOf(startAt) else emptyList()
        }
        val result = mutableListOf<LocalDateTime>()
        var date = startAt.toLocalDate()
        while (!date.isAfter(to)) {
            if (!date.isBefore(from)) result += date.atTime(startAt.toLocalTime())
            date = recurrence.nextOccurrence(date)
        }
        return result
    }
}
```

`occurrencesIn`이 이 서브프로젝트의 핵심 로직 — 스케줄러 없이 조회 범위 안에서만 반복을 펼쳐 날짜+시간 목록을 반환한다. 시작일이 조회 범위보다 늦으면 빈 목록, 단일 일정은 범위 안에 있을 때만 자기 자신 하나를 반환.

## 테스트 전략

- `RecurrencePolicyTest`: `common` 모듈로 그대로 이전(기존 7개 테스트, 로직 변경 없으니 내용도 변경 없음)
- `CalendarEventTest`: 제목 공백 검증, `occurrencesIn` — 단일 일정(범위 안/밖), Daily/WeeklyNTimes/Monthly 각각 범위 안에서 여러 회 나오는지, 시작일이 범위보다 늦으면 빈 목록. 전부 Docker 불필요(순수 도메인 로직).

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
