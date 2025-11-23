package project.fitnessanalytics.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.fitnessanalytics.dto.sync.ExerciseSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSetSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSyncRequest;
import project.fitnessanalytics.model.Equipment;
import project.fitnessanalytics.model.Exercise;
import project.fitnessanalytics.model.MuscleGroup;
import project.fitnessanalytics.model.WorkoutSession;
import project.fitnessanalytics.repository.ExerciseRepository;
import project.fitnessanalytics.repository.WorkoutSessionRepository;
import project.fitnessanalytics.repository.WorkoutSetRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private ExerciseRepository exerciseRepo;

    @Mock
    private WorkoutSessionRepository sessionRepo;

    @Mock
    private WorkoutSetRepository setRepo;

    @InjectMocks
    private SyncService syncService;

    @Test
    void syncExercises_success() {
        UUID exerciseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExerciseSyncRequest request = new ExerciseSyncRequest(
                exerciseId,
                userId,
                "Bench Press",
                MuscleGroup.CHEST,
                Equipment.BARBELL,
                LocalDateTime.now()
        );

        when(exerciseRepo.findById(exerciseId)).thenReturn(Optional.empty());
        when(exerciseRepo.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        syncService.syncExercises(List.of(request));

        verify(exerciseRepo).findById(exerciseId);
        verify(exerciseRepo).save(any(Exercise.class));
    }

    @Test
    void syncExercises_nullList_doesNothing() {
        syncService.syncExercises(null);

        verify(exerciseRepo, never()).save(any());
    }

    @Test
    void syncExercises_emptyList_doesNothing() {
        syncService.syncExercises(List.of());

        verify(exerciseRepo, never()).save(any());
    }

    @Test
    void syncExercises_nullId_skipsExercise() {
        ExerciseSyncRequest request = new ExerciseSyncRequest(
                null,
                UUID.randomUUID(),
                "Test",
                MuscleGroup.CHEST,
                Equipment.BARBELL,
                LocalDateTime.now()
        );

        syncService.syncExercises(List.of(request));

        verify(exerciseRepo, never()).save(any());
    }

    @Test
    void syncExercises_updatesExistingExercise() {
        UUID exerciseId = UUID.randomUUID();
        Exercise existing = Exercise.builder()
                .id(exerciseId)
                .name("Old Name")
                .build();

        ExerciseSyncRequest request = new ExerciseSyncRequest(
                exerciseId,
                UUID.randomUUID(),
                "New Name",
                MuscleGroup.CHEST,
                Equipment.BARBELL,
                LocalDateTime.now()
        );

        when(exerciseRepo.findById(exerciseId)).thenReturn(Optional.of(existing));
        when(exerciseRepo.save(any(Exercise.class))).thenAnswer(inv -> inv.getArgument(0));

        syncService.syncExercises(List.of(request));

        verify(exerciseRepo).findById(exerciseId);
        verify(exerciseRepo).save(existing);
        assertEquals("New Name", existing.getName());
    }

    @Test
    void deleteExercise_success() {
        UUID exerciseId = UUID.randomUUID();

        doNothing().when(setRepo).deleteByExerciseId(exerciseId);
        doNothing().when(exerciseRepo).deleteById(exerciseId);

        syncService.deleteExercise(exerciseId);

        verify(setRepo).deleteByExerciseId(exerciseId);
        verify(exerciseRepo).deleteById(exerciseId);
    }

    @Test
    void deleteExercise_nullId_doesNothing() {
        syncService.deleteExercise(null);

        verify(setRepo, never()).deleteByExerciseId(any());
        verify(exerciseRepo, never()).deleteById(any());
    }

    @Test
    void syncWorkout_success() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();

        WorkoutSetSyncRequest setRequest = new WorkoutSetSyncRequest(
                UUID.randomUUID(),
                exerciseId,
                10,
                new BigDecimal("100.0"),
                false,
                null,
                null,
                null,
                1,
                1
        );

        WorkoutSyncRequest request = new WorkoutSyncRequest(
                sessionId,
                userId,
                LocalDateTime.now(),
                LocalDateTime.now(),
                WorkoutSession.SessionStatus.FINISHED,
                List.of(setRequest)
        );

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.empty());
        when(sessionRepo.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(setRepo).deleteBySessionId(sessionId);
        when(setRepo.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        syncService.syncWorkout(request);

        verify(sessionRepo).findById(sessionId);
        verify(sessionRepo).save(any(WorkoutSession.class));
        verify(setRepo).deleteBySessionId(sessionId);
        verify(setRepo).saveAll(any());
    }

    @Test
    void syncWorkout_nullRequest_doesNothing() {
        syncService.syncWorkout(null);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void syncWorkout_nullId_doesNothing() {
        WorkoutSyncRequest request = new WorkoutSyncRequest(
                null,
                UUID.randomUUID(),
                LocalDateTime.now(),
                null,
                WorkoutSession.SessionStatus.IN_PROGRESS,
                List.of()
        );

        syncService.syncWorkout(request);

        verify(sessionRepo, never()).save(any());
    }

    @Test
    void syncWorkout_updatesExistingSession() {
        UUID sessionId = UUID.randomUUID();
        WorkoutSession existing = WorkoutSession.builder()
                .id(sessionId)
                .status(WorkoutSession.SessionStatus.IN_PROGRESS)
                .build();

        WorkoutSyncRequest request = new WorkoutSyncRequest(
                sessionId,
                UUID.randomUUID(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                WorkoutSession.SessionStatus.FINISHED,
                List.of()
        );

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.of(existing));
        when(sessionRepo.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(setRepo).deleteBySessionId(sessionId);

        syncService.syncWorkout(request);

        verify(sessionRepo).save(existing);
        assertEquals(WorkoutSession.SessionStatus.FINISHED, existing.getStatus());
    }

    @Test
    void syncWorkout_emptySets_deletesExistingSets() {
        UUID sessionId = UUID.randomUUID();
        WorkoutSyncRequest request = new WorkoutSyncRequest(
                sessionId,
                UUID.randomUUID(),
                LocalDateTime.now(),
                null,
                WorkoutSession.SessionStatus.IN_PROGRESS,
                List.of()
        );

        when(sessionRepo.findById(sessionId)).thenReturn(Optional.empty());
        when(sessionRepo.save(any(WorkoutSession.class))).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(setRepo).deleteBySessionId(sessionId);

        syncService.syncWorkout(request);

        verify(setRepo).deleteBySessionId(sessionId);
        verify(setRepo, never()).saveAll(any());
    }

    @Test
    void deleteWorkout_success() {
        UUID sessionId = UUID.randomUUID();

        doNothing().when(setRepo).deleteBySessionId(sessionId);
        doNothing().when(sessionRepo).deleteById(sessionId);

        syncService.deleteWorkout(sessionId);

        verify(setRepo).deleteBySessionId(sessionId);
        verify(sessionRepo).deleteById(sessionId);
    }

    @Test
    void deleteWorkout_nullId_doesNothing() {
        syncService.deleteWorkout(null);

        verify(setRepo, never()).deleteBySessionId(any());
        verify(sessionRepo, never()).deleteById(any());
    }
}

