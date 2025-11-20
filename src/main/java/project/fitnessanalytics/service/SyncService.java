package project.fitnessanalytics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutSetRepository setRepository;

    @Transactional
    public void syncExercises(List<ExerciseSyncRequest> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return;
        }
        exercises.forEach(req -> {
            if (req.id() == null) {
                log.warn("Skipping exercise sync with null id");
                return;
            }
            Exercise exercise = exerciseRepository.findById(req.id())
                    .orElseGet(() -> Exercise.builder().id(req.id()).build());
            exercise.setOwnerUserId(req.ownerUserId());
            exercise.setName(req.name());
            exercise.setPrimaryMuscle(req.primaryMuscle());
            exercise.setEquipment(req.equipment());
            exercise.setCreatedOn(req.createdOn());
            exerciseRepository.save(exercise);
        });
    }

    @Transactional
    public void deleteExercise(UUID exerciseId) {
        if (exerciseId == null) {
            return;
        }
        setRepository.deleteByExerciseId(exerciseId);
        exerciseRepository.deleteById(exerciseId);
    }

    @Transactional
    public void syncWorkout(WorkoutSyncRequest request) {
        if (request == null || request.id() == null) {
            return;
        }
        WorkoutSession session = sessionRepository.findById(request.id())
                .orElseGet(() -> WorkoutSession.builder().id(request.id()).build());
        session.setUserId(request.userId());
        session.setStartedAt(request.startedAt());
        session.setFinishedAt(request.finishedAt());
        session.setStatus(request.status());
        sessionRepository.save(session);

        setRepository.deleteBySessionId(request.id());

        if (request.sets() != null && !request.sets().isEmpty()) {
            List<WorkoutSet> toSave = request.sets().stream()
                    .map(set -> mapSet(request.id(), set))
                    .toList();
            setRepository.saveAll(toSave);
        }
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

    @Transactional
    public void deleteWorkout(UUID sessionId) {
        if (sessionId == null) {
            return;
        }
        setRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }
}
