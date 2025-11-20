package project.fitnessanalytics.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void syncExercises(@RequestBody List<ExerciseSyncRequest> exercises) {
        syncService.syncExercises(exercises);
    }

    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExercise(@PathVariable UUID exerciseId) {
        syncService.deleteExercise(exerciseId);
    }

    @PostMapping("/workouts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void syncWorkout(@RequestBody WorkoutSyncRequest request) {
        syncService.syncWorkout(request);
    }

    @DeleteMapping("/workouts/{workoutId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkout(@PathVariable UUID workoutId) {
        syncService.deleteWorkout(workoutId);
    }
}
