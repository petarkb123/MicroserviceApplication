package project.fitnessanalytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PersonalRecordsDto(
        List<ExercisePR> exercisePRs,
        List<Milestone> milestones
) {
    public record ExercisePR(
            UUID exerciseId,
            String exerciseName,
            String recordType, 
            BigDecimal weight,
            Integer reps,
            LocalDate achievedDate
    ) {}

    public record Milestone(
            String title,
            String description,
            String icon
    ) {}
}
