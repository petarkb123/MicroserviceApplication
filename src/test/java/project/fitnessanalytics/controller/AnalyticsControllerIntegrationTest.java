package project.fitnessanalytics.controller;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import project.fitnessanalytics.dto.*;
import project.fitnessanalytics.service.AnalyticsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    private UUID userId;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void getWeeklyStats_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        WeeklySummaryResponse.DayStat dayStat = new WeeklySummaryResponse.DayStat(
                start, 0, 0, 0, BigDecimal.ZERO
        );
        WeeklySummaryResponse mockResponse = new WeeklySummaryResponse(
                start, end, Collections.singletonList(dayStat)
        );

        when(analyticsService.getWeeklyStats(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/weekly")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").exists())
                .andExpect(jsonPath("$.to").exists());
    }

    @Test
    void getWeeklyStats_withMissingUserId_returnsInternalServerError() throws Exception {
        mockMvc.perform(get("/api/analytics/weekly")
                        .param("from", LocalDate.now().minusDays(7).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void getPersonalRecords_returnsOk() throws Exception {
        userId = UUID.randomUUID();

        PersonalRecordsDto mockResponse = new PersonalRecordsDto(
                new ArrayList<>(),
                new ArrayList<>()
        );

        when(analyticsService.getPersonalRecords(eq(userId)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/personal-records")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exercisePRs").exists())
                .andExpect(jsonPath("$.milestones").exists());
    }

    @Test
    void getTrainingFrequency_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();

        TrainingFrequencyResponse mockResponse = new TrainingFrequencyResponse(
                0, 0.0, new HashMap<>(), new ArrayList<>(), 0, 0.0
        );

        when(analyticsService.getTrainingFrequency(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/analytics/training-frequency")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkouts").exists());
    }

    @Test
    void getVolumeTrends_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(90);
        LocalDate end = LocalDate.now();

        when(analyticsService.getExerciseVolumeTrends(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/volume-trends")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getProgressiveOverload_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(90);
        LocalDate end = LocalDate.now();

        when(analyticsService.getProgressiveOverload(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/progressive-overload")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getSessionSummaries_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        when(analyticsService.getSessionSummaries(eq(userId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/sessions")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void getWeeklyStats_withInvalidDateRange_returnsBadRequest() throws Exception {
        userId = UUID.randomUUID();
        LocalDate start = LocalDate.now();
        LocalDate end = LocalDate.now().minusDays(7);

        mockMvc.perform(get("/api/analytics/weekly")
                        .header("X-User-Id", userId.toString())
                        .param("from", start.toString())
                        .param("to", end.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMilestone_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        CreateMilestoneRequest request = new CreateMilestoneRequest(
                userId,
                "First 100kg bench",
                "Achieved bench press milestone",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD
        );
        MilestoneDto response = new MilestoneDto(
                milestoneId,
                "First 100kg bench",
                "Achieved bench press milestone",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD,
                false
        );

        when(analyticsService.createMilestone(any(CreateMilestoneRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/analytics/milestones")
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First 100kg bench"));
    }

    @Test
    void getMilestones_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        MilestoneDto milestone = new MilestoneDto(
                UUID.randomUUID(),
                "First 100kg bench",
                "Achieved bench press milestone",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD,
                false
        );

        when(analyticsService.getUserMilestones(eq(userId)))
                .thenReturn(List.of(milestone));

        mockMvc.perform(get("/api/analytics/milestones")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("First 100kg bench"));
    }

    @Test
    void updateMilestone_returnsOk() throws Exception {
        userId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();
        UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                "Updated title",
                "Updated description",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD
        );
        MilestoneDto response = new MilestoneDto(
                milestoneId,
                "Updated title",
                "Updated description",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD,
                false
        );

        when(analyticsService.updateMilestone(eq(milestoneId), eq(userId), any(UpdateMilestoneRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/analytics/milestones/{id}", milestoneId)
                        .header("X-User-Id", userId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));
    }

    @Test
    void deleteMilestone_returnsNoContent() throws Exception {
        userId = UUID.randomUUID();
        UUID milestoneId = UUID.randomUUID();

        mockMvc.perform(delete("/api/analytics/milestones/{id}", milestoneId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
    }
}

