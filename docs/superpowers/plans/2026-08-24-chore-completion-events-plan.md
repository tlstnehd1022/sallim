# Chore(집안일) 완료 이벤트 → Kafka → 통계 원본 저장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ChoreInstance.complete()`가 만드는 `ChoreCompletedEvent`를 실제로 Kafka에 발행하고, 별도 소비자가 그 이벤트를 받아 `chore_completion_record`(완료 fact 테이블)에 멱등하게 적재한다.

**Architecture:** `ChoreInstanceService.complete()`가 DB 저장 후 Spring `ApplicationEventPublisher`로 이벤트를 발행 → `@TransactionalEventListener(AFTER_COMMIT)`가 커밋 성공 후에만 `ChoreEventProducer` 포트를 통해 Kafka로 보냄 → `@KafkaListener` 소비자가 받아서 `ChoreCompletionRecordRepository` 포트를 통해 JPA로 저장. 모든 포트는 Room/ChoreDefinition/ChoreInstance 리포지토리와 같은 이유(Docker 없이 페이크로 단위 테스트)로 인터페이스+어댑터 분리.

**Tech Stack:** Kotlin(JDK 21) / Spring Boot 3.3.4 / Spring Kafka (BOM으로 버전 관리) / Testcontainers Kafka / Flyway / Kotest — Spring Kafka와 Testcontainers Kafka 모듈만 신규 추가

**Spec:** `docs/superpowers/specs/2026-08-24-chore-completion-events-design.md`

**설계 문서 대비 구현 세부사항 변경 1건:** 설계 문서는 `JpaChoreCompletionRecordRepository.save()`가 `saveAndFlush()` 호출 후 `DataIntegrityViolationException`을 잡아 무시하는 방식을 제시했다. 이 계획은 대신 **존재 확인 후 삽입**(`existsByChoreInstanceId` 먼저 확인, 있으면 조기 반환) 방식을 쓴다. 이유: 같은 `@DataJpaTest` 트랜잭션 안에서 flush 예외를 잡은 뒤 같은 EntityManager로 계속 조회하는 건 Hibernate 세션이 예외 이후에도 안전하게 재사용 가능한지가 불확실해서(JPA 스펙상 flush 중 예외는 트랜잭션을 rollback-only로 표시하며, 이후 EntityManager 재사용은 권장되지 않음) — 이 프로젝트에서 이미 검증 못 하는 상황(Docker 없음)에 불확실성까지 얹고 싶지 않았다. 존재 확인 방식은 이 우려를 완전히 피하고, 예외를 정상 흐름(Kafka 재전달) 제어에 쓰지 않아도 되며, 코드도 더 단순하다. DB 유니크 제약(`V5`)은 그대로 둬서 드문 동시 경쟁 상황의 최종 방어선 역할은 유지한다(그 경우 예외가 컨슈머까지 전파되지만, Kafka는 어차피 재시도하므로 허용 가능).

## Global Constraints

- 이벤트 발행은 `@TransactionalEventListener(AFTER_COMMIT)` + `KafkaTemplate` — 아웃박스 패턴 안 씀 (설계 "결정된 사항")
- `ChoreEventProducer`/`ChoreCompletionRecordRepository` 포트는 `sallim.chore.domain`에, 프레임워크 의존 없는 순수 Kotlin (CLAUDE.md)
- 완료 기록은 원본 fact만 저장, 집계는 다음 서브프로젝트(CQRS) — 이번 범위에서 집계 테이블/쿼리 안 만듦
- 토픽명 `chore.completed`, 키는 `choreInstanceId`
- 컨슈머는 `chore` 모듈 안에 둔다 — 다른 컨텍스트로 안 뺌
- `chore_completion_record.chore_instance_id`에 DB 유니크 제약 (멱등성 최종 방어선)

---

## File Structure

```
gradle/libs.versions.toml                                                        (수정)
chore/build.gradle.kts                                                           (수정)
bootstrap/src/main/resources/application.yml                                     (수정)
chore/src/main/kotlin/sallim/chore/application/
  ChoreInstanceService.kt                                                        (수정 — complete()에 이벤트 발행 추가)
chore/src/test/kotlin/sallim/chore/application/
  ChoreInstanceServiceTest.kt                                                    (수정 — 이벤트 발행 테스트 추가)
chore/src/main/kotlin/sallim/chore/domain/
  ChoreEventProducer.kt                                                          (신규)
  ChoreCompletionRecordRepository.kt                                             (신규)
chore/src/main/kotlin/sallim/chore/infrastructure/messaging/
  TransactionalChoreEventPublisher.kt                                            (신규)
  KafkaChoreEventProducer.kt                                                     (신규)
  ChoreCompletedEventConsumer.kt                                                 (신규)
chore/src/test/kotlin/sallim/chore/infrastructure/messaging/
  FakeChoreEventProducer.kt                                                      (신규)
  TransactionalChoreEventPublisherTest.kt                                        (신규)
  FakeChoreCompletionRecordRepository.kt                                         (신규)
  ChoreCompletedEventConsumerTest.kt                                             (신규)
  AbstractKafkaIntegrationTest.kt                                                (신규)
  KafkaEndToEndTest.kt                                                           (신규)
chore/src/main/kotlin/sallim/chore/infrastructure/persistence/
  ChoreCompletionRecordEntity.kt                                                 (신규)
  ChoreCompletionRecordJpaRepository.kt                                          (신규)
  JpaChoreCompletionRecordRepository.kt                                          (신규)
chore/src/main/resources/db/migration/
  V5__create_chore_completion_record_table.sql                                   (신규)
chore/src/test/kotlin/sallim/chore/infrastructure/persistence/
  JpaChoreCompletionRecordRepositoryTest.kt                                      (신규)
```

---

### Task 1: 빌드 배선 (Spring Kafka + Testcontainers Kafka)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `chore/build.gradle.kts`
- Modify: `bootstrap/src/main/resources/application.yml`

**Interfaces:**
- Produces: `chore` 모듈 컴파일/런타임 클래스패스에 `spring-kafka`, 테스트 클래스패스에 `testcontainers-kafka` 추가. `bootstrap`이 실제 Kafka 브로커에 JSON으로 직렬화된 이벤트를 주고받을 수 있도록 `application.yml`에 프로듀서/컨슈머 설정 추가. 이후 모든 태스크가 이 배선 위에서 동작.
- 새 프로덕션 코드 없음 — 순수 빌드/설정 변경.

- [ ] **Step 1: 버전 카탈로그에 라이브러리 추가**

`gradle/libs.versions.toml` 전체를 아래로 교체:

```toml
[versions]
kotlin = "2.0.20"
spring-boot = "3.3.4"
spring-dependency-management = "1.1.6"
kotest = "5.9.1"
testcontainers = "1.21.4"

# spring-boot-starter*, kotlin-reflect, spring-boot-starter-data-jpa, mysql-connector-j,
# flyway-core, flyway-mysql, spring-kafka: 버전 없음 —
# Spring Boot BOM으로 해석. bootstrap은 org.springframework.boot 플러그인이 BOM을 자동
# 임포트하고, chore처럼 그 플러그인 없이 io.spring.dependency-management만 적용하는 모듈은
# dependencyManagement { imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) } }로 직접
# 임포트한다. 둘 중 하나도 없는 모듈에서 이 라이브러리를 쓰면 버전 해석에 실패한다.
# testcontainers-junit-jupiter/testcontainers-mysql/testcontainers-kafka는 Spring Boot 3.3.4
# BOM이 관리하는 1.19.8이 로컬 Docker Desktop(신버전 API)과 "client version too old" 충돌을
# 일으켜 명시적으로 최신으로 고정 — BOM 권장값보다 명시 버전이 우선 적용된다.
[libraries]
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-kafka = { module = "org.springframework.kafka:spring-kafka" }
jackson-module-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin" }
mysql-connector-j = { module = "com.mysql:mysql-connector-j" }
flyway-core = { module = "org.flywaydb:flyway-core" }
flyway-mysql = { module = "org.flywaydb:flyway-mysql" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter", version.ref = "testcontainers" }
testcontainers-mysql = { module = "org.testcontainers:mysql", version.ref = "testcontainers" }
testcontainers-kafka = { module = "org.testcontainers:kafka", version.ref = "testcontainers" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

- [ ] **Step 2: `chore/build.gradle.kts`에 의존성 추가**

`chore/build.gradle.kts` 전체를 아래로 교체 (기존 `dependencySet`에 `entry("kafka")` 한 줄만 추가, `dependencies` 블록에 두 줄 추가):

```kotlin
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
    // Spring Boot 3.3.4가 관리하는 testcontainers 1.19.8은 최신 로컬 Docker 엔진과
    // "client version too old" 충돌이 나서 libs.versions.toml의 명시 버전으로 오버라이드.
    dependencies {
        dependencySet("org.testcontainers:${libs.versions.testcontainers.get()}") {
            entry("testcontainers")
            entry("junit-jupiter")
            entry("mysql")
            entry("jdbc")
            entry("database-commons")
            entry("kafka")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.kafka)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
    testImplementation(libs.testcontainers.kafka)
}
```

- [ ] **Step 3: `bootstrap/application.yml`에 Kafka 설정 추가**

`bootstrap/src/main/resources/application.yml` 전체를 아래로 교체:

```yaml
spring:
  application:
    name: sallim
  datasource:
    url: jdbc:mysql://localhost:3306/sallim
    username: sallim
    password: sallim
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: chore-stats
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "sallim.chore.domain"
```

- [ ] **Step 4: 의존성 해석 + 기존 빌드 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` (이 셸에 `JAVA_HOME`이 PATH에 없음 — 모든 gradle 실행 전에 필요), 이어서 `./gradlew :chore:compileKotlin :chore:compileTestKotlin`
Expected: `BUILD SUCCESSFUL` — `spring-kafka`/`testcontainers-kafka` 의존성이 버전 충돌 없이 해석된다. (Docker 불필요 — 컴파일만 확인)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml chore/build.gradle.kts bootstrap/src/main/resources/application.yml
git commit -m "build: chore 모듈에 Spring Kafka/Testcontainers Kafka 배선"
```

---

### Task 2: `ChoreInstanceService.complete()` — 완료 시 이벤트 발행

**Files:**
- Modify: `chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt`
- Test: `chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt` (수정)

**Interfaces:**
- Consumes: `ChoreInstance.domainEvents: List<DomainEvent>`(기존), `ChoreInstance.clearEvents()`(기존), `ChoreCompletedEvent`(기존, `sallim.chore.domain`)
- Produces: `ChoreInstanceService`의 두 번째 생성자 파라미터로 `eventPublisher: ApplicationEventPublisher`(기본값 `ApplicationEventPublisher { }`) 추가 — 이 파라미터에 기본값이 있으므로 기존에 `ChoreInstanceService(instances)` 한 개짜리 인자로 생성하던 다른 파일들(`ChoreInstanceControllerTest.kt`, `ChoreInstanceSchedulerTest.kt`)은 **수정 불필요**. Task 3의 `TransactionalChoreEventPublisher`가 이 서비스가 발행하는 `ChoreCompletedEvent`(Spring 이벤트로서)를 수신한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt` 전체를 아래로 교체 (import 2개 추가, 마지막 `test(...)` 블록 뒤에 새 테스트 1개 추가, 나머지는 기존 그대로):

```kotlin
package sallim.chore.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.ApplicationEventPublisher
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes
import java.time.LocalDate

class ChoreInstanceServiceTest : FunSpec({
    fun choreDefinition(recurrence: RecurrencePolicy): ChoreDefinition = ChoreDefinition(
        ChoreDefinitionId.generate(), RoomId.generate(), "청소", MemberId.generate(), recurrence, listOf("단계1"), "영상"
    )

    test("날짜로 필터링해 조회한다") {
        val instances = FakeChoreInstanceRepository()
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))
        instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 21)))
        val service = ChoreInstanceService(instances)

        service.listByDate(LocalDate.of(2026, 8, 20)) shouldHaveSize 1
    }

    test("완료 처리하면 저장된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()

        val completed = service.complete(instance.id, member)

        completed.completed shouldBe true
        instances.findById(instance.id)!!.completed shouldBe true
    }

    test("존재하지 않는 인스턴스를 완료 처리하면 NotFoundException") {
        val service = ChoreInstanceService(FakeChoreInstanceRepository())

        shouldThrow<NotFoundException> { service.complete(ChoreInstanceId.generate(), MemberId.generate()) }
    }

    test("이미 완료된 인스턴스를 다시 완료 처리하면 IllegalStateException") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val service = ChoreInstanceService(instances)
        val member = MemberId.generate()
        service.complete(instance.id, member)

        shouldThrow<IllegalStateException> { service.complete(instance.id, member) }
    }

    test("최근 인스턴스로부터 오늘까지 매일 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(3)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 3
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(2), today.minusDays(1), today)
    }

    test("WeeklyNTimes도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(WeeklyNTimes(2))  // nextOccurrence는 7/2=3일 간격
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today.minusDays(7)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusDays(4), today.minusDays(1))
    }

    test("Monthly도 소급 생성한다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Monthly)
        val today = LocalDate.of(2026, 6, 15)
        instances.save(ChoreInstance.schedule(definition.id, today.minusMonths(2)))

        val created = service.generateDueInstances(listOf(definition), today)

        created shouldHaveSize 2
        created.map { it.scheduledDate }.toSet() shouldBe setOf(today.minusMonths(1), today)
    }

    test("이미 오늘까지 인스턴스가 있으면 아무것도 생성하지 않는다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)
        val today = LocalDate.now()
        instances.save(ChoreInstance.schedule(definition.id, today))

        service.generateDueInstances(listOf(definition), today).shouldBeEmpty()
    }

    test("인스턴스가 하나도 없는 정의는 건너뛴다") {
        val instances = FakeChoreInstanceRepository()
        val service = ChoreInstanceService(instances)
        val definition = choreDefinition(Daily)

        service.generateDueInstances(listOf(definition), LocalDate.now()).shouldBeEmpty()
    }

    test("완료 처리하면 ChoreCompletedEvent가 발행된다") {
        val instances = FakeChoreInstanceRepository()
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        instances.save(instance)
        val publishedEvents = mutableListOf<Any>()
        val service = ChoreInstanceService(instances, ApplicationEventPublisher { publishedEvents.add(it) })
        val member = MemberId.generate()

        service.complete(instance.id, member)

        publishedEvents shouldHaveSize 1
        val event = publishedEvents.first() as ChoreCompletedEvent
        event.choreInstanceId shouldBe instance.id
        event.completedBy shouldBe member
    }
})
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: FAIL — `ChoreInstanceService`의 2-인자 생성자가 없어 컴파일 에러

- [ ] **Step 3: `ChoreInstanceService.complete()` 수정**

`chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt` 전체를 아래로 교체:

```kotlin
package sallim.chore.application

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.time.LocalDate

@Service
class ChoreInstanceService(
    private val choreInstanceRepository: ChoreInstanceRepository,
    private val eventPublisher: ApplicationEventPublisher = ApplicationEventPublisher { }
) {
    @Transactional(readOnly = true)
    fun listByDate(date: LocalDate): List<ChoreInstance> =
        choreInstanceRepository.findAll().filter { it.scheduledDate == date }

    @Transactional
    fun complete(id: ChoreInstanceId, completedBy: MemberId): ChoreInstance {
        val instance = choreInstanceRepository.findById(id) ?: throw NotFoundException("chore instance not found: $id")
        instance.complete(completedBy)
        val saved = choreInstanceRepository.save(instance)
        instance.domainEvents.forEach { eventPublisher.publishEvent(it) }
        instance.clearEvents()
        return saved
    }

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
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.application.ChoreInstanceServiceTest"`
Expected: PASS (10개 테스트 모두 통과)

- [ ] **Step 5: 이 서비스를 쓰는 다른 테스트들이 안 깨지는지 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.api.*"`
Expected: PASS — `ChoreInstanceControllerTest`/`ChoreInstanceSchedulerTest`는 `ChoreInstanceService(instances)` 한 개짜리 인자 호출을 그대로 쓰고 있고, 새 파라미터에 기본값이 있어 컴파일도 동작도 그대로다.

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/application/ChoreInstanceService.kt chore/src/test/kotlin/sallim/chore/application/ChoreInstanceServiceTest.kt
git commit -m "feat: ChoreInstance 완료 시 ChoreCompletedEvent를 Spring 이벤트로 발행"
```

---

### Task 3: Kafka 발행 포트/어댑터

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreEventProducer.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisher.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/messaging/KafkaChoreEventProducer.kt`
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreEventProducer.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisherTest.kt`

**Interfaces:**
- Consumes: `ChoreCompletedEvent`(기존, Task 2에서 Spring 이벤트로 발행됨)
- Produces: `interface ChoreEventProducer { fun publish(event: ChoreCompletedEvent) }` — Task 6의 종단 간 테스트가 `KafkaChoreEventProducer`(이 포트의 실제 구현체)를 직접 사용한다.

- [ ] **Step 1: 포트 작성**

순수 인터페이스라 별도 테스트 없이 바로 작성한다 (기존 리포지토리 포트들과 동일 스타일 — 이미 검증된 패턴).

`chore/src/main/kotlin/sallim/chore/domain/ChoreEventProducer.kt`:
```kotlin
package sallim.chore.domain

interface ChoreEventProducer {
    fun publish(event: ChoreCompletedEvent)
}
```

- [ ] **Step 2: 페이크 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreEventProducer.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

class FakeChoreEventProducer : ChoreEventProducer {
    val published = mutableListOf<ChoreCompletedEvent>()

    override fun publish(event: ChoreCompletedEvent) {
        published.add(event)
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisherTest.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId

class TransactionalChoreEventPublisherTest : FunSpec({
    test("이벤트를 받으면 producer에 그대로 전달한다") {
        val producer = FakeChoreEventProducer()
        val publisher = TransactionalChoreEventPublisher(producer)
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        publisher.onChoreCompleted(event)

        producer.published shouldHaveSize 1
        producer.published.first() shouldBe event
    }
})
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.messaging.TransactionalChoreEventPublisherTest"`
Expected: FAIL — `TransactionalChoreEventPublisher`가 없어 컴파일 에러

- [ ] **Step 5: `TransactionalChoreEventPublisher`, `KafkaChoreEventProducer` 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisher.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

@Component
class TransactionalChoreEventPublisher(private val producer: ChoreEventProducer) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChoreCompleted(event: ChoreCompletedEvent) {
        producer.publish(event)
    }
}
```

`chore/src/main/kotlin/sallim/chore/infrastructure/messaging/KafkaChoreEventProducer.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreEventProducer

@Component
class KafkaChoreEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, ChoreCompletedEvent>
) : ChoreEventProducer {
    override fun publish(event: ChoreCompletedEvent) {
        kafkaTemplate.send("chore.completed", event.choreInstanceId.value.toString(), event)
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.messaging.TransactionalChoreEventPublisherTest"`
Expected: PASS (Docker 불필요 — 순수 단위 테스트)

- [ ] **Step 7: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/ChoreEventProducer.kt chore/src/main/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisher.kt chore/src/main/kotlin/sallim/chore/infrastructure/messaging/KafkaChoreEventProducer.kt chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreEventProducer.kt chore/src/test/kotlin/sallim/chore/infrastructure/messaging/TransactionalChoreEventPublisherTest.kt
git commit -m "feat: 트랜잭션 커밋 후 ChoreCompletedEvent를 Kafka로 발행"
```

---

### Task 4: Kafka 소비자 + 완료 기록 포트

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionRecordRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumer.kt`
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreCompletionRecordRepository.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumerTest.kt`

**Interfaces:**
- Consumes: `ChoreCompletedEvent`(기존)
- Produces: `interface ChoreCompletionRecordRepository { fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) }` — Task 5의 `JpaChoreCompletionRecordRepository`가 이 포트를 구현한다.

- [ ] **Step 1: 포트 작성**

`chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionRecordRepository.kt`:
```kotlin
package sallim.chore.domain

import java.time.Instant

interface ChoreCompletionRecordRepository {
    fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant)
}
```

- [ ] **Step 2: 페이크 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreCompletionRecordRepository.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import sallim.chore.domain.ChoreCompletionRecordRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant

class FakeChoreCompletionRecordRepository : ChoreCompletionRecordRepository {
    data class Record(
        val choreInstanceId: ChoreInstanceId,
        val choreDefinitionId: ChoreDefinitionId,
        val completedBy: MemberId,
        val completedAt: Instant
    )

    val saved = mutableListOf<Record>()

    override fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) {
        saved.add(Record(choreInstanceId, choreDefinitionId, completedBy, completedAt))
    }
}
```

- [ ] **Step 3: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumerTest.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId

class ChoreCompletedEventConsumerTest : FunSpec({
    test("이벤트를 받으면 완료 기록을 저장한다") {
        val repository = FakeChoreCompletionRecordRepository()
        val consumer = ChoreCompletedEventConsumer(repository)
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        consumer.onMessage(event)

        repository.saved shouldHaveSize 1
        repository.saved.first().choreInstanceId shouldBe event.choreInstanceId
        repository.saved.first().choreDefinitionId shouldBe event.choreDefinitionId
        repository.saved.first().completedBy shouldBe event.completedBy
        repository.saved.first().completedAt shouldBe event.occurredAt
    }
})
```

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.messaging.ChoreCompletedEventConsumerTest"`
Expected: FAIL — `ChoreCompletedEventConsumer`가 없어 컴파일 에러

- [ ] **Step 5: `ChoreCompletedEventConsumer` 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumer.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreCompletionRecordRepository

@Component
class ChoreCompletedEventConsumer(private val repository: ChoreCompletionRecordRepository) {
    @KafkaListener(topics = ["chore.completed"], groupId = "chore-stats")
    fun onMessage(event: ChoreCompletedEvent) {
        repository.save(event.choreInstanceId, event.choreDefinitionId, event.completedBy, event.occurredAt)
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.messaging.ChoreCompletedEventConsumerTest"`
Expected: PASS (Docker 불필요 — 순수 단위 테스트, `@KafkaListener`는 어노테이션일 뿐 이 테스트에서 실제 브로커 연결 없이 메서드를 직접 호출한다)

- [ ] **Step 7: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/ChoreCompletionRecordRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumer.kt chore/src/test/kotlin/sallim/chore/infrastructure/messaging/FakeChoreCompletionRecordRepository.kt chore/src/test/kotlin/sallim/chore/infrastructure/messaging/ChoreCompletedEventConsumerTest.kt
git commit -m "feat: Kafka 완료 이벤트 소비자"
```

---

### Task 5: 완료 기록 영속성 (JPA 엔티티/어댑터/마이그레이션)

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordEntity.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepository.kt`
- Create: `chore/src/main/resources/db/migration/V5__create_chore_completion_record_table.sql`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepositoryTest.kt`

**Interfaces:**
- Consumes: `ChoreCompletionRecordRepository`(Task 4)
- Produces: `class JpaChoreCompletionRecordRepository(jpaRepository: ChoreCompletionRecordJpaRepository) : ChoreCompletionRecordRepository` — 이 태스크가 완료 이벤트 파이프라인의 마지막 저장 지점. Task 6의 종단 간 테스트는 이 리포지토리를 직접 쓰지 않고 별도 테스트 전용 컨슈머를 쓴다(아래 Task 6 참고) — 이미 이 태스크에서 저장 로직 자체는 검증됨.

- [ ] **Step 1: 엔티티, JpaRepository 작성 (멱등성용 존재 확인 쿼리 포함)**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordEntity.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

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
```

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreCompletionRecordJpaRepository : JpaRepository<ChoreCompletionRecordEntity, String> {
    fun existsByChoreInstanceId(choreInstanceId: String): Boolean
}
```

- [ ] **Step 2: Flyway 마이그레이션 작성**

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

- [ ] **Step 3: 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepositoryTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaChoreCompletionRecordRepository::class)
class JpaChoreCompletionRecordRepositoryTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var repository: JpaChoreCompletionRecordRepository

    @Autowired
    lateinit var jpaRepository: ChoreCompletionRecordJpaRepository

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `완료 기록을 저장하면 조회된다`() {
        val instanceId = ChoreInstanceId.generate()
        val definitionId = ChoreDefinitionId.generate()
        val member = MemberId.generate()
        val completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()

        val all = jpaRepository.findAll()
        all shouldHaveSize 1
        all.first().choreInstanceId shouldBe instanceId.value.toString()
        all.first().choreDefinitionId shouldBe definitionId.value.toString()
        all.first().completedBy shouldBe member.value.toString()
        all.first().completedAt shouldBe completedAt
    }

    @Test
    fun `같은 인스턴스로 두 번 저장해도 중복 없이 무시된다`() {
        val instanceId = ChoreInstanceId.generate()
        val definitionId = ChoreDefinitionId.generate()
        val member = MemberId.generate()
        val completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()
        repository.save(instanceId, definitionId, member, completedAt)
        em.flush()
        em.clear()

        jpaRepository.findAll() shouldHaveSize 1
    }
}
```
`completedAt`은 `Instant.now().truncatedTo(ChronoUnit.MICROS)`로 미리 마이크로초까지 잘라서 넘긴다 — `DATETIME(6)`은 마이크로초까지만 저장하는데 `Instant.now()`는 나노초 정밀도라, 그대로 비교하면 나노초 자리 때문에 간헐적으로 실패한다(스케줄러 서브프로젝트에서 겪은 것과 같은 문제, 여기선 처음부터 잘라서 회피).

- [ ] **Step 4: 테스트 실행 → 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.JpaChoreCompletionRecordRepositoryTest"`
Expected: FAIL — `JpaChoreCompletionRecordRepository`가 없어 컴파일 에러. (Docker Desktop이 로컬에 떠 있어야 Testcontainers가 MySQL 컨테이너를 띄울 수 있다 — 이 단계는 컴파일 실패라 Docker 필요 없음)

- [ ] **Step 5: `JpaChoreCompletionRecordRepository` 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepository.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreCompletionRecordRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.time.Instant
import java.util.UUID

@Repository
class JpaChoreCompletionRecordRepository(
    private val jpaRepository: ChoreCompletionRecordJpaRepository
) : ChoreCompletionRecordRepository {
    companion object {
        private val logger = LoggerFactory.getLogger(JpaChoreCompletionRecordRepository::class.java)
    }

    override fun save(choreInstanceId: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, completedBy: MemberId, completedAt: Instant) {
        val instanceIdStr = choreInstanceId.value.toString()
        if (jpaRepository.existsByChoreInstanceId(instanceIdStr)) {
            logger.info("chore completion record already exists for instance {}, skipping (Kafka redelivery)", choreInstanceId)
            return
        }
        jpaRepository.save(
            ChoreCompletionRecordEntity(
                id = UUID.randomUUID().toString(),
                choreInstanceId = instanceIdStr,
                choreDefinitionId = choreDefinitionId.value.toString(),
                completedBy = completedBy.value.toString(),
                completedAt = completedAt
            )
        )
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.JpaChoreCompletionRecordRepositoryTest"`
Expected: PASS (2개 테스트 모두 통과). 실패 시 가장 먼저 Docker Desktop이 실행 중인지 확인.

- [ ] **Step 7: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordEntity.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreCompletionRecordJpaRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepository.kt chore/src/main/resources/db/migration/V5__create_chore_completion_record_table.sql chore/src/test/kotlin/sallim/chore/infrastructure/persistence/JpaChoreCompletionRecordRepositoryTest.kt
git commit -m "feat: ChoreCompletionRecord JPA 엔티티/어댑터/마이그레이션"
```

---

### Task 6: Kafka 종단 간 통합 테스트 + 최종 빌드 검증

**Files:**
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/AbstractKafkaIntegrationTest.kt`
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/messaging/KafkaEndToEndTest.kt`

**Interfaces:**
- Consumes: `KafkaChoreEventProducer`(Task 3)
- Produces: 이 태스크가 계획의 마지막이라 이후 소비자 없음. `./gradlew build` 전체 검증으로 마무리.

이 테스트는 "발행 → 실제 브로커 → 소비" 배선 자체만 검증한다 — 저장 로직(Task 5에서 이미 페이크/DB로 각각 검증됨)을 다시 검증하지 않기 위해, 운영용 `ChoreCompletedEventConsumer` 대신 이 테스트 전용의 기록용 리스너를 하나 둔다.

- [ ] **Step 1: Testcontainers Kafka 공유 인프라 작성**

이 파일은 인프라 설정이라 별도 단위 테스트 없이 바로 작성한다 — 뒤이은 `KafkaEndToEndTest`가 정상 동작하는지가 곧 이 설정이 맞는지의 검증이다.

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/AbstractKafkaIntegrationTest.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractKafkaIntegrationTest {
    companion object {
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers)
        }
    }
}
```
`org.testcontainers.containers.KafkaContainer`가 `testcontainers-kafka` 모듈(Task 1에서 추가)에서 컴파일이 안 되면(최신 testcontainers 버전에서 `org.testcontainers.kafka.ConfluentKafkaContainer`로 옮겨졌을 수 있음), import를 `org.testcontainers.kafka.ConfluentKafkaContainer`로 바꾸고 `KafkaContainer` 대신 `ConfluentKafkaContainer`를 사용 — 나머지 코드(생성자 인자, `getBootstrapServers`)는 동일하게 동작한다.

이 컨테이너는 `AbstractMySqlIntegrationTest`와 같은 이유로 명시적으로 멈추지 않는다 — Ryuk 리소스 리퍼가 JVM 종료 시 정리한다.

- [ ] **Step 2: 종단 간 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/messaging/KafkaEndToEndTest.kt`:
```kotlin
package sallim.chore.infrastructure.messaging

import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import sallim.chore.TestApplication
import sallim.chore.domain.ChoreCompletedEvent
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.MemberId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Component
class RecordingChoreCompletedListener {
    val received = mutableListOf<ChoreCompletedEvent>()
    val latch = CountDownLatch(1)

    @KafkaListener(topics = ["chore.completed"], groupId = "test-recorder")
    fun onMessage(event: ChoreCompletedEvent) {
        received.add(event)
        latch.countDown()
    }
}

@SpringBootTest(classes = [TestApplication::class])
@Import(KafkaChoreEventProducer::class, RecordingChoreCompletedListener::class)
class KafkaEndToEndTest : AbstractKafkaIntegrationTest() {

    @Autowired
    lateinit var producer: KafkaChoreEventProducer

    @Autowired
    lateinit var listener: RecordingChoreCompletedListener

    @Test
    fun `발행한 이벤트를 실제 브로커를 거쳐 컨슈머가 받는다`() {
        val event = ChoreCompletedEvent(ChoreInstanceId.generate(), ChoreDefinitionId.generate(), MemberId.generate())

        producer.publish(event)

        val received = listener.latch.await(10, TimeUnit.SECONDS)
        received shouldBe true
        listener.received shouldHaveSize 1
    }
}
```
`io.kotest.matchers.shouldBe` import가 빠지지 않도록 주의 — `received shouldBe true`에 필요.

운영용 `ChoreCompletedEventConsumer`(Task 4)와 별개로 `RecordingChoreCompletedListener`를 테스트 전용으로 둔 이유: `ChoreCompletedEventConsumer`가 실제로 저장까지 하는지는 Task 5에서 이미 DB로 검증됐고, 이 테스트는 오직 "Kafka JSON 직렬화/역직렬화 설정이 실제로 맞는지, 메시지가 브로커를 거쳐 실제로 도착하는지"만 좁게 검증한다 — 같은 걸 두 번 검증하지 않는다.

- [ ] **Step 3: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.messaging.KafkaEndToEndTest"`
Expected: PASS (10초 안에 메시지 수신)

- [ ] **Step 4: chore 모듈 전체 + 루트 빌드 검증 (Docker Desktop 필요)**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1~6에서 작성한 모든 테스트 통과 — 도메인/application/api 테스트 + 신규 messaging/persistence 통합 테스트)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add chore/src/test/kotlin/sallim/chore/infrastructure/messaging/AbstractKafkaIntegrationTest.kt chore/src/test/kotlin/sallim/chore/infrastructure/messaging/KafkaEndToEndTest.kt
git commit -m "test: Kafka 종단 간(producer-broker-consumer) 통합 테스트"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- `@TransactionalEventListener(AFTER_COMMIT)` + `KafkaTemplate` 발행 → Task 2, 3
- 포트/어댑터 패턴(`ChoreEventProducer`, `ChoreCompletionRecordRepository`) → Task 3, 4, 5
- 완료 기록 원본 fact 저장, 집계 없음 → Task 5 (엔티티에 카운트/집계 필드 없음)
- 멱등성 → Task 5. **설계 문서의 "예외 잡기" 방식에서 "존재 확인 후 삽입" 방식으로 변경** — 이유는 이 문서 상단 "설계 문서 대비 구현 세부사항 변경 1건" 참고. DB 유니크 제약(V5)은 설계 그대로 유지.
- 토픽명/키 → Task 3 (`KafkaChoreEventProducer`)
- 컨슈머는 chore 모듈 안 → Task 4 (`sallim.chore.infrastructure.messaging`)
- 빌드/설정 배선 → Task 1

**Placeholder scan:** 전 단계 실제 코드/SQL/커맨드 포함. TBD/TODO 없음. Task 6의 `KafkaContainer` 클래스명 대체 안내는 실제 라이브러리 버전 불확실성에 대한 구체적 대체 경로(다른 정확한 클래스명)이지 막연한 TODO가 아님.

**Type consistency:** `ChoreCompletedEvent`(기존, Task2/3/4/6 공통 사용) → `ChoreInstanceService(choreInstanceRepository, eventPublisher)`(Task2) → `ChoreEventProducer.publish(event)`/`TransactionalChoreEventPublisher.onChoreCompleted(event)`(Task3) → `ChoreCompletionRecordRepository.save(choreInstanceId, choreDefinitionId, completedBy, completedAt)`/`ChoreCompletedEventConsumer.onMessage(event)`(Task4) → `JpaChoreCompletionRecordRepository`(Task5, Task4의 포트 구현) → `KafkaChoreEventProducer`(Task3에서 이미 정의, Task6이 재사용) 순서로 각 태스크의 Produces가 다음 태스크의 Consumes와 시그니처 일치.
