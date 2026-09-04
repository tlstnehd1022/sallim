package sallim.calendar.api

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
import sallim.calendar.application.CalendarEventService
import sallim.calendar.application.FakeCalendarEventRepository
import java.time.LocalDateTime
import java.util.UUID

// ponytail: TestConfig의 FakeCalendarEventRepository는 캐시된 Spring 컨텍스트에 묶인 싱글턴이라
// 테스트 메서드 간 상태가 새어나간다(예: 다른 테스트가 만든 이벤트가 목록 조회에 섞임) —
// @DirtiesContext로 메서드마다 컨텍스트(=repository)를 새로 만들어 격리한다.
@WebMvcTest(CalendarEventController::class)
@Import(CalendarEventControllerTest.TestConfig::class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CalendarEventControllerTest {

    @TestConfiguration
    class TestConfig {
        private val repository = FakeCalendarEventRepository()

        @Bean
        fun calendarEventService(): CalendarEventService = CalendarEventService(repository)
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var calendarEventService: CalendarEventService

    @Test
    fun `반복 없는 일정을 생성하면 201을 반환한다`() {
        mockMvc.perform(
            post("/api/calendar-events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), UUID.randomUUID(), "케이크 사기", null
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("생일"))
            .andExpect(jsonPath("$.recurrence").doesNotExist())
    }

    @Test
    fun `반복 있는 일정을 생성하면 recurrence가 응답에 포함된다`() {
        mockMvc.perform(
            post("/api/calendar-events").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "운동", LocalDateTime.of(2026, 9, 1, 7, 0), UUID.randomUUID(), null,
                            RecurrenceDto("WEEKLY_N_TIMES", 3)
                        )
                    )
                )
        ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.recurrence.type").value("WEEKLY_N_TIMES"))
            .andExpect(jsonPath("$.recurrence.times").value(3))
    }

    @Test
    fun `조회하면 발생 목록을 반환한다`() {
        val member = UUID.randomUUID()
        calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(member), null, null
        )

        mockMvc.perform(
            get("/api/calendar-events").param("from", "2026-09-01").param("to", "2026-09-30")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].title").value("생일"))
            .andExpect(jsonPath("$[0].memberId").value(member.toString()))
    }

    @Test
    fun `from 파라미터가 없으면 400`() {
        mockMvc.perform(get("/api/calendar-events").param("to", "2026-09-30"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    @Test
    fun `from이 to보다 늦으면 400`() {
        mockMvc.perform(get("/api/calendar-events").param("from", "2026-09-30").param("to", "2026-09-01"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `수정하면 200과 갱신된 값을 반환한다`() {
        val member = UUID.randomUUID()
        val created = calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(member), null, null
        )

        mockMvc.perform(
            put("/api/calendar-events/${created.id.value}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest(
                            "생일파티", LocalDateTime.of(2026, 9, 11, 18, 0), member, "장소 예약", null
                        )
                    )
                )
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("생일파티"))
            .andExpect(jsonPath("$.memo").value("장소 예약"))
    }

    @Test
    fun `존재하지 않는 일정을 수정하면 404를 반환한다`() {
        mockMvc.perform(
            put("/api/calendar-events/${UUID.randomUUID()}").contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        CalendarEventRequest("생일", LocalDateTime.of(2026, 9, 10, 14, 0), UUID.randomUUID(), null, null)
                    )
                )
        ).andExpect(status().isNotFound)
    }

    @Test
    fun `삭제하면 204를 반환한다`() {
        val created = calendarEventService.create(
            "생일", LocalDateTime.of(2026, 9, 10, 14, 0), sallim.calendar.domain.MemberId(UUID.randomUUID()), null, null
        )

        mockMvc.perform(delete("/api/calendar-events/${created.id.value}"))
            .andExpect(status().isNoContent)
    }
}
