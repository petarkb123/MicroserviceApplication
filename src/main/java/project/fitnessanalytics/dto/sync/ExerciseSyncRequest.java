package project.fitnessanalytics.dto.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import project.fitnessanalytics.model.Equipment;
import project.fitnessanalytics.model.MuscleGroup;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExerciseSyncRequest(
        @NotNull UUID id,
        @NotNull UUID ownerUserId,
        @NotBlank String name,
        @NotNull MuscleGroup primaryMuscle,
        @NotNull Equipment equipment,
        @NotNull LocalDateTime createdOn
) {}