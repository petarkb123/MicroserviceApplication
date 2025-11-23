package project.fitnessanalytics.dto.sync;

import project.fitnessanalytics.model.WorkoutSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkoutSyncRequest(
        UUID id,
        UUID userId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        WorkoutSession.SessionStatus status,
        List<WorkoutSetSyncRequest> sets
) {}