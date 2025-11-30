package project.fitnessanalytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.fitnessanalytics.dto.sync.ExerciseSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSetSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSyncRequest;
import project.fitnessanalytics.model.Exercise;
import project.fitnessanalytics.model.WorkoutSession;
import project.fitnessanalytics.model.WorkoutSet;
import project.fitnessanalytics.repository.ExerciseRepository;
import project.fitnessanalytics.repository.WorkoutSessionRepository;
import project.fitnessanalytics.repository.WorkoutSetRepository;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncService {

    private final ExerciseRepository exerciseRepo;
    private final WorkoutSessionRepository sessionRepo;
    private final WorkoutSetRepository setRepo;

    @CacheEvict(value = {"volumeTrends", "progressiveOverload", "personalRecords"}, allEntries = true)
    @Transactional
    public void syncExercises(List<ExerciseSyncRequest> exercises) {
        log.info("Syncing {} exercises", exercises != null ? exercises.size() : 0);
        if (exercises == null || exercises.isEmpty()) {
            return;
        }
        exercises.forEach(this::syncSingleExercise);
    }

    private void syncSingleExercise(ExerciseSyncRequest req) {
        if (req.id() == null) {
            log.warn("Skipping exercise sync with null id");
            return;
        }
        Exercise exercise = exerciseRepo.findById(req.id())
                .orElseGet(() -> Exercise.builder().id(req.id()).build());
        updateExerciseFromRequest(exercise, req);
        exerciseRepo.save(exercise);
    }

    private void updateExerciseFromRequest(Exercise exercise, ExerciseSyncRequest req) {
        exercise.setOwnerUserId(req.ownerUserId());
        exercise.setName(req.name());
        exercise.setPrimaryMuscle(req.primaryMuscle());
        exercise.setEquipment(req.equipment());
        exercise.setCreatedOn(req.createdOn());
    }

    @CacheEvict(value = {"volumeTrends", "progressiveOverload", "personalRecords", "weeklyStats", "sessionSummaries"}, allEntries = true)
    @Transactional
    public void deleteExercise(UUID exerciseId) {
        log.info("Deleting exercise {}", exerciseId);
        if (exerciseId == null) {
            return;
        }
        setRepo.deleteByExerciseId(exerciseId);
        exerciseRepo.deleteById(exerciseId);
    }

    @CacheEvict(value = {"weeklyStats", "sessionSummaries", "trainingFrequency", "volumeTrends", "progressiveOverload", "personalRecords"}, allEntries = true)
    @Transactional
    public void syncWorkout(WorkoutSyncRequest request) {
        log.info("Syncing workout {}", request != null ? request.id() : null);
        if (request == null || request.id() == null) {
            return;
        }
        WorkoutSession session = sessionRepo.findById(request.id())
                .orElseGet(() -> WorkoutSession.builder().id(request.id()).build());
        updateSessionFromRequest(session, request);
        sessionRepo.save(session);

        setRepo.deleteBySessionId(request.id());

        if (request.sets() != null && !request.sets().isEmpty()) {
            List<WorkoutSet> toSave = request.sets().stream()
                    .map(set -> mapSet(request.id(), set))
                    .toList();
            setRepo.saveAll(toSave);
        }
    }

    private void updateSessionFromRequest(WorkoutSession session, WorkoutSyncRequest request) {
        session.setUserId(request.userId());
        session.setStartedAt(request.startedAt());
        session.setFinishedAt(request.finishedAt());
        session.setStatus(request.status());
    }

    private WorkoutSet mapSet(UUID sessionId, WorkoutSetSyncRequest set) {
        UUID setId = set.id() != null ? set.id() : UUID.randomUUID();
        boolean warmup = Boolean.TRUE.equals(set.warmup());
        return WorkoutSet.builder()
                .id(setId)
                .sessionId(sessionId)
                .exerciseId(set.exerciseId())
                .reps(set.reps())
                .weight(set.weight())
                .warmup(warmup)
                .groupId(set.groupId())
                .groupType(set.groupType())
                .groupOrder(set.groupOrder())
                .setNumber(set.setNumber())
                .exerciseOrder(set.exerciseOrder())
                .build();
    }

    @CacheEvict(value = {"weeklyStats", "sessionSummaries", "trainingFrequency", "volumeTrends", "progressiveOverload", "personalRecords"}, allEntries = true)
    @Transactional
    public void deleteWorkout(UUID sessionId) {
        log.info("Deleting workout session {}", sessionId);
        if (sessionId == null) {
            return;
        }
        setRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }
}