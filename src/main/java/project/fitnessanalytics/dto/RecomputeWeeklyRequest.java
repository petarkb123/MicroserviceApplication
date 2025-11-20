package project.fitnessanalytics.dto;

import java.time.LocalDate;

public record RecomputeWeeklyRequest(
        LocalDate from,
        LocalDate to
) {}


