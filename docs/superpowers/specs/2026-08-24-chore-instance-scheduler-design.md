# Chore(집안일) 반복 인스턴스 스케줄러 — 설계

> 2026-08-24 · sallim-master-spec.md 8장 구현순서 7번 "반복 인스턴스 생성 스케줄러 (자정 배치)"
> 이전 서브프로젝트: `2026-08-21-chore-api-design.md` (Room/ChoreDefinition/ChoreInstance REST API + bootstrap 실부팅 완료)

## 범위

- 매일 자정에 `RecurrencePolicy`에 따라 각 `ChoreDefinition`의 다음 `ChoreInstance`를 자동 생성하는 배치 추가
- `ChoreDefinition`을 새로 만들면 그 즉시 오늘 날짜 인스턴스를 하나 생성 (지금은 API로 정의를 만들어도 인스턴스가 하나도 안 생겨서 스케줄러 전까진 "오늘 할 일"에 영원히 안 뜸)
- 서버가 며칠 꺼져 있다가 다시 켜져도, 그 사이 놓친 날짜의 인스턴스를 소급 생성(catch-up)
- `chore_instance` 테이블에 (정의, 날짜) 유니크 제약 추가 — 중복 생성 방지

**이번 서브프로젝트에서 제외:**
- 갭 #1(마스터 스펙 9장) — 요일 지정 없는 `WeeklyNTimes`의 균등분배 근사치 부정확성. `RecurrencePolicy.nextOccurrence` 자체의 계산 로직은 건드리지 않고, 그 결과를 "언제 새 인스턴스를 만들지" 판단하는 데만 쓴다
- 완료 취소(uncomplete), household 실연동 — 이전 서브프로젝트와 동일하게 범위 밖
- 알림(이메일/카카오톡) — 인스턴스가 새로 생겼다고 알려주는 기능은 갭 #3으로 별도

## 결정된 사항

- **정의 생성 즉시 첫 인스턴스 생성.** `ChoreDefinitionService.create()`가 정의 저장 직후 오늘 날짜 인스턴스를 하나 만든다. 스케줄러는 "인스턴스가 하나도 없는 정의"를 부트스트랩하는 케이스를 따로 다루지 않는다 — 항상 "기존 인스턴스가 있고, 거기서부터 다음 회차를 계산" 하나의 경로만 갖는다.
- **소급 보정(catch-up)한다.** 정의의 최근 인스턴스부터 `nextOccurrence`를 반복 호출해 오늘까지의 모든 회차를 만든다. 마스터 스펙의 청결도 모델("먼지 = f(Σ 미완료 할 일마다 (1+지연일수×계수))")이 애초에 밀린 날짜가 누적되는 걸 전제로 하므로, 소급 없이 "오늘치만" 만들면 이 모델 자체가 의도대로 안 돈다.
- **유니크 제약으로 중복 방지.** `chore_instance(chore_definition_id, scheduled_date)`에 DB 유니크 제약을 건다 — 동시 실행이나 코드 버그로 같은 정의+날짜가 두 번 저장되는 걸 애플리케이션 코드가 아니라 DB가 막는다. 스케줄 메서드는 이 제약 위반을 따로 잡지 않는다 — Spring이 `@Scheduled` 메서드의 예외를 로그만 남기고 다음 실행엔 영향을 주지 않는다(스프링 기본 동작).
- **스케줄러는 `api` 패키지에 둔다.** REST 컨트롤러와 cron 트리거는 둘 다 "외부에서 application 계층을 호출하는 진입점"이라는 성격이 같다. CLAUDE.md의 4계층(domain/application/infrastructure/api)에 별도 "스케줄링" 계층을 새로 만들지 않고 기존 `api`에 얹는다.
- **핵심 로직은 서비스에, 스케줄러는 얇은 배선만.** 소급 보정 반복문은 `ChoreInstanceService.generateDueInstances(...)`에 두고, `ChoreInstanceScheduler`는 이 메서드를 부르는 `@Scheduled` 트리거 하나만 갖는다 — 테스트도 서비스 메서드 위주로, 스케줄러 자체는 배선만 확인.
- **주기 변경은 별도 처리 안 함.** `ChoreDefinition.update()`로 `recurrence`가 바뀌어도 스케줄러 쪽엔 특별한 로직이 필요 없다 — 다음 실행 때 "최근 인스턴스 + 현재(바뀐) 정책"으로 계산하니 자연스럽게 새 정책이 반영된다.

## `application` 계층 변경

### `ChoreDefinitionService.create()`

```kotlin
@Transactional
fun create(
    label: String, roomId: RoomId, assigneeId: MemberId,
    recurrence: RecurrencePolicy, howToSteps: List<String>, videoQuery: String
): ChoreDefinition {
    roomRepository.findById(roomId) ?: throw IllegalArgumentException("room not found: $roomId")
    val definition = choreDefinitionRepository.save(
        ChoreDefinition(ChoreDefinitionId.generate(), roomId, label, assigneeId, recurrence, howToSteps, videoQuery)
    )
    choreInstanceRepository.save(ChoreInstance.schedule(definition.id, LocalDate.now()))
    return definition
}
```
`choreInstanceRepository`는 이미 `delete()`에서 쓰고 있어 새 의존성 추가 없음.

### `ChoreInstanceService.generateDueInstances()` (신규)

```kotlin
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
```
`latest`가 없는 정의(이론상 `create()`가 항상 하나 만들어주므로 정상 동작 중엔 발생 안 함)는 이번 배치에서 건너뛴다 — 과거 시점부터 소급 생성하는 복구 로직은 추가하지 않는다(YAGNI, 발생 안 하는 상황을 위한 코드).

## `api` 계층 변경 (`sallim.chore.api`, 신규 파일 1개)

### `ChoreInstanceScheduler`

```kotlin
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

## `bootstrap` 변경

`SallimApplication.kt`에 `@EnableScheduling` 추가 — composition root이므로 여기서 활성화.

## Flyway 마이그레이션

`chore/src/main/resources/db/migration/V4__add_chore_instance_unique_constraint.sql`:
```sql
ALTER TABLE chore_instance
    ADD CONSTRAINT uq_chore_instance_definition_date UNIQUE (chore_definition_id, scheduled_date);
```

## 테스트 전략

- `ChoreDefinitionServiceTest`: `create()` 호출 시 정의 1개 + 인스턴스 1개(오늘 날짜)가 함께 저장되는지 — 기존 `FakeChoreDefinitionRepository`/`FakeChoreInstanceRepository` 재사용, Docker 불필요
- `ChoreInstanceServiceTest`: `generateDueInstances()` —
  - `Daily`: 최근 인스턴스가 3일 전이면 3개 생성
  - `WeeklyNTimes(n)`/`Monthly`: 각각 최소 1개 이상 생성되는 케이스
  - 이미 오늘 날짜까지 인스턴스가 있으면 0개 생성(멱등)
  - `latest`가 없는 정의는 결과에서 빠짐
  - 전부 페이크 리포지토리, Docker 불필요
- `ChoreInstanceSchedulerTest`: `generateDueInstances()` 호출 시 두 서비스가 올바른 인자(오늘 날짜, 전체 정의 목록)로 불리는지 배선만 확인
- `V4` 유니크 제약: 이제 Docker가 정상 동작하는 환경이므로, 기존 `ChoreInstanceRepositoryAdapterTest`에 "같은 정의+날짜로 두 번 저장 시 예외" 케이스를 추가해 실제 MySQL로 검증

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
