# Chore(집안일) 영속성 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `chore` 모듈의 `Room`/`ChoreDefinition`/`ChoreInstance` 도메인 모델에 JPA 엔티티 + 리포지토리 어댑터 + Flyway 마이그레이션을 붙여 실제로 MySQL에 저장/조회할 수 있게 만든다.

**Architecture:** 도메인 클래스는 그대로 두고(`sallim.chore.domain`에 프레임워크 의존 없음), `sallim.chore.infrastructure.persistence`에 별도 JPA 엔티티 + Spring Data JPA 리포지토리 + 어댑터(도메인 포트 구현체)를 추가한다. 도메인 ↔ 엔티티 변환은 어댑터가 담당. `ChoreInstance`는 새 `reconstitute` 팩토리로 이벤트 재발행 없이 복원한다.

**Tech Stack:** Kotlin(JDK 21) / Spring Boot 3.3.4 / Spring Data JPA / MySQL(mysql-connector-j) / Flyway / Testcontainers(MySQL) — 전부 Spring Boot 3.3.4 BOM으로 버전 관리, 신규 버전 번호 하드코딩 없음

**Spec:** `docs/superpowers/specs/2026-08-20-chore-persistence-design.md`

## Global Constraints

- 바운디드 컨텍스트 간 직접 참조 금지 — `chore`는 `household`를 참조하지 않는다 (CLAUDE.md)
- `sallim.chore.domain` 패키지는 Spring·JPA 등 프레임워크 의존 금지 — 순수 Kotlin 유지. 엔티티는 `sallim.chore.infrastructure.persistence`에 별도 클래스로 (CLAUDE.md, 설계 문서)
- YAGNI — 이번 범위는 엔티티/매핑/리포지토리 어댑터/Flyway/통합테스트뿐. `application`/`api` 레이어, `DefaultRooms` 시드 데이터 DB 적재, QueryDSL 설치, 자정 배치 스케줄러, Kafka, CQRS, household 실연동은 범위 밖 (설계 문서 "제외" 절)
- UUID 기반 ID는 엔티티에서 `String`(CHAR(36))으로 저장 — 매퍼 경계에서 `UUID.toString()`/`UUID.fromString()` 변환
- FK 제약(REFERENCES)은 걸지 않는다 — raw 컬럼만 (설계 문서)
- `bootstrap/src/main/resources/application.yml`은 이번 범위에서 건드리지 않는다 (설계 문서)

---

## File Structure

```
gradle/libs.versions.toml                                          (수정)
chore/build.gradle.kts                                              (수정)
chore/src/main/kotlin/sallim/chore/domain/
  RoomRepository.kt                                                 (신규)
  ChoreDefinitionRepository.kt                                      (신규)
  ChoreInstanceRepository.kt                                        (신규)
  ChoreInstance.kt                                                  (수정 — reconstitute 팩토리 추가)
chore/src/test/kotlin/sallim/chore/domain/
  ChoreInstanceTest.kt                                               (수정 — reconstitute 테스트 추가)
chore/src/main/kotlin/sallim/chore/infrastructure/persistence/
  RoomEntity.kt                                                     (신규)
  RoomJpaRepository.kt                                              (신규)
  RoomRepositoryAdapter.kt                                          (신규)
  ChoreDefinitionEntity.kt                                          (신규)
  ChoreDefinitionJpaRepository.kt                                   (신규)
  ChoreDefinitionRepositoryAdapter.kt                                (신규)
  ChoreInstanceEntity.kt                                            (신규)
  ChoreInstanceJpaRepository.kt                                     (신규)
  ChoreInstanceRepositoryAdapter.kt                                 (신규)
chore/src/main/resources/db/migration/
  V1__create_room_table.sql                                         (신규)
  V2__create_chore_definition_tables.sql                            (신규)
  V3__create_chore_instance_table.sql                               (신규)
chore/src/test/kotlin/sallim/chore/infrastructure/persistence/
  TestApplication.kt                                                (신규)
  AbstractMySqlIntegrationTest.kt                                   (신규)
  RoomRepositoryAdapterTest.kt                                      (신규)
  ChoreDefinitionRepositoryAdapterTest.kt                           (신규)
  ChoreInstanceRepositoryAdapterTest.kt                             (신규)
```

---

### Task 1: 빌드 배선 (Spring Data JPA + MySQL + Flyway + Testcontainers)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `chore/build.gradle.kts`

**Interfaces:**
- Produces: `chore` 모듈의 compile/runtime classpath에 `spring-boot-starter-data-jpa`, `mysql-connector-j`, `flyway-core`/`flyway-mysql`(runtime), test classpath에 `spring-boot-starter-test` + `testcontainers`(junit-jupiter/mysql) 추가. `org.jetbrains.kotlin.plugin.jpa` 컴파일러 플러그인 활성화(`@Entity` 클래스에 자동으로 no-arg 생성자 + open 처리). 이후 모든 태스크가 이 classpath 위에서 동작한다.
- 새 프로덕션 코드 없음 — 순수 빌드 설정 변경.

- [ ] **Step 1: 버전 카탈로그에 라이브러리/플러그인 추가**

`gradle/libs.versions.toml` 전체를 아래로 교체:

```toml
[versions]
kotlin = "2.0.20"
spring-boot = "3.3.4"
spring-dependency-management = "1.1.6"
kotest = "5.9.1"

# spring-boot-starter*, kotlin-reflect, spring-boot-starter-data-jpa, mysql-connector-j,
# flyway-core, flyway-mysql, testcontainers-junit-jupiter, testcontainers-mysql: 버전 없음 —
# Spring Boot BOM으로 해석. bootstrap은 org.springframework.boot 플러그인이 BOM을 자동
# 임포트하고, chore처럼 그 플러그인 없이 io.spring.dependency-management만 적용하는 모듈은
# dependencyManagement { imports { mavenBom(SpringBootPlugin.BOM_COORDINATES) } }로 직접
# 임포트한다. 둘 중 하나도 없는 모듈에서 이 라이브러리를 쓰면 버전 해석에 실패한다.
[libraries]
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
mysql-connector-j = { module = "com.mysql:mysql-connector-j" }
flyway-core = { module = "org.flywaydb:flyway-core" }
flyway-mysql = { module = "org.flywaydb:flyway-mysql" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter" }
testcontainers-mysql = { module = "org.testcontainers:mysql" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
kotlin-jpa = { id = "org.jetbrains.kotlin.plugin.jpa", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

- [ ] **Step 2: `chore/build.gradle.kts`에 플러그인 + 의존성 추가**

`chore/build.gradle.kts` 전체를 아래로 교체:

```kotlin
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
```

- [ ] **Step 3: 의존성 해석 + 기존 빌드 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` (이 셸에 `JAVA_HOME`이 PATH에 없음 — 모든 gradle 실행 전에 필요), 이어서 `./gradlew :chore:build`
Expected: `BUILD SUCCESSFUL` — 기존 도메인 테스트(Task 1~6, 이전 서브프로젝트)가 새 의존성 아래서도 그대로 통과하고, `spring-boot-starter-data-jpa`/`mysql-connector-j`/`flyway-core`/`flyway-mysql`/testcontainers 의존성이 버전 충돌 없이 해석된다.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml chore/build.gradle.kts
git commit -m "build: chore 모듈에 Spring Data JPA/MySQL/Flyway/Testcontainers 배선"
```

---

### Task 2: 도메인 리포지토리 포트 + ChoreInstance.reconstitute

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt`
- Modify: `chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt`
- Test: `chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt` (수정)

**Interfaces:**
- Consumes: 기존 `Room`/`RoomPlacement`/`ChoreDefinition`/`ChoreInstance`/`ChoreInstanceId`/`ChoreDefinitionId`/`MemberId`(이전 서브프로젝트)
- Produces:
  - `interface RoomRepository { fun save(room: Room, placement: RoomPlacement): Room; fun findAll(): List<Pair<Room, RoomPlacement>> }`
  - `interface ChoreDefinitionRepository { fun save(choreDefinition: ChoreDefinition): ChoreDefinition; fun findAll(): List<ChoreDefinition> }`
  - `interface ChoreInstanceRepository { fun save(choreInstance: ChoreInstance): ChoreInstance; fun findById(id: ChoreInstanceId): ChoreInstance? }`
  - `ChoreInstance.Companion.reconstitute(id: ChoreInstanceId, choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate, completed: Boolean, completedBy: MemberId?, completedAt: Instant?): ChoreInstance`
  - Task 3의 `RoomRepositoryAdapter`가 `RoomRepository`를, Task 4의 `ChoreDefinitionRepositoryAdapter`가 `ChoreDefinitionRepository`를, Task 5의 `ChoreInstanceRepositoryAdapter`가 `ChoreInstanceRepository`와 `reconstitute`를 사용한다.

- [ ] **Step 1: 리포지토리 포트 3개 작성**

순수 인터페이스라 별도 테스트 없이 바로 작성한다 (household의 `HouseholdRepository`와 동일 스타일 — 이미 검증된 패턴).

`chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt`:
```kotlin
package sallim.chore.domain

interface RoomRepository {
    fun save(room: Room, placement: RoomPlacement): Room
    fun findAll(): List<Pair<Room, RoomPlacement>>
}
```

`chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt`:
```kotlin
package sallim.chore.domain

interface ChoreDefinitionRepository {
    fun save(choreDefinition: ChoreDefinition): ChoreDefinition
    fun findAll(): List<ChoreDefinition>
}
```

`chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt`:
```kotlin
package sallim.chore.domain

interface ChoreInstanceRepository {
    fun save(choreInstance: ChoreInstance): ChoreInstance
    fun findById(id: ChoreInstanceId): ChoreInstance?
}
```

- [ ] **Step 2: reconstitute 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt`의 `import java.time.LocalDate` 줄 바로 아래에 `import java.time.Instant`를 추가하고, 마지막 `test(...)` 블록(`"이미 완료된 인스턴스는 다시 완료할 수 없다"`) 뒤에 새 테스트를 추가한다. 파일 전체를 아래로 교체:

```kotlin
package sallim.chore.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate

class ChoreInstanceTest : FunSpec({
    val choreDefinitionId = ChoreDefinitionId.generate()
    val scheduledDate = LocalDate.of(2026, 8, 19)

    test("schedule 직후에는 미완료 상태다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)

        instance.completed shouldBe false
        instance.completedBy shouldBe null
        instance.completedAt shouldBe null
    }

    test("complete 호출 시 완료 처리되고 ChoreCompletedEvent가 발행된다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        val memberId = MemberId.generate()

        instance.complete(memberId)

        instance.completed shouldBe true
        instance.completedBy shouldBe memberId
        instance.domainEvents shouldHaveSize 1
        val event = instance.domainEvents.first() as ChoreCompletedEvent
        event.choreInstanceId shouldBe instance.id
        event.choreDefinitionId shouldBe choreDefinitionId
        event.completedBy shouldBe memberId
    }

    test("이미 완료된 인스턴스는 다시 완료할 수 없다") {
        val instance = ChoreInstance.schedule(choreDefinitionId, scheduledDate)
        instance.complete(MemberId.generate())

        io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
            instance.complete(MemberId.generate())
        }
    }

    test("reconstitute는 저장된 상태를 그대로 복원하고 이벤트를 발행하지 않는다") {
        val memberId = MemberId.generate()
        val completedAt = Instant.now()

        val instance = ChoreInstance.reconstitute(
            id = ChoreInstanceId.generate(),
            choreDefinitionId = choreDefinitionId,
            scheduledDate = scheduledDate,
            completed = true,
            completedBy = memberId,
            completedAt = completedAt
        )

        instance.completed shouldBe true
        instance.completedBy shouldBe memberId
        instance.completedAt shouldBe completedAt
        instance.domainEvents shouldHaveSize 0
    }
})
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.domain.ChoreInstanceTest"`
Expected: FAIL — `ChoreInstance.reconstitute`가 없어 컴파일 에러.

- [ ] **Step 4: ChoreInstance에 reconstitute 팩토리 추가**

`chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt`의 `companion object` 블록을 아래로 교체 (`schedule`은 그대로, `reconstitute`만 추가):

```kotlin
    companion object {
        fun schedule(choreDefinitionId: ChoreDefinitionId, scheduledDate: LocalDate): ChoreInstance =
            ChoreInstance(
                id = ChoreInstanceId.generate(),
                choreDefinitionId = choreDefinitionId,
                scheduledDate = scheduledDate,
                completed = false,
                completedBy = null,
                completedAt = null
            )

        fun reconstitute(
            id: ChoreInstanceId,
            choreDefinitionId: ChoreDefinitionId,
            scheduledDate: LocalDate,
            completed: Boolean,
            completedBy: MemberId?,
            completedAt: Instant?
        ): ChoreInstance =
            ChoreInstance(
                id = id,
                choreDefinitionId = choreDefinitionId,
                scheduledDate = scheduledDate,
                completed = completed,
                completedBy = completedBy,
                completedAt = completedAt
            )
    }
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :chore:test --tests "sallim.chore.domain.ChoreInstanceTest"`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 6: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/domain/RoomRepository.kt chore/src/main/kotlin/sallim/chore/domain/ChoreDefinitionRepository.kt chore/src/main/kotlin/sallim/chore/domain/ChoreInstanceRepository.kt chore/src/main/kotlin/sallim/chore/domain/ChoreInstance.kt chore/src/test/kotlin/sallim/chore/domain/ChoreInstanceTest.kt
git commit -m "feat: chore 리포지토리 포트 3종 + ChoreInstance.reconstitute 팩토리"
```

---

### Task 3: Room 영속성 (엔티티 + 어댑터 + 마이그레이션 + 공유 테스트 인프라)

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomEntity.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomJpaRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapter.kt`
- Create: `chore/src/main/resources/db/migration/V1__create_room_table.sql`
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/TestApplication.kt`
- Create: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `RoomRepository`(Task 2), `Room`/`RoomId`/`RoomPlacement`/`FloorPlan`(이전 서브프로젝트)
- Produces:
  - `class TestApplication` — `@SpringBootApplication`, `chore` 모듈 테스트 전용(운영 코드 아님). Task 4/5의 `@DataJpaTest` 슬라이스가 이 클래스를 컨텍스트 부트스트랩 지점으로 찾는다(패키지가 같아서 자동 인식).
  - `abstract class AbstractMySqlIntegrationTest` — MySQL Testcontainers를 테스트 클래스 전체에서 공유(싱글턴 컨테이너 패턴). Task 4/5의 통합 테스트가 이 클래스를 상속한다.
  - `class RoomRepositoryAdapter(jpaRepository: RoomJpaRepository) : RoomRepository`

- [ ] **Step 1: 공유 테스트 인프라 작성 (TestApplication, AbstractMySqlIntegrationTest)**

이 파일들은 인프라 설정이라 별도 단위 테스트 없이 바로 작성한다 — 뒤이은 `RoomRepositoryAdapterTest`가 정상 동작하는지가 곧 이 설정이 맞는지의 검증이다.

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/TestApplication.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class TestApplication
```

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

class KMySqlContainer(imageName: String) : MySQLContainer<KMySqlContainer>(DockerImageName.parse(imageName))

abstract class AbstractMySqlIntegrationTest {
    companion object {
        @JvmStatic
        val mysql: KMySqlContainer = KMySqlContainer("mysql:8.0").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }
}
```

이 컨테이너는 명시적으로 멈추지 않는다 — Testcontainers의 Ryuk 리소스 리퍼가 JVM 종료 시 정리한다(공식 "싱글턴 컨테이너" 패턴, `@Container`/`@Testcontainers`를 쓰지 않는 이유는 클래스당 컨테이너를 새로 띄우지 않고 Task 3~5의 세 테스트 클래스가 하나를 공유하기 위함).

- [ ] **Step 2: RoomEntity, RoomJpaRepository 작성**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomEntity.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "room")
class RoomEntity(
    @Id
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val z: Int
)
```

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomJpaRepository.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface RoomJpaRepository : JpaRepository<RoomEntity, String>
```

- [ ] **Step 3: Flyway 마이그레이션 작성**

`chore/src/main/resources/db/migration/V1__create_room_table.sql`:
```sql
CREATE TABLE room (
    id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    w INT NOT NULL,
    h INT NOT NULL,
    z INT NOT NULL,
    PRIMARY KEY (id)
);
```

- [ ] **Step 4: RoomRepositoryAdapter 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapterTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import sallim.chore.domain.FloorPlan
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RoomRepositoryAdapter::class)
class RoomRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: RoomRepositoryAdapter

    @Test
    fun `저장한 방과 배치를 다시 읽으면 값이 같다`() {
        val room = Room(RoomId.generate(), "거실")
        val placement = RoomPlacement(room.id, x = 26, y = 38, w = 74, h = 50, z = 1)

        adapter.save(room, placement)

        val found = adapter.findAll()
        found shouldHaveSize 1
        val (foundRoom, foundPlacement) = found[0]
        foundRoom.id shouldBe room.id
        foundRoom.name shouldBe room.name
        foundPlacement shouldBe placement
    }

    @Test
    fun `여러 방을 저장하면 findAll 결과로 FloorPlan을 조립할 수 있다`() {
        val living = Room(RoomId.generate(), "거실")
        val livingPlacement = RoomPlacement(living.id, x = 26, y = 38, w = 74, h = 50, z = 1)
        val kitchen = Room(RoomId.generate(), "주방")
        val kitchenPlacement = RoomPlacement(kitchen.id, x = 26, y = 8, w = 36, h = 30, z = 2)

        adapter.save(living, livingPlacement)
        adapter.save(kitchen, kitchenPlacement)

        val placements = adapter.findAll().map { it.second }
        val floorPlan = FloorPlan.of(placements)
        floorPlan.placements shouldHaveSize 2
    }
}
```

- [ ] **Step 5: 테스트 실행 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.RoomRepositoryAdapterTest"`
Expected: FAIL — `RoomRepositoryAdapter`가 없어 컴파일 에러. (Docker Desktop이 로컬에 떠 있어야 Testcontainers가 MySQL 컨테이너를 띄울 수 있다 — 이 단계는 컴파일 실패라 Docker 필요 없음)

- [ ] **Step 6: RoomRepositoryAdapter 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapter.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.Room
import sallim.chore.domain.RoomId
import sallim.chore.domain.RoomPlacement
import sallim.chore.domain.RoomRepository
import java.util.UUID

@Repository
class RoomRepositoryAdapter(
    private val jpaRepository: RoomJpaRepository
) : RoomRepository {

    override fun save(room: Room, placement: RoomPlacement): Room {
        jpaRepository.save(
            RoomEntity(
                id = room.id.value.toString(),
                name = room.name,
                x = placement.x,
                y = placement.y,
                w = placement.w,
                h = placement.h,
                z = placement.z
            )
        )
        return room
    }

    override fun findAll(): List<Pair<Room, RoomPlacement>> =
        jpaRepository.findAll().map { entity ->
            val roomId = RoomId(UUID.fromString(entity.id))
            Room(roomId, entity.name) to RoomPlacement(roomId, entity.x, entity.y, entity.w, entity.h, entity.z)
        }
}
```

- [ ] **Step 7: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.RoomRepositoryAdapterTest"`
Expected: PASS (2개 테스트 모두 통과). 실패 시 가장 먼저 Docker Desktop이 실행 중인지 확인 — Testcontainers는 로컬 Docker 데몬이 필요하다.

- [ ] **Step 8: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomEntity.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomJpaRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapter.kt chore/src/main/resources/db/migration/V1__create_room_table.sql chore/src/test/kotlin/sallim/chore/infrastructure/persistence/TestApplication.kt chore/src/test/kotlin/sallim/chore/infrastructure/persistence/AbstractMySqlIntegrationTest.kt chore/src/test/kotlin/sallim/chore/infrastructure/persistence/RoomRepositoryAdapterTest.kt
git commit -m "feat: Room JPA 엔티티/어댑터/마이그레이션 + 공유 Testcontainers 인프라"
```

---

### Task 4: ChoreDefinition 영속성

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionEntity.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionJpaRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt`
- Create: `chore/src/main/resources/db/migration/V2__create_chore_definition_tables.sql`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `ChoreDefinitionRepository`(Task 2), `ChoreDefinition`/`ChoreDefinitionId`/`RoomId`/`MemberId`/`RecurrencePolicy`/`Daily`/`WeeklyNTimes`/`Monthly`(이전 서브프로젝트), `TestApplication`/`AbstractMySqlIntegrationTest`(Task 3)
- Produces: `class ChoreDefinitionRepositoryAdapter(jpaRepository: ChoreDefinitionJpaRepository) : ChoreDefinitionRepository` — Task 5에서 직접 재사용하지는 않지만 같은 매핑 패턴(String↔UUID, enum↔sealed interface)을 Task 5의 리뷰 기준으로 삼는다.

- [ ] **Step 1: ChoreDefinitionEntity, ChoreDefinitionJpaRepository 작성**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionEntity.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OrderColumn
import jakarta.persistence.Table

@Entity
@Table(name = "chore_definition")
class ChoreDefinitionEntity(
    @Id
    val id: String,
    val roomId: String,
    val label: String,
    val assigneeId: String,
    val recurrenceType: String,
    val recurrenceTimes: Int?,
    val videoQuery: String,
    @ElementCollection
    @CollectionTable(
        name = "chore_definition_step",
        joinColumns = [JoinColumn(name = "chore_definition_id")]
    )
    @OrderColumn(name = "step_order")
    @Column(name = "step", columnDefinition = "TEXT")
    val howToSteps: List<String>
)
```

컬럼명은 Spring Boot 기본 네이밍 전략(camelCase → snake_case)이 자동 변환한다 — `roomId` → `room_id`, `assigneeId` → `assignee_id` 등, 별도 `@Column(name=...)` 불필요.

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionJpaRepository.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreDefinitionJpaRepository : JpaRepository<ChoreDefinitionEntity, String>
```

- [ ] **Step 2: Flyway 마이그레이션 작성**

`chore/src/main/resources/db/migration/V2__create_chore_definition_tables.sql`:
```sql
CREATE TABLE chore_definition (
    id CHAR(36) NOT NULL,
    room_id CHAR(36) NOT NULL,
    label VARCHAR(255) NOT NULL,
    assignee_id CHAR(36) NOT NULL,
    recurrence_type VARCHAR(32) NOT NULL,
    recurrence_times INT NULL,
    video_query VARCHAR(255) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE chore_definition_step (
    chore_definition_id CHAR(36) NOT NULL,
    step_order INT NOT NULL,
    step TEXT NOT NULL,
    PRIMARY KEY (chore_definition_id, step_order)
);
```

- [ ] **Step 3: ChoreDefinitionRepositoryAdapter 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreDefinitionRepositoryAdapter::class)
class ChoreDefinitionRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreDefinitionRepositoryAdapter

    private fun choreDefinition(
        recurrence: RecurrencePolicy = Daily,
        steps: List<String> = listOf("헹구기")
    ) = ChoreDefinition(
        id = ChoreDefinitionId.generate(),
        roomId = RoomId.generate(),
        label = "설거지",
        assigneeId = MemberId.generate(),
        recurrence = recurrence,
        howToSteps = steps,
        videoQuery = "설거지 순서 팁"
    )

    @Test
    fun `Daily WeeklyNTimes Monthly 세 종류 모두 저장 후 그대로 읽힌다`() {
        val daily = choreDefinition(recurrence = Daily)
        val weekly = choreDefinition(recurrence = WeeklyNTimes(2))
        val monthly = choreDefinition(recurrence = Monthly)

        adapter.save(daily)
        adapter.save(weekly)
        adapter.save(monthly)

        val found = adapter.findAll().associateBy { it.id }
        found[daily.id]!!.recurrence shouldBe Daily
        found[weekly.id]!!.recurrence shouldBe WeeklyNTimes(2)
        found[monthly.id]!!.recurrence shouldBe Monthly
    }

    @Test
    fun `howToSteps 순서가 저장 순서 그대로 보존된다`() {
        val definition = choreDefinition(steps = listOf("첫번째", "두번째", "세번째"))

        adapter.save(definition)

        val found = adapter.findAll().first { it.id == definition.id }
        found.howToSteps shouldBe listOf("첫번째", "두번째", "세번째")
    }
}
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreDefinitionRepositoryAdapterTest"`
Expected: FAIL — `ChoreDefinitionRepositoryAdapter`가 없어 컴파일 에러.

- [ ] **Step 5: ChoreDefinitionRepositoryAdapter 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreDefinition
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreDefinitionRepository
import sallim.chore.domain.Daily
import sallim.chore.domain.MemberId
import sallim.chore.domain.Monthly
import sallim.chore.domain.RecurrencePolicy
import sallim.chore.domain.RoomId
import sallim.chore.domain.WeeklyNTimes
import java.util.UUID

@Repository
class ChoreDefinitionRepositoryAdapter(
    private val jpaRepository: ChoreDefinitionJpaRepository
) : ChoreDefinitionRepository {

    override fun save(choreDefinition: ChoreDefinition): ChoreDefinition {
        val (recurrenceType, recurrenceTimes) = choreDefinition.recurrence.toColumns()
        jpaRepository.save(
            ChoreDefinitionEntity(
                id = choreDefinition.id.value.toString(),
                roomId = choreDefinition.roomId.value.toString(),
                label = choreDefinition.label,
                assigneeId = choreDefinition.assigneeId.value.toString(),
                recurrenceType = recurrenceType,
                recurrenceTimes = recurrenceTimes,
                videoQuery = choreDefinition.videoQuery,
                howToSteps = choreDefinition.howToSteps
            )
        )
        return choreDefinition
    }

    override fun findAll(): List<ChoreDefinition> =
        jpaRepository.findAll().map { it.toDomain() }

    private fun ChoreDefinitionEntity.toDomain(): ChoreDefinition = ChoreDefinition(
        id = ChoreDefinitionId(UUID.fromString(id)),
        roomId = RoomId(UUID.fromString(roomId)),
        label = label,
        assigneeId = MemberId(UUID.fromString(assigneeId)),
        recurrence = toRecurrencePolicy(recurrenceType, recurrenceTimes),
        howToSteps = howToSteps,
        videoQuery = videoQuery
    )

    private fun RecurrencePolicy.toColumns(): Pair<String, Int?> = when (this) {
        is Daily -> "DAILY" to null
        is WeeklyNTimes -> "WEEKLY_N_TIMES" to times
        is Monthly -> "MONTHLY" to null
    }

    private fun toRecurrencePolicy(type: String, times: Int?): RecurrencePolicy = when (type) {
        "DAILY" -> Daily
        "WEEKLY_N_TIMES" -> WeeklyNTimes(requireNotNull(times) { "WEEKLY_N_TIMES requires recurrenceTimes" })
        "MONTHLY" -> Monthly
        else -> error("unknown recurrence type: $type")
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreDefinitionRepositoryAdapterTest"`
Expected: PASS (2개 테스트 모두 통과)

- [ ] **Step 7: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionEntity.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionJpaRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapter.kt chore/src/main/resources/db/migration/V2__create_chore_definition_tables.sql chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreDefinitionRepositoryAdapterTest.kt
git commit -m "feat: ChoreDefinition JPA 엔티티/어댑터/마이그레이션"
```

---

### Task 5: ChoreInstance 영속성 + 최종 빌드 검증

**Files:**
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceEntity.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceJpaRepository.kt`
- Create: `chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapter.kt`
- Create: `chore/src/main/resources/db/migration/V3__create_chore_instance_table.sql`
- Test: `chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `ChoreInstanceRepository`(Task 2), `ChoreInstance.reconstitute`(Task 2), `ChoreInstance`/`ChoreInstanceId`/`ChoreDefinitionId`/`MemberId`(이전 서브프로젝트), `TestApplication`/`AbstractMySqlIntegrationTest`(Task 3)
- Produces: `class ChoreInstanceRepositoryAdapter(jpaRepository: ChoreInstanceJpaRepository) : ChoreInstanceRepository` — 이 태스크가 계획의 마지막이라 이후 소비자 없음. 대신 `./gradlew build` 전체 검증으로 마무리.

- [ ] **Step 1: ChoreInstanceEntity, ChoreInstanceJpaRepository 작성**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceEntity.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "chore_instance")
class ChoreInstanceEntity(
    @Id
    val id: String,
    val choreDefinitionId: String,
    val scheduledDate: LocalDate,
    val completed: Boolean,
    val completedBy: String?,
    val completedAt: Instant?
)
```

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceJpaRepository.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface ChoreInstanceJpaRepository : JpaRepository<ChoreInstanceEntity, String>
```

- [ ] **Step 2: Flyway 마이그레이션 작성**

`chore/src/main/resources/db/migration/V3__create_chore_instance_table.sql`:
```sql
CREATE TABLE chore_instance (
    id CHAR(36) NOT NULL,
    chore_definition_id CHAR(36) NOT NULL,
    scheduled_date DATE NOT NULL,
    completed BOOLEAN NOT NULL,
    completed_by CHAR(36) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);
```

`completed_at`은 `TIMESTAMP`가 아니라 `DATETIME(6)`을 쓴다 — MySQL의 `TIMESTAMP`는 세션 타임존 기준으로 변환되어 저장/조회되는데, `DATETIME`은 그대로 저장되어 `Instant` 매핑에서 타임존 관련 오차가 생기지 않는다.

- [ ] **Step 3: ChoreInstanceRepositoryAdapter 실패하는 테스트 작성**

`chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.MemberId
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChoreInstanceRepositoryAdapter::class)
class ChoreInstanceRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: ChoreInstanceRepositoryAdapter

    @Test
    fun `미완료 인스턴스를 저장하고 다시 읽으면 값이 같다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))

        adapter.save(instance)
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.id shouldBe instance.id
        found.choreDefinitionId shouldBe instance.choreDefinitionId
        found.scheduledDate shouldBe instance.scheduledDate
        found.completed shouldBe false
        found.completedBy shouldBe null
        found.completedAt shouldBe null
    }

    @Test
    fun `완료된 인스턴스를 저장 후 다시 읽으면 상태는 보존되고 이벤트는 재발행되지 않는다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        val member = MemberId.generate()
        instance.complete(member)

        adapter.save(instance)
        val found = adapter.findById(instance.id)

        found.shouldNotBeNull()
        found.completed shouldBe true
        found.completedBy shouldBe member
        found.completedAt shouldBe instance.completedAt!!.truncatedTo(ChronoUnit.MICROS)
        found.domainEvents.shouldBeEmpty()
    }
}
```

`completedAt` 비교에서 `truncatedTo(ChronoUnit.MICROS)`를 쓰는 이유: `DATETIME(6)`은 마이크로초까지만 저장하는데 `Instant.now()`는 나노초 정밀도라, 그대로 비교하면 나노초 자리 때문에 간헐적으로 실패한다.

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'` 후 `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreInstanceRepositoryAdapterTest"`
Expected: FAIL — `ChoreInstanceRepositoryAdapter`가 없어 컴파일 에러.

- [ ] **Step 5: ChoreInstanceRepositoryAdapter 구현**

`chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapter.kt`:
```kotlin
package sallim.chore.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import sallim.chore.domain.ChoreInstanceId
import sallim.chore.domain.ChoreInstanceRepository
import sallim.chore.domain.MemberId
import java.util.UUID

@Repository
class ChoreInstanceRepositoryAdapter(
    private val jpaRepository: ChoreInstanceJpaRepository
) : ChoreInstanceRepository {

    override fun save(choreInstance: ChoreInstance): ChoreInstance {
        jpaRepository.save(
            ChoreInstanceEntity(
                id = choreInstance.id.value.toString(),
                choreDefinitionId = choreInstance.choreDefinitionId.value.toString(),
                scheduledDate = choreInstance.scheduledDate,
                completed = choreInstance.completed,
                completedBy = choreInstance.completedBy?.value?.toString(),
                completedAt = choreInstance.completedAt
            )
        )
        return choreInstance
    }

    override fun findById(id: ChoreInstanceId): ChoreInstance? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    private fun ChoreInstanceEntity.toDomain(): ChoreInstance = ChoreInstance.reconstitute(
        id = ChoreInstanceId(UUID.fromString(id)),
        choreDefinitionId = ChoreDefinitionId(UUID.fromString(choreDefinitionId)),
        scheduledDate = scheduledDate,
        completed = completed,
        completedBy = completedBy?.let { MemberId(UUID.fromString(it)) },
        completedAt = completedAt
    )
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인 (Docker Desktop 필요)**

Run: `./gradlew :chore:test --tests "sallim.chore.infrastructure.persistence.ChoreInstanceRepositoryAdapterTest"`
Expected: PASS (2개 테스트 모두 통과)

- [ ] **Step 7: chore 모듈 전체 + 루트 빌드 검증**

Run: `./gradlew :chore:test`
Expected: PASS (Task 1~5에서 작성한 모든 테스트 통과 — 도메인 테스트 + 신규 통합 테스트)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — 전체 모듈이 컴파일되고 모든 테스트가 통과한다.

- [ ] **Step 8: Commit**

```bash
git add chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceEntity.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceJpaRepository.kt chore/src/main/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapter.kt chore/src/main/resources/db/migration/V3__create_chore_instance_table.sql chore/src/test/kotlin/sallim/chore/infrastructure/persistence/ChoreInstanceRepositoryAdapterTest.kt
git commit -m "feat: ChoreInstance JPA 엔티티/어댑터/마이그레이션"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- Flyway 마이그레이션 도구 → Task 3/4/5
- QueryDSL 미설치(YAGNI) → Task 1에 의존성 추가하지 않음으로 반영
- 도메인 리포지토리 포트 3종 + `ChoreInstance.reconstitute` → Task 2
- Room/RoomPlacement 1:1 평탄화 매핑, FloorPlan 미저장 → Task 3
- ChoreDefinition 엔티티(`@ElementCollection` howToSteps, recurrence 컬럼 매핑) → Task 4
- ChoreInstance 엔티티 + reconstitute를 통한 이벤트 재발행 방지 검증 → Task 5
- FK 제약 없음 → Task 3/4/5 마이그레이션 SQL에 REFERENCES 없음으로 반영
- `application.yml` 미변경 → 어느 태스크도 건드리지 않음
- `@DataJpaTest`가 `@SpringBootConfiguration`을 못 찾는 문제 → Task 3의 `TestApplication`으로 해결

**Placeholder scan:** 전 단계 실제 코드/SQL/커맨드 포함. TBD/TODO 없음.

**Type consistency:** `RoomRepository`/`ChoreDefinitionRepository`/`ChoreInstanceRepository`/`reconstitute`(Task2) → `RoomEntity`/`RoomRepositoryAdapter`/`TestApplication`/`AbstractMySqlIntegrationTest`(Task3) → `ChoreDefinitionEntity`/`ChoreDefinitionRepositoryAdapter`(Task4, Task3의 공유 테스트 인프라 상속) → `ChoreInstanceEntity`/`ChoreInstanceRepositoryAdapter`(Task5, Task2의 `reconstitute` 사용) 순서로 각 태스크의 Produces가 다음 태스크의 Consumes와 시그니처 일치.
