package project.fitnessanalytics.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import project.fitnessanalytics.dto.sync.ExerciseSyncRequest;
import project.fitnessanalytics.dto.sync.WorkoutSyncRequest;
import project.fitnessanalytics.service.SyncService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics/internal")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/exercises")
    public ResponseEntity<Void> syncExercises(@RequestBody @Valid @NotNull List<ExerciseSyncRequest> exercises) {
        syncService.syncExercises(exercises);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/exercises/{exerciseId}")
    public ResponseEntity<Void> deleteExercise(@PathVariable @NotNull UUID exerciseId) {
        syncService.deleteExercise(exerciseId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/workouts")
    public ResponseEntity<Void> syncWorkout(@RequestBody @Valid @NotNull WorkoutSyncRequest request) {
        syncService.syncWorkout(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @DeleteMapping("/workouts/{workoutId}")
    public ResponseEntity<Void> deleteWorkout(@PathVariable @NotNull UUID workoutId) {
        syncService.deleteWorkout(workoutId);
        return ResponseEntity.noContent().build();
    }
}
