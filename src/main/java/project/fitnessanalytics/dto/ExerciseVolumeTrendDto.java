package project.fitnessanalytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ExerciseVolumeTrendDto(
        UUID exerciseId,
        String exerciseName,
        String muscleGroup,
        BigDecimal totalVolume,
        int totalSets,
        BigDecimal avgVolumePerSession,
        String trend, 
        List<WeeklyData> weeklyData
) {
    public record WeeklyData(
            LocalDate weekStart,
            BigDecimal volume,
            int sets
    ) {}
}