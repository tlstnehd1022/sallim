package sallim.chore.api

import org.hamcrest.Matchers.hasSize
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
import sallim.chore.application.CleanlinessService
import sallim.chore.application.FakeChoreDefinitionRepository
import sallim.chore.application.FakeChoreInstanceRepository
import sallim.chore.application.FakeRoomRepository
import sallim.chore.application.RoomService

@WebMvcTest(CleanlinessController::class)
@Import(CleanlinessControllerTest.TestConfig::class)
class CleanlinessControllerTest {

    @TestConfiguration
    class TestConfig {
        private val rooms = FakeRoomRepository()
        private val definitions = FakeChoreDefinitionRepository()
        private val instances = FakeChoreInstanceRepository()

        @Bean
        fun roomServiceForSetup(): RoomService = RoomService(rooms, definitions, instances)

        @Bean
        fun cleanlinessService(): CleanlinessService = CleanlinessService(rooms, definitions, instances)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var roomServiceForSetup: RoomService

    @Test
    fun `방 개수만큼 점수를 반환한다`() {
        roomServiceForSetup.create("거실", 26, 38, 74, 50, 1)

        mockMvc.perform(get("/api/cleanliness"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasSize<Any>(1)))
    }
}
