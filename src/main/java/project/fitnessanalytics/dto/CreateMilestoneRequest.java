package project.fitnessanalytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateMilestoneRequest(
        @NotNull UUID userId,
        @NotBlank String title,
        String description,
        @NotNull LocalDate achievedDate,
        @NotNull MilestoneType type
) {
    public enum MilestoneType {
        VOLUME, CONSISTENCY, STRENGTH, ENDURANCE, PERSONAL_RECORD
    }
}
