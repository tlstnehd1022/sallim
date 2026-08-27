package sallim.chore.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreStatsService
import sallim.chore.application.FakeChoreCompletionStatsQuery
import sallim.chore.domain.MemberCompletionCount
import sallim.chore.domain.MemberId

@WebMvcTest(ChoreStatsController::class)
@Import(ChoreStatsControllerTest.TestConfig::class)
class ChoreStatsControllerTest {

    @TestConfiguration
    class TestConfig {
        val query = FakeChoreCompletionStatsQuery()

        @Bean
        fun choreStatsService(): ChoreStatsService = ChoreStatsService(query)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var testConfig: TestConfig

    @Test
    fun `기간과 함께 요청하면 멤버별 개수를 반환한다`() {
        val member = MemberId.generate()
        testConfig.query.result = listOf(MemberCompletionCount(member, 5L))

        mockMvc.perform(get("/api/chore-stats").param("from", "2026-08-01").param("to", "2026-08-31"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].memberId").value(member.value.toString()))
            .andExpect(jsonPath("$[0].count").value(5))
    }

    @Test
    fun `from 파라미터가 없으면 400`() {
        mockMvc.perform(get("/api/chore-stats").param("to", "2026-08-31"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `from이 to보다 늦으면 400`() {
        mockMvc.perform(get("/api/chore-stats").param("from", "2026-08-31").param("to", "2026-08-01"))
            .andExpect(status().isBadRequest)
    }
}
