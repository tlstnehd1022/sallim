package sallim.chore.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreInstanceService
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.domain.ChoreDefinitionId
import sallim.chore.domain.ChoreInstance
import java.time.LocalDate
import java.util.UUID

@WebMvcTest(ChoreInstanceController::class)
@Import(ChoreInstanceControllerTest.TestConfig::class)
class ChoreInstanceControllerTest {

    @TestConfiguration
    class TestConfig {
        val instances = FakeChoreInstanceRepository()

        @Bean
        fun choreInstanceService(): ChoreInstanceService = ChoreInstanceService(instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var testConfig: TestConfig

    @Test
    fun `date 파라미터 없이 조회하면 400을 반환한다`() {
        mockMvc.perform(get("/api/chore-instances")).andExpect(status().isBadRequest)
    }

    @Test
    fun `date 파라미터 없이 조회하면 에러 바디도 통일된 형식으로 반환한다`() {
        mockMvc.perform(get("/api/chore-instances"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `날짜로 조회하면 200과 목록을 반환한다`() {
        testConfig.instances.save(ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20)))

        mockMvc.perform(get("/api/chore-instances").param("date", "2026-08-20"))
            .andExpect(status().isOk)
    }

    @Test
    fun `완료 처리하면 200을 반환한다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        testConfig.instances.save(instance)

        mockMvc.perform(
            post("/api/chore-instances/${instance.id.value}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        ).andExpect(status().isOk)
    }

    @Test
    fun `존재하지 않는 인스턴스를 완료 처리하면 404를 반환한다`() {
        mockMvc.perform(
            post("/api/chore-instances/${UUID.randomUUID()}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `이미 완료된 인스턴스를 다시 완료 처리하면 409를 반환한다`() {
        val instance = ChoreInstance.schedule(ChoreDefinitionId.generate(), LocalDate.of(2026, 8, 20))
        testConfig.instances.save(instance)
        mockMvc.perform(
            post("/api/chore-instances/${instance.id.value}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        )

        mockMvc.perform(
            post("/api/chore-instances/${instance.id.value}/complete").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CompleteRequest(UUID.randomUUID())))
        ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").exists())
    }
}
