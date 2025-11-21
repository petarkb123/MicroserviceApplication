package project.fitnessanalytics.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import project.fitnessanalytics.dto.sync.ExerciseSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSyncRequest;
import project.fitnessanalytics.model.Equipment;
import project.fitnessanalytics.model.MuscleGroup;
import project.fitnessanalytics.model.WorkoutSession;
import project.fitnessanalytics.service.SyncService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Mock
    private SyncService syncService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void syncExercises_returnsAccepted() throws Exception {
        ExerciseSyncRequest request = new ExerciseSyncRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bench Press",
                MuscleGroup.CHEST,
                Equipment.BARBELL,
                LocalDateTime.now()
        );

        doNothing().when(syncService).syncExercises(any());

        mockMvc.perform(post("/api/analytics/internal/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(request))))
                .andExpect(status().isAccepted());
    }

    @Test
    void deleteExercise_returnsNoContent() throws Exception {
        UUID exerciseId = UUID.randomUUID();

        doNothing().when(syncService).deleteExercise(exerciseId);

        mockMvc.perform(delete("/api/analytics/internal/exercises/{exerciseId}", exerciseId))
                .andExpect(status().isNoContent());
    }

    @Test
    void syncWorkout_returnsAccepted() throws Exception {
        WorkoutSyncRequest request = new WorkoutSyncRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                WorkoutSession.SessionStatus.FINISHED,
                List.of()
        );

        doNothing().when(syncService).syncWorkout(any());

        mockMvc.perform(post("/api/analytics/internal/workouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());
    }

    @Test
    void deleteWorkout_returnsNoContent() throws Exception {
        UUID workoutId = UUID.randomUUID();

        doNothing().when(syncService).deleteWorkout(workoutId);

        mockMvc.perform(delete("/api/analytics/internal/workouts/{workoutId}", workoutId))
                .andExpect(status().isNoContent());
    }
}

