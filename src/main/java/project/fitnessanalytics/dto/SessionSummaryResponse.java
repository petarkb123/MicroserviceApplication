package project.fitnessanalytics.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SessionSummaryResponse(
        UUID sessionId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int totalSets,
        int totalReps,
        BigDecimal totalVolume
) {}
