package project.fitnessanalytics.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import project.fitnessanalytics.dto.*;
import project.fitnessanalytics.service.AnalyticsService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/weekly")
    public ResponseEntity<WeeklySummaryResponse> getWeeklyStats(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate to) {
        
        if (from.isAfter(to)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        
        return ResponseEntity.ok(analyticsService.getWeeklyStats(userId, from, to));
    }

    @PostMapping("/weekly/recompute")
    public ResponseEntity<WeeklySummaryResponse> recomputeWeeklyStats(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestBody @Valid RecomputeWeeklyRequest request) {
        LocalDate start = request.from() != null ? request.from() : LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate end = request.to() != null ? request.to() : start.plusDays(6);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        WeeklySummaryResponse summary = analyticsService.recomputeWeeklyStats(userId, start, end);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionSummaryResponse>> getSessionSummaries(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate to) {
        
        if (from.isAfter(to)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        
        return ResponseEntity.ok(analyticsService.getSessionSummaries(userId, from, to));
    }

    @GetMapping("/training-frequency")
    public ResponseEntity<TrainingFrequencyResponse> getTrainingFrequency(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate to) {
        
        if (from.isAfter(to)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        
        return ResponseEntity.ok(analyticsService.getTrainingFrequency(userId, from, to));
    }

    @GetMapping("/volume-trends")
    public ResponseEntity<List<ExerciseVolumeTrendDto>> getExerciseVolumeTrends(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate to) {
        
        if (from.isAfter(to)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        
        return ResponseEntity.ok(analyticsService.getExerciseVolumeTrends(userId, from, to));
    }

    @GetMapping("/progressive-overload")
    public ResponseEntity<List<ProgressiveOverloadDto>> getProgressiveOverload(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @NotNull LocalDate to) {
        
        if (from.isAfter(to)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Start date must be before end date");
        }
        
        return ResponseEntity.ok(analyticsService.getProgressiveOverload(userId, from, to));
    }

    @GetMapping("/personal-records")
    public ResponseEntity<PersonalRecordsDto> getPersonalRecords(
            @RequestHeader("X-User-Id") @NotNull UUID userId) {
        return ResponseEntity.ok(analyticsService.getPersonalRecords(userId));
    }

    @PostMapping("/milestones")
    public ResponseEntity<MilestoneDto> createMilestone(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @RequestBody @Valid CreateMilestoneRequest request) {
        if (!request.userId().equals(userId)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Cannot create milestone for another user");
        }
        return ResponseEntity.ok(analyticsService.createMilestone(request));
    }

    @GetMapping("/milestones")
    public ResponseEntity<List<MilestoneDto>> getMilestones(
            @RequestHeader("X-User-Id") @NotNull UUID userId) {
        return ResponseEntity.ok(analyticsService.getUserMilestones(userId));
    }

    @PutMapping("/milestones/{id}")
    public ResponseEntity<MilestoneDto> updateMilestone(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @PathVariable UUID id,
            @RequestBody @Valid UpdateMilestoneRequest request) {
        return ResponseEntity.ok(analyticsService.updateMilestone(id, userId, request));
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<Void> deleteMilestone(
            @RequestHeader("X-User-Id") @NotNull UUID userId,
            @PathVariable UUID id) {
        analyticsService.deleteMilestone(id, userId);
        return ResponseEntity.noContent().build();
    }
}


