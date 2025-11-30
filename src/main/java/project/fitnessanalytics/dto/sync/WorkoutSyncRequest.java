package project.fitnessanalytics.dto.sync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import project.fitnessanalytics.model.WorkoutSession;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkoutSyncRequest(
        @NotNull UUID id,
        @NotNull UUID userId,
        @NotNull LocalDateTime startedAt,
        LocalDateTime finishedAt,
        @NotNull WorkoutSession.SessionStatus status,
        @Valid List<WorkoutSetSyncRequest> sets
) {}