# Ledger 영속성 + API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `Transaction` 도메인 모델에 JPA 영속성과 REST API(조회/CRUD)를 추가한다.

**Architecture:** calendar-persistence-api가 확립한 hexagonal 어댑터 패턴(domain 포트 → JPA 어댑터 → application 서비스 → REST 컨트롤러)을 그대로 따르되, `Transaction`이 반복 규칙 변환도 없는 가장 단순한 엔티티라 영속성+API를 한 서브프로젝트(3개 태스크)로 묶는다.

**Tech Stack:** Kotlin, Spring Boot 3.x, Spring Data JPA, Flyway, MySQL(Testcontainers), Kotest.

**Spec:** `docs/superpowers/specs/2026-09-11-ledger-persistence-api-design.md`

## Global Constraints

- 영속성+API를 한 서브프로젝트(한 브랜치)로 묶는다.
- 목록 조회 API(`GET /api/transactions`)의 `from`/`to`는 필수, `memberId`/`category`는 선택 필터다.
- `memberId`/`category` 필터는 DB 쿼리가 아니라 애플리케이션 계층에서 `.filter { }`로 인메모리 처리한다 — `TransactionJpaRepository`에는 `findByOccurredAtBetween` 하나만 둔다.
- 테이블명은 `transactions`(복수형)다.
- Flyway 마이그레이션은 `V7`부터 시작한다 — 확인 결과 전체 모듈 공유 시퀀스의 현재 최고 버전은 calendar의 V6(`calendar/src/main/resources/db/migration/V6__create_calendar_event_table.sql`)이고, `bootstrap`이 `classpath:db/migration`을 전체 모듈에 걸쳐 하나로 스캔하므로 그 다음 번호를 반드시 써야 한다.
- 예외 핸들러는 처음부터 `LedgerApiExceptionHandler`로 이름 짓고 `@RestControllerAdvice(basePackages = ["sallim.ledger"])`로 스코프를 지정한다 — calendar-persistence-api 최종 리뷰에서 잡힌 실제 버그(같은 클래스 단순명이 다른 컨텍스트와 Spring 빈 이름 충돌 → `bootstrap` 부팅 실패)를 재현하지 않기 위함. **이 규칙은 절대 `ApiExceptionHandler`라는 단순명을 쓰지 않는 것으로 지켜진다.**
- `ledger` 모듈의 `@DataJpaTest`/`@WebMvcTest`가 부팅하려면 `@SpringBootConfiguration`이 필요하다 — calendar 서브프로젝트에서 이걸 빠뜨렸다가 뒤늦게 발견한 적이 있으므로, Task 1에서 `ledger/src/test/kotlin/sallim/ledger/TestApplication.kt`를 처음부터 만든다(`chore`/`calendar`의 것과 동일한 패턴).
- `bootstrap/src/main/kotlin/sallim/bootstrap/SallimApplication.kt`는 이미 `@EnableJpaRepositories(basePackages=["sallim"])` + `@EntityScan(basePackages=["sallim"])`로 전체 `sallim` 패키지를 스캔하므로 이번 서브프로젝트에서 **수정하지 않는다**.
- `Transaction`은 `RecurrencePolicy` 변환이 필요 없다 — chore/calendar 어댑터보다 단순한 컬럼 매핑만 한다.

---

### Task 1: Ledger 모듈 Gradle 설정 + 영속성 계층

**Files:**
- Modify: `ledger/build.gradle.kts`
- Create: `ledger/src/main/kotlin/sallim/ledger/domain/TransactionRepository.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/infrastructure/persistence/TransactionEntity.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/infrastructure/persistence/TransactionJpaRepository.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/infrastructure/persistence/TransactionRepositoryAdapter.kt`
- Create: `ledger/src/main/resources/db/migration/V7__create_transactions_table.sql`
- Test: `ledger/src/test/kotlin/sallim/ledger/TestApplication.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/infrastructure/persistence/TransactionRepositoryAdapterTest.kt`

**Interfaces:**
- Consumes: `sallim.ledger.domain.{Transaction,TransactionId,MemberId}` (기존, `ledger-domain` 서브프로젝트에서 완성됨)
- Produces: `sallim.ledger.domain.TransactionRepository`(도메인 포트) — Task 2가 이걸 주입받는다. 시그니처: `save(transaction: Transaction): Transaction`, `findById(id: TransactionId): Transaction?`, `findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction>`, `deleteById(id: TransactionId)`.

- [ ] **Step 1: `ledger/build.gradle.kts`를 calendar의 것과 동일한 구조로 채운다**

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
    // chore/calendar의 build.gradle.kts와 동일한 이유(로컬 Docker 엔진과 testcontainers 1.19.8 충돌) —
    // 이 모듈도 Testcontainers MySQL로 TransactionRepositoryAdapterTest를 띄우므로 동일 오버라이드가 필요하다.
    dependencies {
        dependencySet("org.testcontainers:${libs.versions.testcontainers.get()}") {
            entry("testcontainers")
            entry("junit-jupiter")
            entry("mysql")
            entry("jdbc")
            entry("database-commons")
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.kotlin.reflect)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.module.kotlin)
    runtimeOnly(libs.flyway.core)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector.j)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mysql)
}
```

- [ ] **Step 2: 도메인 포트를 작성한다 — `sallim/ledger/domain/TransactionRepository.kt`**

```kotlin
package sallim.ledger.domain

import java.time.LocalDateTime

interface TransactionRepository {
    fun save(transaction: Transaction): Transaction
    fun findById(id: TransactionId): Transaction?
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction>
    fun deleteById(id: TransactionId)
}
```

- [ ] **Step 3: `TestApplication`을 작성한다 — `sallim/ledger/TestApplication.kt`**

```kotlin
package sallim.ledger

import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class TestApplication
```

`chore`/`calendar`의 `TestApplication`과 동일한 이유 — `ledger` 모듈에 아직 `@SpringBootApplication`/`@SpringBootConfiguration`이 없어 이게 없으면 이번 태스크의 `@DataJpaTest`도, 다음 태스크의 `@WebMvcTest`도 부팅하지 못한다.

- [ ] **Step 4: `AbstractMySqlIntegrationTest`를 chore/calendar의 것과 동일하게 복제한다 — `sallim/ledger/infrastructure/persistence/AbstractMySqlIntegrationTest.kt`**

```kotlin
package sallim.ledger.infrastructure.persistence

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

- [ ] **Step 5: 실패하는 테스트를 작성한다 — `sallim/ledger/infrastructure/persistence/TransactionRepositoryAdapterTest.kt`**

```kotlin
package sallim.ledger.infrastructure.persistence

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TransactionRepositoryAdapter::class)
class TransactionRepositoryAdapterTest : AbstractMySqlIntegrationTest() {

    @Autowired
    lateinit var adapter: TransactionRepositoryAdapter

    @Autowired
    lateinit var em: TestEntityManager

    @Test
    fun `저장한 거래를 다시 읽으면 값이 같다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 15000L, "식비", "장보기",
            LocalDateTime.of(2026, 9, 10, 14, 30)
        )

        adapter.save(transaction)
        em.flush()
        em.clear()

        val found = adapter.findById(transaction.id)
        found.shouldNotBeNull()
        found.memberId shouldBe transaction.memberId
        found.amount shouldBe 15000L
        found.category shouldBe "식비"
        found.memo shouldBe "장보기"
        found.occurredAt shouldBe LocalDateTime.of(2026, 9, 10, 14, 30)
    }

    @Test
    fun `memo가 null인 거래를 저장하고 조회하면 null로 돌아온다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 5000L, "생활용품", null,
            LocalDateTime.of(2026, 9, 10, 10, 0)
        )

        adapter.save(transaction)
        em.flush()
        em.clear()

        val found = adapter.findById(transaction.id)
        found.shouldNotBeNull()
        found.memo shouldBe null
    }

    @Test
    fun `존재하지 않는 id로 조회하면 null을 반환한다`() {
        adapter.findById(TransactionId.generate()).shouldBeNull()
    }

    @Test
    fun `findByOccurredAtBetween은 경계값을 포함한다`() {
        val before = Transaction(
            TransactionId.generate(), MemberId.generate(), 1000L, "식비", null,
            LocalDateTime.of(2026, 8, 31, 23, 59)
        )
        val fromBoundary = Transaction(
            TransactionId.generate(), MemberId.generate(), 2000L, "식비", null,
            LocalDateTime.of(2026, 9, 1, 0, 0)
        )
        val toBoundary = Transaction(
            TransactionId.generate(), MemberId.generate(), 3000L, "식비", null,
            LocalDateTime.of(2026, 9, 30, 23, 59)
        )
        val after = Transaction(
            TransactionId.generate(), MemberId.generate(), 4000L, "식비", null,
            LocalDateTime.of(2026, 10, 1, 0, 0)
        )
        adapter.save(before)
        adapter.save(fromBoundary)
        adapter.save(toBoundary)
        adapter.save(after)
        em.flush()
        em.clear()

        val found = adapter.findByOccurredAtBetween(
            LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59)
        )

        found shouldHaveSize 2
        found.map { it.amount }.toSet() shouldBe setOf(2000L, 3000L)
    }

    @Test
    fun `삭제하면 findById 결과가 null이 된다`() {
        val transaction = Transaction(
            TransactionId.generate(), MemberId.generate(), 1000L, "식비", null,
            LocalDateTime.of(2026, 9, 10, 14, 0)
        )
        adapter.save(transaction)
        em.flush()
        em.clear()

        adapter.deleteById(transaction.id)
        em.flush()
        em.clear()

        adapter.findById(transaction.id).shouldBeNull()
    }
}
```

- [ ] **Step 6: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.infrastructure.persistence.TransactionRepositoryAdapterTest"`
Expected: FAIL — `TransactionEntity`/`TransactionJpaRepository`/`TransactionRepositoryAdapter`가 없어 컴파일 에러.

- [ ] **Step 7: `TransactionEntity`를 작성한다 — `sallim/ledger/infrastructure/persistence/TransactionEntity.kt`**

```kotlin
package sallim.ledger.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

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

- [ ] **Step 8: `TransactionJpaRepository`를 작성한다 — `sallim/ledger/infrastructure/persistence/TransactionJpaRepository.kt`**

```kotlin
package sallim.ledger.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface TransactionJpaRepository : JpaRepository<TransactionEntity, String> {
    fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<TransactionEntity>
}
```

- [ ] **Step 9: `TransactionRepositoryAdapter`를 작성한다 — `sallim/ledger/infrastructure/persistence/TransactionRepositoryAdapter.kt`**

```kotlin
package sallim.ledger.infrastructure.persistence

import org.springframework.stereotype.Repository
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime
import java.util.UUID

@Repository
class TransactionRepositoryAdapter(
    private val jpaRepository: TransactionJpaRepository
) : TransactionRepository {

    override fun save(transaction: Transaction): Transaction {
        jpaRepository.save(
            TransactionEntity(
                id = transaction.id.value.toString(),
                memberId = transaction.memberId.value.toString(),
                amount = transaction.amount,
                category = transaction.category,
                memo = transaction.memo,
                occurredAt = transaction.occurredAt
            )
        )
        return transaction
    }

    override fun findById(id: TransactionId): Transaction? =
        jpaRepository.findById(id.value.toString()).map { it.toDomain() }.orElse(null)

    override fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction> =
        jpaRepository.findByOccurredAtBetween(from, to).map { it.toDomain() }

    override fun deleteById(id: TransactionId) {
        jpaRepository.deleteById(id.value.toString())
    }

    private fun TransactionEntity.toDomain(): Transaction = Transaction(
        id = TransactionId(UUID.fromString(id)),
        memberId = MemberId(UUID.fromString(memberId)),
        amount = amount,
        category = category,
        memo = memo,
        occurredAt = occurredAt
    )
}
```

- [ ] **Step 10: Flyway 마이그레이션을 작성한다 — `ledger/src/main/resources/db/migration/V7__create_transactions_table.sql`**

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

- [ ] **Step 11: 테스트 실행 — 통과 확인 (Docker 필요)**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.infrastructure.persistence.TransactionRepositoryAdapterTest"`
Expected: PASS (Docker가 없는 환경이면 Testcontainers가 컨테이너를 못 띄워 이 스텝은 실패한다 — Docker 미설치 환경이면 `./gradlew :ledger:compileTestKotlin`으로 컴파일 성공만 확인하고, 실제 통과 여부는 Docker 있는 환경에서 검증)

- [ ] **Step 12: Commit**

```bash
git add ledger/build.gradle.kts ledger/src/main/kotlin/sallim/ledger/domain/TransactionRepository.kt ledger/src/main/kotlin/sallim/ledger/infrastructure ledger/src/main/resources/db/migration ledger/src/test/kotlin/sallim/ledger/TestApplication.kt ledger/src/test/kotlin/sallim/ledger/infrastructure
git commit -m "feat: Transaction JPA 영속성 계층 추가"
```

---

### Task 2: Application 계층 (`TransactionService`)

**Files:**
- Create: `ledger/src/main/kotlin/sallim/ledger/application/NotFoundException.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/application/TransactionService.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/application/FakeTransactionRepository.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/application/TransactionServiceTest.kt`

**Interfaces:**
- Consumes: `sallim.ledger.domain.TransactionRepository`(Task 1에서 만든 포트, 시그니처는 Task 1의 "Produces" 참고), `Transaction`, `TransactionId`, `MemberId`
- Produces: `sallim.ledger.application.TransactionService` — `list(from: LocalDateTime, to: LocalDateTime, memberId: MemberId?, category: String?): List<Transaction>`, `create(memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime): Transaction`, `update(id: TransactionId, memberId: MemberId, amount: Long, category: String, memo: String?, occurredAt: LocalDateTime): Transaction`, `delete(id: TransactionId)`. `sallim.ledger.application.NotFoundException(message: String) : RuntimeException` — Task 3의 API 예외 핸들러가 이걸 잡는다.

- [ ] **Step 1: `FakeTransactionRepository`를 작성한다 — `sallim/ledger/application/FakeTransactionRepository.kt`**

```kotlin
package sallim.ledger.application

import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime

class FakeTransactionRepository : TransactionRepository {
    private val store = mutableMapOf<TransactionId, Transaction>()

    override fun save(transaction: Transaction): Transaction {
        store[transaction.id] = transaction
        return transaction
    }

    override fun findById(id: TransactionId): Transaction? = store[id]

    override fun findByOccurredAtBetween(from: LocalDateTime, to: LocalDateTime): List<Transaction> =
        store.values.filter { !it.occurredAt.isBefore(from) && !it.occurredAt.isAfter(to) }

    override fun deleteById(id: TransactionId) {
        store.remove(id)
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 작성한다 — `sallim/ledger/application/TransactionServiceTest.kt`**

```kotlin
package sallim.ledger.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.TransactionId
import java.time.LocalDate
import java.time.LocalDateTime

class TransactionServiceTest : FunSpec({
    val septFrom = LocalDateTime.of(2026, 9, 1, 0, 0)
    val septTo = LocalDateTime.of(2026, 9, 30, 23, 59)

    test("생성한 거래를 조회 범위 안에서 조회할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()

        val created = service.create(member, 15000L, "식비", "장보기", LocalDateTime.of(2026, 9, 10, 14, 0))

        val result = service.list(septFrom, septTo, null, null)
        result shouldHaveSize 1
        result.first().id shouldBe created.id
    }

    test("조회 범위 밖의 거래는 나오지 않는다") {
        val service = TransactionService(FakeTransactionRepository())
        service.create(MemberId.generate(), 15000L, "식비", null, LocalDateTime.of(2026, 10, 1, 0, 0))

        service.list(septFrom, septTo, null, null) shouldHaveSize 0
    }

    test("memberId로 필터링할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member1 = MemberId.generate()
        val member2 = MemberId.generate()
        service.create(member1, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
        service.create(member2, 2000L, "식비", null, LocalDateTime.of(2026, 9, 11, 10, 0))

        val result = service.list(septFrom, septTo, member1, null)
        result shouldHaveSize 1
        result.first().memberId shouldBe member1
    }

    test("category로 필터링할 수 있다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()
        service.create(member, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
        service.create(member, 2000L, "생활용품", null, LocalDateTime.of(2026, 9, 11, 10, 0))

        val result = service.list(septFrom, septTo, null, "생활용품")
        result shouldHaveSize 1
        result.first().category shouldBe "생활용품"
    }

    test("from이 to보다 늦으면 IllegalArgumentException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<IllegalArgumentException> {
            service.list(septTo, septFrom, null, null)
        }
    }

    test("to가 비현실적으로 먼 미래면 IllegalArgumentException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<IllegalArgumentException> {
            service.list(septFrom, LocalDate.MAX.atStartOfDay(), null, null)
        }
    }

    test("존재하는 거래를 수정하면 값이 갱신된다") {
        val service = TransactionService(FakeTransactionRepository())
        val member = MemberId.generate()
        val created = service.create(member, 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))

        val updated = service.update(created.id, member, 2000L, "생활용품", "메모", LocalDateTime.of(2026, 9, 11, 11, 0))

        updated.amount shouldBe 2000L
        updated.category shouldBe "생활용품"
        updated.memo shouldBe "메모"
    }

    test("존재하지 않는 거래를 수정하면 NotFoundException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<NotFoundException> {
            service.update(TransactionId.generate(), MemberId.generate(), 1000L, "식비", null, septFrom)
        }
    }

    test("존재하는 거래를 삭제하면 이후 조회에서 사라진다") {
        val service = TransactionService(FakeTransactionRepository())
        val created = service.create(MemberId.generate(), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))

        service.delete(created.id)

        service.list(septFrom, septTo, null, null) shouldHaveSize 0
    }

    test("존재하지 않는 거래를 삭제하면 NotFoundException") {
        val service = TransactionService(FakeTransactionRepository())

        shouldThrow<NotFoundException> {
            service.delete(TransactionId.generate())
        }
    }
})
```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.application.TransactionServiceTest"`
Expected: FAIL — `TransactionService`/`NotFoundException`이 없어 컴파일 에러.

- [ ] **Step 4: `NotFoundException`을 작성한다 — `sallim/ledger/application/NotFoundException.kt`**

```kotlin
package sallim.ledger.application

class NotFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 5: `TransactionService`를 작성한다 — `sallim/ledger/application/TransactionService.kt`**

```kotlin
package sallim.ledger.application

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import sallim.ledger.domain.TransactionRepository
import java.time.LocalDateTime

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

- [ ] **Step 6: 테스트 실행 — 통과 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.application.TransactionServiceTest"`
Expected: PASS (Docker 불필요 — Fake 리포지토리만 사용)

- [ ] **Step 7: Commit**

```bash
git add ledger/src/main/kotlin/sallim/ledger/application ledger/src/test/kotlin/sallim/ledger/application
git commit -m "feat: TransactionService 애플리케이션 계층 추가"
```

---

### Task 3: API 계층 (`TransactionController`)

**Files:**
- Create: `ledger/src/main/kotlin/sallim/ledger/api/TransactionController.kt`
- Create: `ledger/src/main/kotlin/sallim/ledger/api/LedgerApiExceptionHandler.kt`
- Test: `ledger/src/test/kotlin/sallim/ledger/api/TransactionControllerTest.kt`

**Interfaces:**
- Consumes: `sallim.ledger.application.TransactionService`/`NotFoundException`(Task 2, 시그니처는 Task 2의 "Produces" 참고), `sallim.ledger.domain.{Transaction,TransactionId,MemberId}`
- Produces: `GET/POST/PUT/DELETE /api/transactions` — 이 서브프로젝트의 최종 산출물, 이후 태스크 없음.

- [ ] **Step 1: 실패하는 테스트를 작성한다 — `sallim/ledger/api/TransactionControllerTest.kt`**

```kotlin
package sallim.ledger.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.ledger.application.FakeTransactionRepository
import sallim.ledger.application.TransactionService
import sallim.ledger.domain.MemberId
import java.time.LocalDateTime
import java.util.UUID

@WebMvcTest(TransactionController::class)
@Import(TransactionControllerTest.TestConfig::class)
// ponytail: TestConfig의 FakeTransactionRepository가 싱글턴으로 캐싱된 Spring 컨텍스트에 걸쳐 공유돼
// 테스트 간 상태가 새는 것을 막기 위함 — calendar-persistence-api에서 같은 문제를 겪었던 것과 동일한 원인.
// @BeforeEach로 store를 비우는 게 더 가볍지만, 지금 규모(8개 테스트)에서는 비용 차이가 미미하다.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionControllerTest {

    @TestConfiguration
    class TestConfig {
        private val repository = FakeTransactionRepository()

        @Bean
        fun transactionService(): TransactionService = TransactionService(repository)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var transactionService: TransactionService

    @Test
    fun `거래를 생성하면 201을 반환한다`() {
        mockMvc.perform(
            post("/api/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        TransactionRequest(UUID.randomUUID(), 15000L, "식비", "장보기", LocalDateTime.of(2026, 9, 10, 14, 0))
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.amount").value(15000))
            .andExpect(jsonPath("$.category").value("식비"))
    }

    @Test
    fun `기간으로 조회하면 200과 목록을 반환한다`() {
        val member = UUID.randomUUID()
        transactionService.create(MemberId(member), 15000L, "식비", null, LocalDateTime.of(2026, 9, 10, 14, 0))

        mockMvc.perform(
            get("/api/transactions").param("from", "2026-09-01T00:00:00").param("to", "2026-09-30T23:59:59")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].amount").value(15000))
    }

    @Test
    fun `memberId로 필터링해서 조회할 수 있다`() {
        val member1 = UUID.randomUUID()
        val member2 = UUID.randomUUID()
        transactionService.create(MemberId(member1), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
        transactionService.create(MemberId(member2), 2000L, "식비", null, LocalDateTime.of(2026, 9, 11, 10, 0))

        mockMvc.perform(
            get("/api/transactions").param("from", "2026-09-01T00:00:00").param("to", "2026-09-30T23:59:59")
                .param("memberId", member1.toString())
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].memberId").value(member1.toString()))
    }

    @Test
    fun `from 파라미터가 없으면 400`() {
        mockMvc.perform(get("/api/transactions").param("to", "2026-09-30T23:59:59"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `from이 to보다 늦으면 400`() {
        mockMvc.perform(
            get("/api/transactions").param("from", "2026-09-30T23:59:59").param("to", "2026-09-01T00:00:00")
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `수정하면 200과 갱신된 값을 반환한다`() {
        val member = UUID.randomUUID()
        val created = transactionService.create(MemberId(member), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))

        mockMvc.perform(
            put("/api/transactions/${created.id.value}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        TransactionRequest(member, 2000L, "생활용품", "메모", LocalDateTime.of(2026, 9, 11, 11, 0))
                    )
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(2000))
            .andExpect(jsonPath("$.category").value("생활용품"))
    }

    @Test
    fun `존재하지 않는 거래를 수정하면 404를 반환한다`() {
        mockMvc.perform(
            put("/api/transactions/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        TransactionRequest(UUID.randomUUID(), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0))
                    )
                )
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제하면 204를 반환한다`() {
        val created = transactionService.create(
            MemberId(UUID.randomUUID()), 1000L, "식비", null, LocalDateTime.of(2026, 9, 10, 10, 0)
        )

        mockMvc.perform(delete("/api/transactions/${created.id.value}"))
            .andExpect(status().isNoContent)
    }
}
```

- [ ] **Step 2: 테스트 실행 — 컴파일 실패 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.api.TransactionControllerTest"`
Expected: FAIL — `TransactionController`/`TransactionRequest` 등이 없어 컴파일 에러.

- [ ] **Step 3: `LedgerApiExceptionHandler`를 작성한다 — `sallim/ledger/api/LedgerApiExceptionHandler.kt`**

```kotlin
package sallim.ledger.api

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import sallim.ledger.application.NotFoundException

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

**주의: 이 클래스의 단순명은 반드시 `LedgerApiExceptionHandler`여야 한다 — `ApiExceptionHandler`로 지으면 `chore`/`calendar`의 동명 클래스와 Spring 빈 이름이 충돌해 `bootstrap`이 부팅에 실패한다(calendar-persistence-api 최종 리뷰에서 실제로 발생했던 버그).**

- [ ] **Step 4: `TransactionController`를 작성한다 — `sallim/ledger/api/TransactionController.kt`**

```kotlin
package sallim.ledger.api

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import sallim.ledger.application.TransactionService
import sallim.ledger.domain.MemberId
import sallim.ledger.domain.Transaction
import sallim.ledger.domain.TransactionId
import java.time.LocalDateTime
import java.util.UUID

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

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test --tests "sallim.ledger.api.TransactionControllerTest"`
Expected: PASS (Docker 불필요 — `@WebMvcTest` + Fake 서비스)

- [ ] **Step 6: 전체 ledger 모듈 테스트 + bootstrap 컴파일 확인**

Run: `export JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot' && ./gradlew :ledger:test :bootstrap:compileKotlin`
Expected: PASS (persistence 테스트는 Docker 없으면 실패할 수 있음 — Docker 없는 환경이면 `--tests` 필터로 Docker 불필요 테스트만 재확인). **`:bootstrap:test --tests "sallim.bootstrap.BeanDefinitionScanTest"`도 반드시 실행한다** — 이건 Docker 없이도 실행되는, calendar-persistence-api 최종 리뷰가 추가한 회귀 방지 테스트로, `LedgerApiExceptionHandler` 네이밍이 실제로 안전한지 이 태스크에서 직접 검증할 수 있는 유일한 수단이다.

- [ ] **Step 7: Commit**

```bash
git add ledger/src/main/kotlin/sallim/ledger/api ledger/src/test/kotlin/sallim/ledger/api
git commit -m "feat: Transaction REST API 추가"
```

## 다음 단계

이 플랜 완료 후 `superpowers:finishing-a-development-branch`로 머지/푸시.
