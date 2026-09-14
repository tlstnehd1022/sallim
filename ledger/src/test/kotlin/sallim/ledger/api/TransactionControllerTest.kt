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
