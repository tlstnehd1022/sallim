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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import sallim.chore.application.ChoreDefinitionService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService
import sallim.chore.domain.MemberId
import sallim.common.domain.Daily

@WebMvcTest(ChoreDefinitionController::class)
@Import(ChoreDefinitionControllerTest.TestConfig::class)
class ChoreDefinitionControllerTest {

    @TestConfiguration
    class TestConfig {
        private val rooms = FakeRoomRepository()
        private val definitions = FakeChoreDefinitionRepository()
        private val instances = FakeChoreInstanceRepository()

        @Bean
        fun roomServiceForSetup(): RoomService = RoomService(rooms, definitions, instances)

        @Bean
        fun choreDefinitionService(): ChoreDefinitionService = ChoreDefinitionService(definitions, rooms, instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var roomServiceForSetup: RoomService

    @Autowired
    lateinit var choreDefinitionService: ChoreDefinitionService

    @Test
    fun `존재하지 않는 방으로 할 일 정의를 생성하면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/chore-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                            RecurrenceDto("DAILY", null), listOf("단계1"), "영상"
                        )
                    )
                )
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `존재하는 방으로 할 일 정의를 생성하면 201을 반환한다`() {
        val room = roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(
            post("/api/chore-definitions").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소", room.id.value, java.util.UUID.randomUUID(),
                            RecurrenceDto("WEEKLY_N_TIMES", 3), listOf("단계1"), "영상"
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.label").value("청소"))
            .andExpect(jsonPath("$.recurrence.type").value("WEEKLY_N_TIMES"))
            .andExpect(jsonPath("$.recurrence.times").value(3))
    }

    @Test
    fun `목록을 조회하면 200을 반환한다`() {
        mockMvc.perform(get("/api/chore-definitions"))
            .andExpect(status().isOk)
    }

    @Test
    fun `수정하면 200과 갱신된 값을 반환한다`() {
        val room = roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)
        val created = choreDefinitionService.create(
            "청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상"
        )

        mockMvc.perform(
            put("/api/chore-definitions/${created.id.value}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소2", room.id.value, java.util.UUID.randomUUID(),
                            RecurrenceDto("MONTHLY", null), listOf("단계2"), "영상2"
                        )
                    )
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.label").value("청소2"))
            .andExpect(jsonPath("$.recurrence.type").value("MONTHLY"))
    }

    @Test
    fun `존재하지 않는 정의를 수정하면 404를 반환한다`() {
        mockMvc.perform(
            put("/api/chore-definitions/${java.util.UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        ChoreDefinitionRequest(
                            "청소", java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                            RecurrenceDto("DAILY", null), listOf("단계1"), "영상"
                        )
                    )
                )
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제하면 204를 반환한다`() {
        val room = roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)
        val created = choreDefinitionService.create("청소", room.id, MemberId.generate(), Daily, listOf("단계1"), "영상")

        mockMvc.perform(delete("/api/chore-definitions/${created.id.value}"))
            .andExpect(status().isNoContent)
    }
}
