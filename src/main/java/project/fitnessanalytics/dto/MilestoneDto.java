package project.fitnessanalytics.dto;

import project.fitnessanalytics.dto.CreateMilestoneRequest.MilestoneType;
import java.time.LocalDate;
import java.util.UUID;

public record MilestoneDto(
        UUID id,
        String title,
        String description,
        LocalDate achievedDate,
        MilestoneType type,
        boolean systemGenerated
) {}
