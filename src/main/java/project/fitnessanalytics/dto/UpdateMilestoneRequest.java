package project.fitnessanalytics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import project.fitnessanalytics.dto.CreateMilestoneRequest.MilestoneType;

import java.time.LocalDate;

public record UpdateMilestoneRequest(
        @NotBlank String title,
        String description,
        @NotNull LocalDate achievedDate,
        @NotNull MilestoneType type
) {}

