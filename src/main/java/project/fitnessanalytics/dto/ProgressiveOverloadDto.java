package project.fitnessanalytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProgressiveOverloadDto(
        UUID exerciseId,
        String exerciseName,
        String muscleGroup,
        BigDecimal startingWeight,
        BigDecimal currentWeight,
        double progressPercent,
        String status, 
        List<ProgressPoint> progressPoints
) {
    public record ProgressPoint(
            LocalDate date,
            BigDecimal weight,
            Integer reps
    ) {}
}