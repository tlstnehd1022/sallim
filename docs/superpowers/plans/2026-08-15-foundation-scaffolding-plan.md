# 기반 스캐폴딩 + Household/Member 도메인 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gradle Kotlin DSL 멀티모듈 스캐폴딩(바운디드 컨텍스트 6개 모듈)을 만들고, 그 안에 공유 커널(`common`)과 `Household`/`Member` 도메인 모델을 순수 Kotlin으로 구현한다.

**Architecture:** 모듈러 모놀리스. `bootstrap`(Spring Boot 실행 모듈)이 `common`·`household`·`chore`·`calendar`·`ledger`를 조립한다. `chore`/`calendar`/`ledger`는 경계만 예약된 빈 모듈. `common`과 `household`의 `domain` 코드는 Spring/JPA 의존 없이 순수 Kotlin으로 작성하고, 리포지토리는 포트(인터페이스)만 정의한다.

**Tech Stack:** Kotlin 2.0.20 / JDK 21 (toolchain) / Gradle 8.10.2 (Kotlin DSL, 버전 카탈로그) / Spring Boot 3.3.4 / JUnit5 + Kotest 5.9.1

**Spec:** `docs/superpowers/specs/2026-08-14-foundation-scaffolding-design.md` (및 `sallim-master-spec.md` 4.3장, 8장, 16장)

## Global Constraints

- 모듈러 모놀리스로 시작 — MSA로 미리 쪼개지 않는다 (마스터 스펙 7장)
- 바운디드 컨텍스트 간 직접 참조 금지 — 오직 도메인 이벤트로만 통신 (CLAUDE.md)
- 각 컨텍스트 내부는 hexagonal 레이어링: `domain`/`application`/`infrastructure`/`api` — 이번 계획은 `domain`까지만
- `domain` 패키지는 Spring·JPA 등 프레임워크 의존 금지 — 순수 Kotlin (CLAUDE.md)
- YAGNI — 설계 문서에 명시된 것만 구현. 인증/동기화/JPA/REST/Chore 도메인/React Native는 이번 범위 밖 (설계 문서 "제외" 절)
- 언어: Kotlin (JDK 21) — 설계 문서 결정 사항
- 리포지토리는 포트(인터페이스)만 정의, 구현체는 다음 서브프로젝트

---

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle/
  libs.versions.toml
  wrapper/
    gradle-wrapper.jar
    gradle-wrapper.properties
gradlew
gradlew.bat
bootstrap/
  build.gradle.kts
  src/main/kotlin/sallim/bootstrap/SallimApplication.kt
  src/main/resources/application.yml
  src/test/kotlin/sallim/bootstrap/SallimApplicationTests.kt
common/
  build.gradle.kts
  src/main/kotlin/sallim/common/domain/Identifier.kt
  src/main/kotlin/sallim/common/domain/DomainEvent.kt
  src/main/kotlin/sallim/common/domain/AggregateRoot.kt
  src/test/kotlin/sallim/common/domain/IdentifierTest.kt
  src/test/kotlin/sallim/common/domain/AggregateRootTest.kt
household/
  build.gradle.kts
  src/main/kotlin/sallim/household/domain/HouseholdId.kt
  src/main/kotlin/sallim/household/domain/MemberId.kt
  src/main/kotlin/sallim/household/domain/MemberRole.kt
  src/main/kotlin/sallim/household/domain/Member.kt
  src/main/kotlin/sallim/household/domain/MemberJoinedEvent.kt
  src/main/kotlin/sallim/household/domain/Household.kt
  src/main/kotlin/sallim/household/domain/HouseholdRepository.kt
  src/test/kotlin/sallim/household/domain/MemberTest.kt
  src/test/kotlin/sallim/household/domain/HouseholdTest.kt
chore/build.gradle.kts       (빈 파일)
calendar/build.gradle.kts    (빈 파일)
ledger/build.gradle.kts      (빈 파일)
```

---

### Task 1: Gradle 멀티모듈 스캐폴딩 + bootstrap 부팅

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar` (바이너리, 다운로드)
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `common/build.gradle.kts` (빈 파일)
- Create: `household/build.gradle.kts` (빈 파일, Task 3에서 내용 채움)
- Create: `chore/build.gradle.kts` (빈 파일)
- Create: `calendar/build.gradle.kts` (빈 파일)
- Create: `ledger/build.gradle.kts` (빈 파일)
- Create: `bootstrap/build.gradle.kts`
- Create: `bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`
- Create: `bootstrap/src/main/resources/application.yml`
- Test: `bootstrap/src/test/kotlin/sallim/bootstrap/SallimApplicationTests.kt`

**Interfaces:**
- Produces: 6개 Gradle 모듈(`bootstrap`/`common`/`household`/`chore`/`calendar`/`ledger`), 버전 카탈로그 `libs` accessor(`libs.plugins.kotlin.jvm` 등), 전체 서브프로젝트에 적용되는 JDK 21 toolchain + JUnit Platform 테스트 설정 + Kotest 의존성. 이후 모든 태스크가 이 스캐폴딩 위에서 컴파일된다.

- [ ] **Step 1: Gradle Wrapper 파일 내려받기**

로컬에 Gradle이 설치되어 있지 않으므로, GitHub에 태그된 Gradle 8.10.2 배포본에서 wrapper 3종을 직접 받는다 (최초 `./gradlew` 실행 시 8.10.2 전체 배포본이 자동 캐시됨).

Run:
```bash
mkdir -p gradle/wrapper
curl -sL -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradle/wrapper/gradle-wrapper.jar
curl -sL -o gradlew https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradlew
curl -sL -o gradlew.bat https://raw.githubusercontent.com/gradle/gradle/v8.10.2/gradlew.bat
chmod +x gradlew
```

Expected: `gradle/wrapper/gradle-wrapper.jar`가 약 43KB로 생성되고, `gradlew`/`gradlew.bat`가 텍스트 스크립트로 생성됨.

- [ ] **Step 2: gradle-wrapper.properties 작성**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 3: 버전 카탈로그 작성**

`gradle/libs.versions.toml`:
```toml
[versions]
kotlin = "2.0.20"
spring-boot = "3.3.4"
spring-dependency-management = "1.1.6"
kotest = "5.9.1"

[libraries]
kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }
spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter" }
spring-boot-starter-test = { module = "org.springframework.boot:spring-boot-starter-test" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-spring = { id = "org.jetbrains.kotlin.plugin.spring", version.ref = "kotlin" }
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
```

- [ ] **Step 4: settings.gradle.kts 작성**

```kotlin
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "sallim"

include("bootstrap")
include("common")
include("household")
include("chore")
include("calendar")
include("ledger")
```

`foojay-resolver-convention`은 로컬에 JDK 21이 없어도 toolchain이 자동으로 내려받게 한다 (로컬 JDK는 22).

- [ ] **Step 5: 루트 build.gradle.kts 작성**

```kotlin
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "sallim"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    dependencies {
        "testImplementation"(libs.kotest.runner.junit5)
        "testImplementation"(libs.kotest.assertions.core)
    }
}
```

- [ ] **Step 6: 빈 모듈 4개 생성**

```bash
mkdir -p common household chore calendar ledger
```

`common/build.gradle.kts`, `chore/build.gradle.kts`, `calendar/build.gradle.kts`, `ledger/build.gradle.kts` — 모두 빈 파일로 생성 (루트 `subprojects` 블록의 기본 설정만으로 충분, 바운디드 컨텍스트 경계만 예약).

`household/build.gradle.kts`는 빈 파일로 우선 생성 (Task 3에서 `common` 의존성 추가).

- [ ] **Step 7: bootstrap 모듈 작성**

`bootstrap/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":common"))
    implementation(project(":household"))
    implementation(project(":chore"))
    implementation(project(":calendar"))
    implementation(project(":ledger"))
    implementation(libs.spring.boot.starter)
    implementation(libs.kotlin.reflect)

    testImplementation(libs.spring.boot.starter.test)
}
```

`bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`:
```kotlin
package sallim.bootstrap

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SallimApplication

fun main(args: Array<String>) {
    runApplication<SallimApplication>(*args)
}
```

`bootstrap/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: sallim
```

- [ ] **Step 8: 부팅 스모크 테스트 작성**

`bootstrap/src/test/kotlin/sallim/bootstrap/SallimApplicationTests.kt`:
```kotlin
package sallim.bootstrap

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SallimApplicationTests {

    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 9: 빌드 + 테스트 실행으로 스캐폴딩 검증**

Run: `./gradlew build`

Expected: `BUILD SUCCESSFUL`. 최초 실행 시 Gradle 8.10.2 배포본과 의존성을 내려받으므로 수 분 소요될 수 있음. `bootstrap:test` 태스크에서 `contextLoads`가 통과해야 한다.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle gradlew gradlew.bat common/build.gradle.kts household/build.gradle.kts chore calendar ledger bootstrap
git commit -m "feat: Gradle 멀티모듈 스캐폴딩 + bootstrap 부팅"
```

---

### Task 2: common 모듈 — 공유 커널 (Identifier / DomainEvent / AggregateRoot)

**Files:**
- Test: `common/src/test/kotlin/sallim/common/domain/IdentifierTest.kt`
- Create: `common/src/main/kotlin/sallim/common/domain/Identifier.kt`
- Create: `common/src/main/kotlin/sallim/common/domain/DomainEvent.kt`
- Test: `common/src/test/kotlin/sallim/common/domain/AggregateRootTest.kt`
- Create: `common/src/main/kotlin/sallim/common/domain/AggregateRoot.kt`

**Interfaces:**
- Consumes: 없음 (최하위 공유 커널)
- Produces:
  - `abstract class Identifier<T>(val value: T)` — `equals`/`hashCode`는 실제 런타임 타입 + `value` 기준
  - `interface DomainEvent { val occurredAt: java.time.Instant }`
  - `abstract class AggregateRoot<ID : Identifier<*>> { abstract val id: ID; val domainEvents: List<DomainEvent>; protected fun registerEvent(event: DomainEvent); fun clearEvents() }`
  - Task 3의 `HouseholdId`/`MemberId`/`Household`가 이 세 타입을 상속/구현한다.

- [ ] **Step 1: Identifier 실패하는 테스트 작성**

`common/src/test/kotlin/sallim/common/domain/IdentifierTest.kt`:
```kotlin
package sallim.common.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

private class SampleId(value: UUID) : Identifier<UUID>(value)
private class OtherId(value: UUID) : Identifier<UUID>(value)

class IdentifierTest : FunSpec({
    test("두 Identifier는 같은 타입 + 같은 값이면 동등하다") {
        val uuid = UUID.randomUUID()
        SampleId(uuid) shouldBe SampleId(uuid)
    }

    test("타입이 다르면 값이 같아도 동등하지 않다") {
        val uuid = UUID.randomUUID()
        val sample: Identifier<UUID> = SampleId(uuid)
        val other: Identifier<UUID> = OtherId(uuid)
        (sample == other) shouldBe false
    }
})
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :common:test --tests "sallim.common.domain.IdentifierTest"`
Expected: FAIL — `Identifier`, `DomainEvent` 클래스가 없어 컴파일 에러.

- [ ] **Step 3: Identifier 구현**

`common/src/main/kotlin/sallim/common/domain/Identifier.kt`:
```kotlin
package sallim.common.domain

abstract class Identifier<T>(val value: T) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as Identifier<*>
        return value == other.value
    }

    override fun hashCode(): Int = value?.hashCode() ?: 0

    override fun toString(): String = "${this::class.simpleName}($value)"
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :common:test --tests "sallim.common.domain.IdentifierTest"`
Expected: PASS

- [ ] **Step 5: AggregateRoot 실패하는 테스트 작성**

`common/src/test/kotlin/sallim/common/domain/AggregateRootTest.kt`:
```kotlin
package sallim.common.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import java.time.Instant
import java.util.UUID

private class SampleId(value: UUID) : Identifier<UUID>(value)

private data class SampleEvent(override val occurredAt: Instant = Instant.now()) : DomainEvent

private class SampleAggregate(override val id: SampleId) : AggregateRoot<SampleId>() {
    fun doSomething() {
        registerEvent(SampleEvent())
    }
}

class AggregateRootTest : FunSpec({
    test("이벤트를 등록하면 domainEvents에 쌓인다") {
        val aggregate = SampleAggregate(SampleId(UUID.randomUUID()))
        aggregate.doSomething()
        aggregate.domainEvents shouldHaveSize 1
    }

    test("clearEvents 호출 시 이벤트가 비워진다") {
        val aggregate = SampleAggregate(SampleId(UUID.randomUUID()))
        aggregate.doSomething()
        aggregate.clearEvents()
        aggregate.domainEvents.shouldBeEmpty()
    }
})
```

- [ ] **Step 6: 테스트 실행 → 실패 확인**

Run: `./gradlew :common:test --tests "sallim.common.domain.AggregateRootTest"`
Expected: FAIL — `DomainEvent`, `AggregateRoot` 클래스가 없어 컴파일 에러.

- [ ] **Step 7: DomainEvent, AggregateRoot 구현**

`common/src/main/kotlin/sallim/common/domain/DomainEvent.kt`:
```kotlin
package sallim.common.domain

import java.time.Instant

interface DomainEvent {
    val occurredAt: Instant
}
```

`common/src/main/kotlin/sallim/common/domain/AggregateRoot.kt`:
```kotlin
package sallim.common.domain

abstract class AggregateRoot<ID : Identifier<*>> {
    abstract val id: ID

    private val _domainEvents = mutableListOf<DomainEvent>()
    val domainEvents: List<DomainEvent> get() = _domainEvents.toList()

    protected fun registerEvent(event: DomainEvent) {
        _domainEvents.add(event)
    }

    fun clearEvents() {
        _domainEvents.clear()
    }
}
```

- [ ] **Step 8: 전체 common 테스트 실행 → 통과 확인**

Run: `./gradlew :common:test`
Expected: PASS (4개 테스트 모두 통과)

- [ ] **Step 9: Commit**

```bash
git add common/src
git commit -m "feat: common 모듈에 공유 커널(Identifier/DomainEvent/AggregateRoot) 추가"
```

---

### Task 3: household 모듈 — Household/Member 도메인

**Files:**
- Create: `common` 의존성 추가 — Modify: `household/build.gradle.kts`
- Create: `household/src/main/kotlin/sallim/household/domain/HouseholdId.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/MemberId.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/MemberRole.kt`
- Test: `household/src/test/kotlin/sallim/household/domain/MemberTest.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/Member.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/MemberJoinedEvent.kt`
- Test: `household/src/test/kotlin/sallim/household/domain/HouseholdTest.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/Household.kt`
- Create: `household/src/main/kotlin/sallim/household/domain/HouseholdRepository.kt`

**Interfaces:**
- Consumes: `sallim.common.domain.Identifier`, `sallim.common.domain.DomainEvent`, `sallim.common.domain.AggregateRoot` (Task 2 산출물)
- Produces:
  - `class HouseholdId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): HouseholdId }`
  - `class MemberId(value: UUID) : Identifier<UUID>` — `companion object { fun generate(): MemberId }`
  - `enum class MemberRole { OWNER, MEMBER }`
  - `class Member(val id: MemberId, val householdId: HouseholdId, val displayName: String, val role: MemberRole)`
  - `data class MemberJoinedEvent(val householdId: HouseholdId, val memberId: MemberId, override val occurredAt: Instant) : DomainEvent`
  - `class Household : AggregateRoot<HouseholdId>` — `companion object { fun create(name: String, ownerDisplayName: String): Household }`, `fun addMember(displayName: String, role: MemberRole): Member`, `val memberList: List<Member>`
  - `interface HouseholdRepository { fun save(household: Household): Household; fun findById(id: HouseholdId): Household? }` — 다음 서브프로젝트(영속성)에서 구현

- [ ] **Step 1: household → common 의존성 추가**

`household/build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":common"))
}
```

- [ ] **Step 2: HouseholdId, MemberId, MemberRole 작성**

이 셋은 순수 데이터 타입이라 별도 단위 테스트 없이 바로 작성한다 (Identifier의 equals/hashCode는 Task 2에서 이미 검증됨).

`household/src/main/kotlin/sallim/household/domain/HouseholdId.kt`:
```kotlin
package sallim.household.domain

import sallim.common.domain.Identifier
import java.util.UUID

class HouseholdId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): HouseholdId = HouseholdId(UUID.randomUUID())
    }
}
```

`household/src/main/kotlin/sallim/household/domain/MemberId.kt`:
```kotlin
package sallim.household.domain

import sallim.common.domain.Identifier
import java.util.UUID

class MemberId(value: UUID) : Identifier<UUID>(value) {
    companion object {
        fun generate(): MemberId = MemberId(UUID.randomUUID())
    }
}
```

`household/src/main/kotlin/sallim/household/domain/MemberRole.kt`:
```kotlin
package sallim.household.domain

enum class MemberRole {
    OWNER,
    MEMBER
}
```

- [ ] **Step 3: Member 실패하는 테스트 작성**

`household/src/test/kotlin/sallim/household/domain/MemberTest.kt`:
```kotlin
package sallim.household.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class MemberTest : FunSpec({
    test("displayName이 빈 문자열이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            Member(
                id = MemberId.generate(),
                householdId = HouseholdId.generate(),
                displayName = "  ",
                role = MemberRole.MEMBER
            )
        }
    }
})
```

- [ ] **Step 4: 테스트 실행 → 실패 확인**

Run: `./gradlew :household:test --tests "sallim.household.domain.MemberTest"`
Expected: FAIL — `Member` 클래스가 없어 컴파일 에러.

- [ ] **Step 5: Member 구현**

`household/src/main/kotlin/sallim/household/domain/Member.kt`:
```kotlin
package sallim.household.domain

class Member(
    val id: MemberId,
    val householdId: HouseholdId,
    val displayName: String,
    val role: MemberRole
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}
```

- [ ] **Step 6: 테스트 실행 → 통과 확인**

Run: `./gradlew :household:test --tests "sallim.household.domain.MemberTest"`
Expected: PASS

- [ ] **Step 7: MemberJoinedEvent 작성**

이벤트는 순수 데이터 홀더라 별도 테스트 없이 작성 (Household 테스트에서 발행 여부를 검증한다).

`household/src/main/kotlin/sallim/household/domain/MemberJoinedEvent.kt`:
```kotlin
package sallim.household.domain

import sallim.common.domain.DomainEvent
import java.time.Instant

data class MemberJoinedEvent(
    val householdId: HouseholdId,
    val memberId: MemberId,
    override val occurredAt: Instant = Instant.now()
) : DomainEvent
```

- [ ] **Step 8: Household 실패하는 테스트 작성**

`household/src/test/kotlin/sallim/household/domain/HouseholdTest.kt`:
```kotlin
package sallim.household.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class HouseholdTest : FunSpec({
    test("household 생성 시 최초 멤버는 OWNER다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.memberList shouldHaveSize 1
        household.memberList.first().role shouldBe MemberRole.OWNER
        household.memberList.first().displayName shouldBe "나"
    }

    test("household 생성 시 MemberJoinedEvent가 발행된다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.domainEvents shouldHaveSize 1
        val event = household.domainEvents.first() as MemberJoinedEvent
        event.householdId shouldBe household.id
        event.memberId shouldBe household.memberList.first().id
    }

    test("이름이 빈 문자열이면 생성할 수 없다") {
        shouldThrow<IllegalArgumentException> {
            Household.create(name = "", ownerDisplayName = "나")
        }
    }

    test("addMember로 두 번째 멤버를 추가할 수 있다") {
        val household = Household.create(name = "우리집", ownerDisplayName = "나")

        household.addMember("짝꿍", MemberRole.MEMBER)

        household.memberList shouldHaveSize 2
        household.memberList.last().role shouldBe MemberRole.MEMBER
        household.domainEvents shouldHaveSize 2
    }
})
```

- [ ] **Step 9: 테스트 실행 → 실패 확인**

Run: `./gradlew :household:test --tests "sallim.household.domain.HouseholdTest"`
Expected: FAIL — `Household` 클래스가 없어 컴파일 에러.

- [ ] **Step 10: Household 구현**

`household/src/main/kotlin/sallim/household/domain/Household.kt`:
```kotlin
package sallim.household.domain

import sallim.common.domain.AggregateRoot
import java.time.Instant

class Household private constructor(
    override val id: HouseholdId,
    val name: String,
    val createdAt: Instant,
    private val members: MutableList<Member>
) : AggregateRoot<HouseholdId>() {

    val memberList: List<Member> get() = members.toList()

    fun addMember(displayName: String, role: MemberRole): Member {
        val member = Member(
            id = MemberId.generate(),
            householdId = id,
            displayName = displayName,
            role = role
        )
        members.add(member)
        registerEvent(MemberJoinedEvent(householdId = id, memberId = member.id))
        return member
    }

    companion object {
        fun create(name: String, ownerDisplayName: String): Household {
            require(name.isNotBlank()) { "household name must not be blank" }
            val household = Household(
                id = HouseholdId.generate(),
                name = name,
                createdAt = Instant.now(),
                members = mutableListOf()
            )
            household.addMember(ownerDisplayName, MemberRole.OWNER)
            return household
        }
    }
}
```

이 구조가 "household 생성 시 최초 멤버는 OWNER" / "멤버 없는 household 불가"(설계 문서 54행) 불변식을 강제한다 — `create()`가 유일한 공개 생성 경로이고 항상 OWNER 멤버를 먼저 추가한 뒤 반환하기 때문.

- [ ] **Step 11: 테스트 실행 → 통과 확인**

Run: `./gradlew :household:test --tests "sallim.household.domain.HouseholdTest"`
Expected: PASS

- [ ] **Step 12: HouseholdRepository 포트 작성**

`household/src/main/kotlin/sallim/household/domain/HouseholdRepository.kt`:
```kotlin
package sallim.household.domain

interface HouseholdRepository {
    fun save(household: Household): Household
    fun findById(id: HouseholdId): Household?
}
```

- [ ] **Step 13: household 모듈 전체 + 루트 빌드 검증**

Run: `./gradlew :household:test`
Expected: PASS (5개 테스트 모두 통과)

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — 전체 모듈(6개)이 컴파일되고 모든 테스트가 통과한다.

- [ ] **Step 14: Commit**

```bash
git add household
git commit -m "feat: Household/Member 도메인 모델 + 단위 테스트"
```

---

## Self-Review

**Spec coverage** (설계 문서 대비):
- Gradle 멀티모듈 스캐폴딩, 바운디드 컨텍스트 경계 확정 → Task 1
- `common`: `AggregateRoot`, `DomainEvent`, `Identifier<T>` → Task 2
- `household`: `Household`(id/name/createdAt), `Member`(id/householdId/displayName/role), `HouseholdId`/`MemberId` 타입 세이프 ID, `MemberJoinedEvent`, 리포지토리 포트만 → Task 3
- 단위 테스트(JUnit5+Kotest), 도메인 불변식(최초 멤버 OWNER, 멤버 없는 household 불가) → Task 3
- 제외 항목(인증/동기화/JPA/REST/Chore 도메인/RN 스캐폴딩) → 계획에 포함하지 않음, Global Constraints에 명시

**Placeholder scan:** 전 단계 실제 코드/커맨드 포함. TBD/TODO 없음.

**Type consistency:** `HouseholdId`/`MemberId`/`MemberRole`/`Member`/`MemberJoinedEvent`/`Household`/`HouseholdRepository`의 시그니처가 Task 3 전체와 File Structure의 Interfaces 절에서 동일하게 사용됨. `Identifier`/`DomainEvent`/`AggregateRoot`도 Task 2→3 간 일관.

## Implementation Note (added after execution)

Task 1's root `build.gradle.kts` step as originally written above (`subprojects { dependencies { "testImplementation"(libs.kotest.runner.junit5) ... } } }` using the `libs` accessor directly inside a `subprojects {}` closure) does not actually work — it fails at configuration time with `Extension with name 'libs' does not exist`. Gradle's type-safe version catalog accessor is only resolvable in the literal top-level scope of the script that owns it; inside `subprojects {}`, the closure runs against each subproject's own `Project` instance where the `libs` extension isn't registered. The implementation correctly worked around this by resolving `libs.kotest.runner.junit5` / `libs.kotest.assertions.core` into local `val`s at the root script's own top-level scope first, then referencing those captured vals inside `subprojects { }`. Future plans reusing this root-build-script pattern should write it this way from the start.
