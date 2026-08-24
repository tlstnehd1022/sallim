# Chore(집안일) 완료 이벤트 → Kafka → 통계 원본 저장 — 설계

> 2026-08-24 · sallim-master-spec.md 8장 구현순서 8번 "도메인 이벤트 → Kafka → 통계 갱신 (EDA)"
> 이전 서브프로젝트: `2026-08-24-chore-instance-scheduler-design.md` (반복 인스턴스 자동 생성 완료)
> 다음 서브프로젝트: 마스터 스펙 8장 9번 "통계 조회 분리 (CQRS)" — 이번 서브프로젝트가 쌓아둔 fact를 실제로 집계/조회하는 API는 거기서

## 범위

- `ChoreInstance.complete()`가 이미 만들고 있는 `ChoreCompletedEvent`(현재 아그리게이트 내부에만 쌓이고 아무도 안 꺼냄)를 실제로 Kafka에 발행
- Kafka에서 그 이벤트를 소비해 `chore_completion_record`(완료 fact 원본 테이블)에 적재
- 이번 서브프로젝트는 "이벤트가 실제로 끝까지 흘러가서 fact로 남는다"까지만 — 멤버별/기간별 집계·조회 API는 범위 밖(다음 서브프로젝트)

**이번 서브프로젝트에서 제외:**
- 통계 집계/조회 API(마스터 스펙 8장 9번, CQRS) — fact 테이블만 있고 조회 쪽은 없음
- 트랜잭션 아웃박스 패턴 — 이 앱 규모(2인용)에서 이벤트 유실은 "통계 카운트 하나 덜 잡히는" 수준이라 감수 가능한 리스크로 판단, `@TransactionalEventListener(AFTER_COMMIT)` + `KafkaTemplate`로 충분
- 알림(이메일/카카오톡) — 이벤트 유실이 치명적인 기능이 생기면 그때 아웃박스 재검토
- 로컬 Kafka 브로커 기동 방법(docker-compose 등) 상세 — 구현 계획에서 정리

## 결정된 사항

- **이벤트 발행: `@TransactionalEventListener(AFTER_COMMIT)` + `KafkaTemplate`.** DB 커밋이 실제로 성공한 뒤에만 Kafka로 발행 — 아웃박스 테이블/폴러 없이 단순하게. `ChoreCompletedEvent`는 Spring 4.2+ 규칙대로 별도 래퍼 없이 POJO 그대로 발행.
- **포트/어댑터 패턴 유지.** `ChoreEventProducer`(도메인 포트) ↔ `KafkaChoreEventProducer`(실제 Kafka 어댑터), `ChoreCompletionRecordRepository`(도메인 포트) ↔ `JpaChoreCompletionRecordRepository`(JPA 어댑터) — Room/ChoreDefinition/ChoreInstance 리포지토리와 같은 이유(Docker 없이도 핵심 로직을 페이크로 단위 테스트 가능).
- **완료 기록은 원본 fact만, 집계 안 함.** `chore_completion_record`는 애그리거트가 아니라 도메인 로직 없는 순수 기록 — 별도 도메인 클래스 없이 리포지토리 포트만 둔다. 조회 요구사항이 아직 안 나왔는데 미리 집계 테이블을 설계하면 YAGNI 위반이고, fact만 있으면 나중에 어떤 집계든 다시 만들 수 있다.
- **멱등성은 DB 유니크 제약으로.** Kafka는 최소 한 번(at-least-once) 전달이라 재처리가 있을 수 있음 — `chore_completion_record.chore_instance_id`에 유니크 제약을 걸고, 어댑터가 중복 삽입 시 `DataIntegrityViolationException`을 잡아 로그만 남기고 무시. 스케줄러 서브프로젝트의 `chore_instance` 유니크 제약과 같은 패턴.
- **`saveAndFlush`로 예외 번역 보장.** 스케줄러 서브프로젝트 최종 리뷰에서 확인한 것과 같은 이유 — 리포지토리 프록시 안에서 flush해야 Spring Data의 `PersistenceExceptionTranslationInterceptor`가 적용돼 `DataIntegrityViolationException`으로 정확히 번역된다. 어댑터가 `save()` 대신 `saveAndFlush()`를 직접 호출.
- **토픽명 `chore.completed`, 키는 `choreInstanceId`.** 같은 인스턴스 관련 메시지 순서 보장 용도(지금은 완료 이벤트 하나뿐이라 실효는 작지만 관례상 고정).
- **컨슈머는 같은 `chore` 모듈 안에 둔다.** 완료 기록은 chore 컨텍스트 소유 데이터이고, ledger가 나중에 같은 "공정성 시각화" 컨셉을 재사용하고 싶어지면 그때 공유 여부를 판단 — 지금 미리 `common`으로 빼는 건 추측성 확장.

## 이벤트 발행 (`sallim.chore.application`, `sallim.chore.infrastructure.messaging`)

### `ChoreInstanceService.complete()` 수정

```kotlin
@Service
class ChoreInstanceService(
    private val choreInstanceRepository: ChoreInstanceRepository,
    private val eventPublisher: ApplicationEventPublisher
) {
    @Transactional
    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        val saved = choreInstanceRepository.save(instance)
        instance.domainEvents.forEach { eventPublisher.publishEvent(it) }
        instance.clearEvents()
        return saved
    }
}
```
`generateDueInstances`/`listByDate`는 변경 없음 — 생성자에 `eventPublisher` 파라미터만 추가되므로 그 두 메서드를 호출하는 기존 테스트들은 페이크 `ApplicationEventPublisher`를 새로 주입해야 한다(로직 자체는 무관).

### Kafka 발행 포트/어댑터 (`sallim.chore.infrastructure.messaging`, 신규)

```kotlin
// domain — 포트
interface ChoreEventProducer {
    fun publish(event: ChoreCompletedEvent)
}
```

```kotlin
// infrastructure.messaging — 트랜잭션 커밋 후 발행 트리거
@Component
class TransactionalChoreEventPublisher(private val producer: ChoreEventProducer) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChoreCompleted(event: ChoreCompletedEvent) {
        producer.publish(event)
    }
}
```

```kotlin
// infrastructure.messaging — 실제 Kafka 어댑터
@Component
class KafkaChoreEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, ChoreCompletedEvent>
) : ChoreEventProducer {
    override fun publish(event: ChoreCompletedEvent) {
        kafkaTemplate.send("chore.completed", event.choreInstanceId.value.toString(), event)
    }
}
```

## 완료 기록 소비/저장 (`sallim.chore.domain`, `sallim.chore.infrastructure.messaging`, `sallim.chore.infrastructure.persistence`)

### 리포지토리 포트

```kotlin
// domain — 포트, 도메인 로직 없는 순수 기록이라 애그리거트 클래스 없이 리포지토리만
interface ChoreCompletionRecordRepository {
    fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant)
}
```

### Kafka 소비자

```kotlin
// infrastructure.messaging
@Component
class ChoreCompletedEventConsumer(private val repository: ChoreCompletionRecordRepository) {
    @KafkaListener(topics = ["chore.completed"], groupId = "chore-stats")
    fun onMessage(event: ChoreCompletedEvent) {
        repository.save(event.choreInstanceId, event.choreDefinitionId, event.completedBy, event.occurredAt)
    }
}
```

### JPA 엔티티 + 어댑터

```kotlin
// infrastructure.persistence
@Entity
@Table(name = "chore_completion_record")
class ChoreCompletionRecordEntity(
    @Id
    val id: String,
    val choreInstanceId: String,
    val choreDefinitionId: String,
    val completedBy: String,
    val completedAt: Instant
)

interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String>
```

```kotlin
// infrastructure.persistence — 어댑터, 멱등성은 유니크 제약 위반을 잡아서 무시
@Repository
class JpaChoreCompletionRecordRepository(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionRecordRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) {
        try {
            jpaRepository.saveAndFlush(
                ChoreCompletionRecordEntity(
                    id = UUID.randomUUID().toString(),
                    choreInstanceId = choreInstanceId.value.toString(),
                    choreDefinitionId = choreDefinitionId.value.toString(),
                    completedBy = completedBy.value.toString(),
                    completedAt = completedAt
                )
            )
        } catch (e: DataIntegrityViolationException) {
            logger.info("chore completion record already exists for instance {}, skipping (Kafka redelivery)", choreInstanceId)
        }
    }
}
```

### Flyway 마이그레이션

`chore/src/main/resources/db/migration/V5__create_chore_completion_record_table.sql`:
```sql
CREATE TABLE chore_completion_record (
    id CHAR(36) NOT NULL,
    chore_instance_id CHAR(36) NOT NULL,
    chore_definition_id CHAR(36) NOT NULL,
    completed_by CHAR(36) NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (chore_instance_id)
);
```

## 빌드/설정 배선

`gradle/libs.versions.toml` 추가 (버전 없이 BOM 해석, `testcontainers-kafka`는 기존 `testcontainers` 버전 참조 재사용 — 로컬 Docker 신버전과의 클라이언트 버전 충돌 방지를 위해 이미 1.21.4로 명시 고정돼 있음):
```toml
spring-kafka = { module = "org.springframework.kafka:spring-kafka" }
testcontainers-kafka = { module = "org.testcontainers:kafka", version.ref = "testcontainers" }
```

`chore/build.gradle.kts` 추가:
```kotlin
implementation(libs.spring.kafka)
testImplementation(libs.testcontainers.kafka)
```

`bootstrap/src/main/resources/application.yml` 추가 (로컬 개발 기준 placeholder):
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```
실제 로컬 Kafka 기동 방법(docker-compose 또는 `docker run`)은 구현 계획에서 정리.

## 테스트 전략

- `ChoreInstanceServiceTest`: 생성자에 추가된 `ApplicationEventPublisher`는 람다 기반 페이크로 대체(Spring 인터페이스라 목킹 라이브러리 불필요) — `complete()` 호출 시 `ChoreCompletedEvent`가 정확히 발행되는지 검증. Docker 불필요.
- `TransactionalChoreEventPublisherTest`: `FakeChoreEventProducer` 주입, `onChoreCompleted()` 직접 호출해 `producer.publish()`가 불리는지 확인 — `AFTER_COMMIT` 타이밍 자체(실제 트랜잭션 커밋 이후에만 발행됨)는 이 테스트로 검증 못 함, Docker 있는 환경에서 통합 테스트로 별도 확인 필요. Docker 불필요(이 유닛 테스트는).
- `ChoreCompletedEventConsumerTest`: `FakeChoreCompletionRecordRepository` 주입, 이벤트 수신 시 저장 호출되는지 확인. Docker 불필요.
- `JpaChoreCompletionRecordRepositoryTest`: 저장 후 조회, 같은 `choreInstanceId`로 두 번 저장 시 예외 없이 조용히 무시되는지(멱등성) — `RoomRepositoryAdapterTest`와 같은 Testcontainers-MySQL 패턴. **Docker 필요.**
- Kafka 종단 간(producer→broker→consumer) 통합 테스트: Testcontainers Kafka로 실제 브로커 띄워서 발행된 이벤트가 실제로 소비되는지 확인. **Docker 필요.**

## 다음 단계

`writing-plans` 스킬로 이 설계를 구현 계획으로 전환.
