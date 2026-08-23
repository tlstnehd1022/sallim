package sallim.chore.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
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
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService

@WebMvcTest(RoomController::class)
@Import(RoomControllerTest.TestConfig::class, ApiExceptionHandler::class)
class RoomControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    class TestConfig {
        @Bean
        fun roomService(): RoomService =
            RoomService(FakeRoomRepository(), FakeChoreDefinitionRepository(), FakeChoreInstanceRepository())
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var roomService: RoomService

    @Test
    fun `방을 생성하고 목록에서 조회한다`() {
        mockMvc.perform(
            post("/api/rooms").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RoomRequest("거실", 26, 38, 74, 50, 1)))
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("거실"))

        mockMvc.perform(get("/api/rooms"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }

    @Test
    fun `존재하지 않는 방을 수정하면 404와 에러 바디를 반환한다`() {
        mockMvc.perform(
            put("/api/rooms/${java.util.UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RoomRequest("거실", 0, 0, 10, 10, 1)))
        ).andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `방을 삭제하면 204를 반환한다`() {
        val room = roomService.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(delete("/api/rooms/${room.id.value}"))
            .andExpect(status().isNoContent)
    }
}
