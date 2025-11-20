package project.fitnessanalytics.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.fitnessanalytics.dto.*;
import project.fitnessanalytics.model.Exercise;
import project.fitnessanalytics.model.WorkoutSession;
import project.fitnessanalytics.model.WorkoutSet;
import project.fitnessanalytics.model.milestone.Milestone;
import project.fitnessanalytics.model.summary.WeeklySummarySnapshot;
import project.fitnessanalytics.repository.ExerciseRepository;
import project.fitnessanalytics.repository.MilestoneRepository;
import project.fitnessanalytics.repository.WeeklySummarySnapshotRepository;
import project.fitnessanalytics.repository.WorkoutSessionRepository;
import project.fitnessanalytics.repository.WorkoutSetRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsService {

    private final WorkoutSessionRepository sessionRepo;
    private final WorkoutSetRepository setRepo;
    private final ExerciseRepository exerciseRepo;
    private final MilestoneRepository milestoneRepo;
    private final WeeklySummarySnapshotRepository weeklySummarySnapshotRepository;
    private final ObjectMapper objectMapper;

    private static final java.util.Set<String> AUTO_MILESTONE_TITLES = java.util.Set.of(
            "Centurion",
            "Dedicated (50 Sessions)",
            "Dedicated",
            "Getting Started",
            "Million Pound Club",
            "Half Million",
            "100K Club",
            "Consistency King",
            "Dedicated (12 in 30)"
    );

    public WeeklySummaryResponse getWeeklyStats(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);

        Map<UUID, WorkoutSession> byId = sessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        List<UUID> sids = sessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> sets = sids.isEmpty() ? List.of() : setRepo.findAllBySessionIdIn(sids);

        Map<LocalDate, DayAcc> days = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.put(d, new DayAcc());
        }

        for (WorkoutSession s : sessions) {
            days.get(s.getStartedAt().toLocalDate()).sessions++;
        }

        for (WorkoutSet ws : sets) {
            WorkoutSession s = byId.get(ws.getSessionId());
            if (s == null) continue;
            DayAcc acc = days.get(s.getStartedAt().toLocalDate());
            acc.sets++;
            if (ws.getReps() != null) acc.reps += ws.getReps();
            if (ws.getWeight() != null && ws.getReps() != null) {
                acc.volume = acc.volume.add(ws.getWeight().multiply(BigDecimal.valueOf(ws.getReps())));
            }
        }

        List<WeeklySummaryResponse.DayStat> dayStats = days.entrySet().stream()
                .map(e -> new WeeklySummaryResponse.DayStat(
                        e.getKey(),
                        e.getValue().sessions,
                        e.getValue().sets,
                        e.getValue().reps,
                        e.getValue().volume))
                .toList();

        return new WeeklySummaryResponse(from, to, dayStats);
    }

    @Transactional
    public WeeklySummaryResponse recomputeWeeklyStats(UUID userId, LocalDate from, LocalDate to) {
        WeeklySummaryResponse summary = getWeeklyStats(userId, from, to);

        int totalSessions = summary.days().stream().mapToInt(WeeklySummaryResponse.DayStat::sessions).sum();
        int totalSets = summary.days().stream().mapToInt(WeeklySummaryResponse.DayStat::sets).sum();
        int totalReps = summary.days().stream().mapToInt(WeeklySummaryResponse.DayStat::reps).sum();
        java.math.BigDecimal totalVolume = summary.days().stream()
                .map(WeeklySummaryResponse.DayStat::volume)
                .filter(java.util.Objects::nonNull)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        WeeklySummarySnapshot snapshot = weeklySummarySnapshotRepository
                .findByUserIdAndWeekStart(userId, summary.from())
                .orElseGet(WeeklySummarySnapshot::new);

        snapshot.setUserId(userId);
        snapshot.setWeekStart(summary.from());
        snapshot.setWeekEnd(summary.to());
        snapshot.setTotalSessions(totalSessions);
        snapshot.setTotalSets(totalSets);
        snapshot.setTotalReps(totalReps);
        snapshot.setTotalVolume(totalVolume);
        snapshot.setUpdatedAt(LocalDateTime.now());
        try {
            snapshot.setPayloadJson(objectMapper.writeValueAsString(summary));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize weekly summary for user {}: {}", userId, e.getMessage());
            snapshot.setPayloadJson(null);
        }

        weeklySummarySnapshotRepository.save(snapshot);
        return summary;
    }

    public List<SessionSummaryResponse> getSessionSummaries(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);

        Map<UUID, List<WorkoutSet>> setsBySession = setRepo.findAllBySessionIdIn(
                        sessions.stream().map(WorkoutSession::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkoutSet::getSessionId));

        List<SessionSummaryResponse> out = new ArrayList<>();
        for (WorkoutSession s : sessions) {
            List<WorkoutSet> sets = setsBySession.getOrDefault(s.getId(), List.of());
            int totalSets = sets.size();
            int totalReps = sets.stream().filter(x -> x.getReps() != null).mapToInt(WorkoutSet::getReps).sum();
            BigDecimal volume = sets.stream()
                    .filter(x -> x.getWeight() != null && x.getReps() != null)
                    .map(x -> x.getWeight().multiply(BigDecimal.valueOf(x.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            out.add(new SessionSummaryResponse(s.getId(), s.getStartedAt(), s.getFinishedAt(), totalSets, totalReps, volume));
        }
        return out;
    }

    public TrainingFrequencyResponse getTrainingFrequency(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);

        if (sessions.isEmpty()) {
            return new TrainingFrequencyResponse(0, 0.0, Map.of(), List.of(), 0, 0.0);
        }

        int totalWorkouts = sessions.size();
        long daysBetween = ChronoUnit.DAYS.between(from, to) + 1;
        double weeksCount = daysBetween / 7.0;
        double avgPerWeek = totalWorkouts / weeksCount;

        Map<String, Integer> byDayOfWeek = new LinkedHashMap<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            byDayOfWeek.put(day.name(), 0);
        }
        for (WorkoutSession s : sessions) {
            String dayName = s.getStartedAt().getDayOfWeek().name();
            byDayOfWeek.put(dayName, byDayOfWeek.get(dayName) + 1);
        }

        List<TrainingFrequencyResponse.WeeklyBreakdown> weeklyBreakdown = new ArrayList<>();
        LocalDate weekStart = from;
        while (!weekStart.isAfter(to)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            if (weekEnd.isAfter(to)) weekEnd = to;

            LocalDateTime weekStartTs = weekStart.atStartOfDay();
            LocalDateTime weekEndTs = weekEnd.plusDays(1).atStartOfDay().minusNanos(1);

            long count = sessions.stream()
                    .filter(s -> !s.getStartedAt().isBefore(weekStartTs) && !s.getStartedAt().isAfter(weekEndTs))
                    .count();

            weeklyBreakdown.add(new TrainingFrequencyResponse.WeeklyBreakdown(weekStart, weekEnd, (int) count));
            weekStart = weekStart.plusDays(7);
        }

        int longestStreak = 0;
        int currentStreak = 0;
        HashSet<LocalDate> workoutDates = sessions.stream()
                .map(s -> s.getStartedAt().toLocalDate())
                .collect(Collectors.toCollection(HashSet::new));

        LocalDate currentDate = from;
        while (!currentDate.isAfter(to)) {
            if (workoutDates.contains(currentDate)) {
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
            currentDate = currentDate.plusDays(1);
        }

        return new TrainingFrequencyResponse(totalWorkouts, avgPerWeek, byDayOfWeek, weeklyBreakdown, longestStreak, currentStreak);
    }

    public List<ExerciseVolumeTrendDto> getExerciseVolumeTrends(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);

        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, WorkoutSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> allSets = setRepo.findAllBySessionIdIn(sessionIds);

        Map<UUID, List<WorkoutSet>> setsByExercise = allSets.stream()
                .collect(Collectors.groupingBy(WorkoutSet::getExerciseId));

        Set<UUID> exerciseIds = setsByExercise.keySet();
        Map<UUID, Exercise> exerciseMap = exerciseRepo.findAllByIdIn(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        List<ExerciseVolumeTrendDto> trends = new ArrayList<>();

        for (Map.Entry<UUID, List<WorkoutSet>> entry : setsByExercise.entrySet()) {
            UUID exerciseId = entry.getKey();
            List<WorkoutSet> sets = entry.getValue();
            Exercise exercise = exerciseMap.get(exerciseId);

            if (exercise == null) continue;

            Map<LocalDate, BigDecimal> weeklyVolume = new TreeMap<>();
            Map<LocalDate, Integer> weeklySets = new TreeMap<>();

            for (WorkoutSet set : sets) {
                WorkoutSession session = sessionMap.get(set.getSessionId());
                if (session == null) continue;

                LocalDate weekStart = session.getStartedAt().toLocalDate()
                        .with(DayOfWeek.MONDAY);

                BigDecimal setVolume = BigDecimal.ZERO;
                if (set.getWeight() != null && set.getReps() != null) {
                    setVolume = set.getWeight().multiply(BigDecimal.valueOf(set.getReps()));
                }

                weeklyVolume.merge(weekStart, setVolume, BigDecimal::add);
                weeklySets.merge(weekStart, 1, Integer::sum);
            }

            BigDecimal totalVolume = sets.stream()
                    .filter(s -> s.getWeight() != null && s.getReps() != null)
                    .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int totalSets = sets.size();
            BigDecimal avgVolumePerSession = weeklySets.isEmpty() ? BigDecimal.ZERO : totalVolume.divide(
                    BigDecimal.valueOf(weeklySets.size()),
                    2,
                    RoundingMode.HALF_UP
            );

            List<LocalDate> weeks = new ArrayList<>(weeklyVolume.keySet());
            String trend = "stable";
            if (weeks.size() >= 2) {
                int midPoint = weeks.size() / 2;
                BigDecimal firstHalfAvg = weeks.subList(0, midPoint).stream()
                        .map(weeklyVolume::get)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(midPoint), 2, RoundingMode.HALF_UP);

                BigDecimal secondHalfAvg = weeks.subList(midPoint, weeks.size()).stream()
                        .map(weeklyVolume::get)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(weeks.size() - midPoint), 2, RoundingMode.HALF_UP);

                if (secondHalfAvg.compareTo(firstHalfAvg.multiply(BigDecimal.valueOf(1.1))) > 0) {
                    trend = "increasing";
                } else if (secondHalfAvg.compareTo(firstHalfAvg.multiply(BigDecimal.valueOf(0.9))) < 0) {
                    trend = "decreasing";
                }
            }

            List<ExerciseVolumeTrendDto.WeeklyData> weeklyData = weeklyVolume.entrySet().stream()
                    .map(e -> new ExerciseVolumeTrendDto.WeeklyData(
                            e.getKey(),
                            e.getValue(),
                            weeklySets.get(e.getKey())
                    ))
                    .toList();

            trends.add(new ExerciseVolumeTrendDto(
                    exerciseId,
                    exercise.getName(),
                    exercise.getPrimaryMuscle().name(),
                    totalVolume,
                    totalSets,
                    avgVolumePerSession,
                    trend,
                    weeklyData
            ));
        }

        trends.sort((a, b) -> b.totalVolume().compareTo(a.totalVolume()));

        return trends;
    }

    public List<ProgressiveOverloadDto> getProgressiveOverload(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);

        List<WorkoutSession> sessions = sessionRepo
                .findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);

        if (sessions.isEmpty()) {
            return List.of();
        }

        Map<UUID, WorkoutSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> allSets = setRepo.findAllBySessionIdIn(sessionIds);

        Map<UUID, List<WorkoutSet>> setsByExercise = allSets.stream()
                .collect(Collectors.groupingBy(WorkoutSet::getExerciseId));

        Set<UUID> exerciseIds = setsByExercise.keySet();
        Map<UUID, Exercise> exerciseMap = exerciseRepo.findAllByIdIn(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        List<ProgressiveOverloadDto> overloads = new ArrayList<>();

        for (Map.Entry<UUID, List<WorkoutSet>> entry : setsByExercise.entrySet()) {
            UUID exerciseId = entry.getKey();
            List<WorkoutSet> sets = entry.getValue();
            Exercise exercise = exerciseMap.get(exerciseId);

            if (exercise == null || sets.isEmpty()) continue;

            Map<LocalDate, BigDecimal> weightByDate = new TreeMap<>();

            for (WorkoutSet set : sets) {
                if (set.getWeight() == null || set.getReps() == null) continue;

                WorkoutSession session = sessionMap.get(set.getSessionId());
                if (session == null) continue;

                LocalDate date = session.getStartedAt().toLocalDate();
                BigDecimal current = weightByDate.getOrDefault(date, BigDecimal.ZERO);
                if (set.getWeight().compareTo(current) > 0) {
                    weightByDate.put(date, set.getWeight());
                }
            }

            if (weightByDate.isEmpty()) continue;

            List<ProgressiveOverloadDto.ProgressPoint> progressPoints = new ArrayList<>();
            BigDecimal firstWeight = null;
            BigDecimal lastWeight = null;

            for (Map.Entry<LocalDate, BigDecimal> dateEntry : weightByDate.entrySet()) {
                if (firstWeight == null) {
                    firstWeight = dateEntry.getValue();
                }
                lastWeight = dateEntry.getValue();

                List<WorkoutSet> setsForDate = sets.stream()
                        .filter(s -> sessionMap.get(s.getSessionId()).getStartedAt().toLocalDate().equals(dateEntry.getKey()))
                        .toList();

                BigDecimal maxWeight = setsForDate.stream()
                        .map(WorkoutSet::getWeight)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);

                Integer maxReps = setsForDate.stream()
                        .map(WorkoutSet::getReps)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo)
                        .orElse(0);

                if (progressPoints.isEmpty() || !progressPoints.get(progressPoints.size() - 1).weight().equals(maxWeight)) {
                    progressPoints.add(new ProgressiveOverloadDto.ProgressPoint(dateEntry.getKey(), maxWeight, maxReps));
                }
            }

            double progressPercent = 0.0;
            if (firstWeight != null && lastWeight != null && firstWeight.compareTo(BigDecimal.ZERO) > 0) {
                progressPercent = lastWeight.subtract(firstWeight).divide(firstWeight, 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue();
            }

            String status = "progressing";
            if (progressPoints.size() >= 2) {
                LocalDate lastProgressDate = progressPoints.get(progressPoints.size() - 1).date();
                long daysSinceProgress = ChronoUnit.DAYS.between(lastProgressDate, to);

                if (daysSinceProgress <= 14) {
                    status = "progressing";
                } else if (daysSinceProgress > 30) {
                    status = "plateau";
                }
            }

            overloads.add(new ProgressiveOverloadDto(
                    exerciseId,
                    exercise.getName(),
                    exercise.getPrimaryMuscle().name(),
                    firstWeight,
                    lastWeight,
                    Math.round(progressPercent * 10.0) / 10.0,
                    status,
                    progressPoints
            ));
        }

        overloads.sort((a, b) -> Double.compare(b.progressPercent(), a.progressPercent()));

        return overloads;
    }

    @Transactional
    public PersonalRecordsDto getPersonalRecords(UUID userId) {
        log.info("Getting personal records for user {} - checking for milestones", userId);
        List<WorkoutSession> allSessions = sessionRepo.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSession.SessionStatus.FINISHED);

        if (allSessions.isEmpty()) {
            List<Milestone> existingMilestones = milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId);
            List<PersonalRecordsDto.Milestone> milestones = existingMilestones.stream()
                    .map(m -> {
                        String icon = switch (m.getType()) {
                            case VOLUME -> "💪";
                            case CONSISTENCY -> "👑";
                            case STRENGTH -> "🏋️";
                            case ENDURANCE -> "🏃";
                            case PERSONAL_RECORD -> "🎯";
                        };
                        return new PersonalRecordsDto.Milestone(
                                m.getTitle(),
                                m.getDescription() != null ? m.getDescription() : "",
                                icon
                        );
                    })
                    .toList();
            return new PersonalRecordsDto(List.of(), milestones);
        }

        List<UUID> sessionIds = allSessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> allSets = setRepo.findAllBySessionIdIn(sessionIds);

        Map<UUID, WorkoutSession> sessionMap = allSessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        Map<UUID, List<WorkoutSet>> setsByExercise = allSets.stream()
                .collect(Collectors.groupingBy(WorkoutSet::getExerciseId));

        Set<UUID> exerciseIds = setsByExercise.keySet();
        Map<UUID, Exercise> exerciseMap = exerciseRepo.findAllByIdIn(exerciseIds).stream()
                .collect(Collectors.toMap(Exercise::getId, e -> e));

        List<PersonalRecordsDto.ExercisePR> exercisePRs = new ArrayList<>();

        for (Map.Entry<UUID, List<WorkoutSet>> entry : setsByExercise.entrySet()) {
            UUID exerciseId = entry.getKey();
            List<WorkoutSet> sets = entry.getValue();
            Exercise exercise = exerciseMap.get(exerciseId);

            if (exercise == null || sets.isEmpty()) continue;

            List<PersonalRecordsDto.ExercisePR> prs = new ArrayList<>();

            BigDecimal maxWeight = sets.stream()
                    .map(WorkoutSet::getWeight)
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            Integer maxReps = sets.stream()
                    .map(WorkoutSet::getReps)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);

            if (maxWeight.compareTo(BigDecimal.ZERO) > 0) {
                WorkoutSet maxSet = sets.stream()
                        .filter(s -> s.getWeight() != null && s.getWeight().equals(maxWeight))
                        .findFirst()
                        .orElse(null);

                if (maxSet != null) {
                    LocalDate achievedDate = sessionMap.get(maxSet.getSessionId()).getStartedAt().toLocalDate();
                    prs.add(new PersonalRecordsDto.ExercisePR(exerciseId, exercise.getName(), "Max Weight", maxWeight, maxSet.getReps(), achievedDate));
                }
            }

            if (maxReps > 0) {
                WorkoutSet maxRepSet = sets.stream()
                        .filter(s -> s.getReps() != null && s.getReps().equals(maxReps))
                        .findFirst()
                        .orElse(null);

                if (maxRepSet != null) {
                    LocalDate achievedDate = sessionMap.get(maxRepSet.getSessionId()).getStartedAt().toLocalDate();
                    prs.add(new PersonalRecordsDto.ExercisePR(exerciseId, exercise.getName(), "Max Reps", maxRepSet.getWeight(), maxReps, achievedDate));
                }
            }

            exercisePRs.addAll(prs);
        }

        BigDecimal totalVolume = allSets.stream()
                .filter(s -> s.getWeight() != null && s.getReps() != null)
                .map(s -> s.getWeight().multiply(BigDecimal.valueOf(s.getReps())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Milestone> existingMilestones = milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId);
        Map<String, Milestone> existingByTitle = existingMilestones.stream()
                .collect(Collectors.toMap(Milestone::getTitle, m -> m, (m1, m2) -> m1));

        LocalDate today = LocalDate.now();
        List<PersonalRecordsDto.Milestone> milestones = new ArrayList<>();

        Set<String> addedTitles = new HashSet<>();
        Set<String> expectedAutoTitles = new HashSet<>();

        if (allSessions.size() >= 25) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Getting Started", "25+ workout sessions completed", "🎯", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (allSessions.size() >= 50) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Dedicated (50 Sessions)", "50+ workout sessions completed", "💪", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (allSessions.size() >= 100) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Centurion", "100+ workout sessions completed", "🏋️", Milestone.MilestoneType.CONSISTENCY, today);
        }

        if (totalVolume.compareTo(BigDecimal.valueOf(100000)) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "100K Club", "Lifted 100,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }
        if (totalVolume.compareTo(BigDecimal.valueOf(500000)) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Half Million", "Lifted 500,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }
        if (totalVolume.compareTo(BigDecimal.valueOf(1000000)) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Million Pound Club", "Lifted 1,000,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }

        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(30);
        long recentWorkouts = allSessions.stream()
                .filter(s -> s.getStartedAt().toLocalDate().isAfter(thirtyDaysAgo))
                .count();

        if (recentWorkouts >= 12) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Dedicated (12 in 30)", "12+ workouts in 30 days", "🔥", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (recentWorkouts >= 20) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Consistency King", "20+ workouts in 30 days", "👑", Milestone.MilestoneType.CONSISTENCY, today);
        }
        
        log.info("Processed milestones for user {}: {} auto-generated milestones found", userId, milestones.size());
        
        existingMilestones = milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId);
        log.debug("Found {} existing milestones in database for user {}", existingMilestones.size(), userId);

        List<Milestone> toRemove = existingMilestones.stream()
                .filter(m ->
                        (m.isSystemGenerated() || AUTO_MILESTONE_TITLES.contains(m.getTitle()))
                                && !expectedAutoTitles.contains(m.getTitle()))
                .toList();
        if (!toRemove.isEmpty()) {
            log.info("Removing {} auto milestones for user {} that no longer meet requirements", toRemove.size(), userId);
            milestoneRepo.deleteAll(toRemove);
            milestoneRepo.flush();
            existingMilestones = existingMilestones.stream()
                    .filter(m -> !AUTO_MILESTONE_TITLES.contains(m.getTitle()) || expectedAutoTitles.contains(m.getTitle()))
                    .toList();
        }

        for (Milestone existing : existingMilestones) {
            String icon = switch (existing.getType()) {
                case VOLUME -> "💪";
                case CONSISTENCY -> "👑";
                case STRENGTH -> "🏋️";
                case ENDURANCE -> "🏃";
                case PERSONAL_RECORD -> "🎯";
            };
            
            if (!addedTitles.contains(existing.getTitle())) {
                milestones.add(new PersonalRecordsDto.Milestone(
                        existing.getTitle(),
                        existing.getDescription() != null ? existing.getDescription() : "",
                        icon
                ));
                addedTitles.add(existing.getTitle());
            }
        }

        return new PersonalRecordsDto(exercisePRs, milestones);
    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    private boolean saveAutoMilestone(UUID userId, String title, String description, LocalDate achievedDate, Milestone.MilestoneType type) {
        try {
            List<Milestone> existing = milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId);
            boolean exists = existing.stream()
                    .anyMatch(m -> m.getTitle().equals(title) && m.getUserId().equals(userId));
            
            if (!exists) {
                Milestone milestone = Milestone.builder()
                        .userId(userId)
                        .title(title)
                        .description(description)
                        .achievedDate(achievedDate)
                        .type(type)
                        .systemGenerated(true)
                        .build();
                Milestone saved = milestoneRepo.save(milestone);
                milestoneRepo.flush();
                log.info("✅ SAVED auto-generated milestone '{}' for user {} with id {} to analytics database", title, userId, saved.getId());
                log.info("Milestone details: title={}, description={}, date={}, type={}", 
                    saved.getTitle(), saved.getDescription(), saved.getAchievedDate(), saved.getType());
                return true;
            } else {
                log.debug("Milestone '{}' already exists for user {}, skipping save", title, userId);
            }
            return false;
        } catch (Exception e) {
            log.error("Error saving milestone '{}' for user {}: {}", title, userId, e.getMessage(), e);
            throw e;
        }
    }

    private void ensureAutoMilestone(UUID userId,
                                     Map<String, Milestone> existingByTitle,
                                     List<PersonalRecordsDto.Milestone> milestones,
                                     Set<String> addedTitles,
                                     Set<String> expectedAutoTitles,
                                     String title,
                                     String description,
                                     String icon,
                                     Milestone.MilestoneType type,
                                     LocalDate achievedDate) {
        if (!existingByTitle.containsKey(title)) {
            saveAutoMilestone(userId, title, description, achievedDate, type);
        }
        if (!addedTitles.contains(title)) {
            milestones.add(new PersonalRecordsDto.Milestone(title, description, icon));
            addedTitles.add(title);
        }
        expectedAutoTitles.add(title);
    }

        private static class DayAcc {
            int sessions = 0;
            int sets = 0;
            int reps = 0;
            BigDecimal volume = BigDecimal.ZERO;
        }

    @Transactional
    public MilestoneDto createMilestone(CreateMilestoneRequest request) {
        Milestone milestone = Milestone.builder()
                .userId(request.userId())
                .title(request.title())
                .description(request.description())
                .achievedDate(request.achievedDate())
                .type(Milestone.MilestoneType.valueOf(request.type().name()))
                .systemGenerated(false)
                .build();
        
        milestone = milestoneRepo.save(milestone);
        return new MilestoneDto(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getAchievedDate(),
                CreateMilestoneRequest.MilestoneType.valueOf(milestone.getType().name()),
                milestone.isSystemGenerated()
        );
    }

    public List<MilestoneDto> getUserMilestones(UUID userId) {
        return milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId).stream()
                .map(m -> new MilestoneDto(
                        m.getId(),
                        m.getTitle(),
                        m.getDescription(),
                        m.getAchievedDate(),
                        CreateMilestoneRequest.MilestoneType.valueOf(m.getType().name()),
                        m.isSystemGenerated()
                ))
                .toList();
    }

    @Transactional
    public MilestoneDto updateMilestone(UUID milestoneId, UUID userId, UpdateMilestoneRequest request) {
        Milestone milestone = milestoneRepo.findById(milestoneId)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found"));
        
        if (!milestone.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Milestone does not belong to user");
        }
        
        milestone.setTitle(request.title());
        milestone.setDescription(request.description());
        milestone.setAchievedDate(request.achievedDate());
        milestone.setType(Milestone.MilestoneType.valueOf(request.type().name()));
        
        milestone = milestoneRepo.save(milestone);
        return new MilestoneDto(
                milestone.getId(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getAchievedDate(),
                CreateMilestoneRequest.MilestoneType.valueOf(milestone.getType().name()),
                milestone.isSystemGenerated()
        );
    }

    @Transactional
    public void deleteMilestone(UUID milestoneId, UUID userId) {
        Milestone milestone = milestoneRepo.findById(milestoneId)
                .orElseThrow(() -> new IllegalArgumentException("Milestone not found"));
        
        if (!milestone.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Milestone does not belong to user");
        }

        if (milestone.isSystemGenerated() || AUTO_MILESTONE_TITLES.contains(milestone.getTitle())) {
            throw new IllegalArgumentException("System-generated milestones cannot be removed manually");
        }
        
        milestoneRepo.delete(milestone);
    }
    }

