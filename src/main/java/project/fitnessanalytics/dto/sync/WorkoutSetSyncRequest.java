package project.fitnessanalytics.dto.sync;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import project.fitnessanalytics.model.SetGroupType;
import java.math.BigDecimal;
import java.util.UUID;

public record WorkoutSetSyncRequest(
        UUID id,
        @NotNull UUID exerciseId,
        @Positive Integer reps,
        @NotNull @Positive BigDecimal weight,
        Boolean warmup,
        UUID groupId,
        SetGroupType groupType,
        Integer groupOrder,
        Integer setNumber,
        Integer exerciseOrder
) {}