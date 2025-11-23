package project.fitnessanalytics.dto.sync;

import project.fitnessanalytics.model.Equipment;
import project.fitnessanalytics.model.MuscleGroup;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExerciseSyncRequest(
        UUID id,
        UUID ownerUserId,
        String name,
        MuscleGroup primaryMuscle,
        Equipment equipment,
        LocalDateTime createdOn
) {}