package project.fitnessanalytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WeeklySummaryResponse(
        LocalDate from,
        LocalDate to,
        List<DayStat> days
) {
    public record DayStat(
            LocalDate date,
            int sessions,
            int sets,
            int reps,
            BigDecimal volume
    ) {}
}
