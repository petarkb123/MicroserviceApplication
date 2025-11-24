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
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsService {

    private final WorkoutSessionRepository sessionRepo;
    private final WorkoutSetRepository setRepo;
    private final ExerciseRepository exerciseRepo;
    private final MilestoneRepository milestoneRepo;
    private final WeeklySummarySnapshotRepository weeklySummaryRepo;
    private final ObjectMapper objectMapper;

    // Milestone thresholds
    private static final int MILESTONE_SESSIONS_GETTING_STARTED = 25;
    private static final int MILESTONE_SESSIONS_DEDICATED = 50;
    private static final int MILESTONE_SESSIONS_CENTURION = 100;
    private static final int MILESTONE_RECENT_WORKOUTS_DEDICATED = 12;
    private static final int MILESTONE_RECENT_WORKOUTS_CONSISTENCY_KING = 20;
    private static final int RECENT_WORKOUTS_DAYS = 30;
    private static final int PLATEAU_THRESHOLD_DAYS = 14;
    private static final int PROGRESSING_THRESHOLD_DAYS = 30;

    private static final BigDecimal VOLUME_100K = BigDecimal.valueOf(100_000);
    private static final BigDecimal VOLUME_500K = BigDecimal.valueOf(500_000);
    private static final BigDecimal VOLUME_1M = BigDecimal.valueOf(1_000_000);

    // Trend calculation thresholds
    private static final BigDecimal TREND_INCREASE_THRESHOLD = BigDecimal.valueOf(1.1);
    private static final BigDecimal TREND_DECREASE_THRESHOLD = BigDecimal.valueOf(0.9);

    private static final Set<String> AUTO_MILESTONE_TITLES = Set.of(
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
        List<WorkoutSession> sessions = findFinishedSessionsInRange(userId, from, to);

        Map<UUID, WorkoutSession> byId = sessions.stream()
                .collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        List<UUID> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();
        List<WorkoutSet> sets = sessionIds.isEmpty() ? List.of() : setRepo.findAllBySessionIdIn(sessionIds);

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
            acc.volume = acc.volume.add(calculateSetVolume(ws));
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
        BigDecimal totalVolume = summary.days().stream()
                .map(WeeklySummaryResponse.DayStat::volume)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        WeeklySummarySnapshot snapshot = weeklySummaryRepo
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

        weeklySummaryRepo.save(snapshot);
        return summary;
    }

    public List<SessionSummaryResponse> getSessionSummaries(UUID userId, LocalDate from, LocalDate to) {
        List<WorkoutSession> sessions = findFinishedSessionsInRange(userId, from, to);

        Map<UUID, List<WorkoutSet>> setsBySession = setRepo.findAllBySessionIdIn(
                        sessions.stream().map(WorkoutSession::getId).toList())
                .stream().collect(Collectors.groupingBy(WorkoutSet::getSessionId));

        List<SessionSummaryResponse> out = new ArrayList<>();
        for (WorkoutSession s : sessions) {
            List<WorkoutSet> sets = setsBySession.getOrDefault(s.getId(), List.of());
            int totalSets = sets.size();
            int totalReps = sets.stream().filter(x -> x.getReps() != null).mapToInt(WorkoutSet::getReps).sum();
            BigDecimal volume = calculateTotalVolume(sets);

            out.add(new SessionSummaryResponse(s.getId(), s.getStartedAt(), s.getFinishedAt(), totalSets, totalReps, volume));
        }
        return out;
    }

    public TrainingFrequencyResponse getTrainingFrequency(UUID userId, LocalDate from, LocalDate to) {
        List<WorkoutSession> sessions = findFinishedSessionsInRange(userId, from, to);

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
        List<WorkoutSession> sessions = findFinishedSessionsInRange(userId, from, to);

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

                BigDecimal setVolume = calculateSetVolume(set);

                weeklyVolume.merge(weekStart, setVolume, BigDecimal::add);
                weeklySets.merge(weekStart, 1, Integer::sum);
            }

            BigDecimal totalVolume = calculateTotalVolume(sets);

            int totalSets = sets.size();
            BigDecimal avgVolumePerSession = weeklySets.isEmpty() ? BigDecimal.ZERO : totalVolume.divide(
                    BigDecimal.valueOf(weeklySets.size()),
                    2,
                    RoundingMode.HALF_UP
            );

            String trend = "stable";
            List<LocalDate> weeks = new ArrayList<>(weeklyVolume.keySet());
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

                if (secondHalfAvg.compareTo(firstHalfAvg.multiply(TREND_INCREASE_THRESHOLD)) > 0) {
                    trend = "increasing";
                } else if (secondHalfAvg.compareTo(firstHalfAvg.multiply(TREND_DECREASE_THRESHOLD)) < 0) {
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
        List<WorkoutSession> sessions = findFinishedSessionsInRange(userId, from, to);

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

                if (daysSinceProgress <= PLATEAU_THRESHOLD_DAYS) {
                    status = "progressing";
                } else if (daysSinceProgress > PROGRESSING_THRESHOLD_DAYS) {
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
                    .map(m -> new PersonalRecordsDto.Milestone(
                                m.getTitle(),
                                m.getDescription() != null ? m.getDescription() : "",
                            getMilestoneIcon(m.getType())
                    ))
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

            findMaxWeightPR(sets, sessionMap, exerciseId, exercise.getName(), prs);
            findMaxRepsPR(sets, sessionMap, exerciseId, exercise.getName(), prs);

            exercisePRs.addAll(prs);
        }

        BigDecimal totalVolume = calculateTotalVolume(allSets);

        List<Milestone> existingMilestones = milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId);
        Map<String, Milestone> existingByTitle = existingMilestones.stream()
                .collect(Collectors.toMap(Milestone::getTitle, m -> m, (m1, m2) -> m1));

        LocalDate today = LocalDate.now();
        List<PersonalRecordsDto.Milestone> milestones = new ArrayList<>();

        Set<String> addedTitles = new HashSet<>();
        Set<String> expectedAutoTitles = new HashSet<>();

        // Check session count milestones
        if (allSessions.size() >= MILESTONE_SESSIONS_GETTING_STARTED) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Getting Started", "25+ workout sessions completed", "🎯", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (allSessions.size() >= MILESTONE_SESSIONS_DEDICATED) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Dedicated (50 Sessions)", "50+ workout sessions completed", "💪", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (allSessions.size() >= MILESTONE_SESSIONS_CENTURION) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Centurion", "100+ workout sessions completed", "🏋️", Milestone.MilestoneType.CONSISTENCY, today);
        }

        // Check volume milestones
        if (totalVolume.compareTo(VOLUME_100K) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "100K Club", "Lifted 100,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }
        if (totalVolume.compareTo(VOLUME_500K) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Half Million", "Lifted 500,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }
        if (totalVolume.compareTo(VOLUME_1M) >= 0) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Million Pound Club", "Lifted 1,000,000+ lbs total", "💪", Milestone.MilestoneType.VOLUME, today);
        }

        // Check recent workout milestones
        LocalDate thirtyDaysAgo = LocalDate.now().minusDays(RECENT_WORKOUTS_DAYS);
        long recentWorkouts = allSessions.stream()
                .filter(s -> s.getStartedAt().toLocalDate().isAfter(thirtyDaysAgo))
                .count();

        if (recentWorkouts >= MILESTONE_RECENT_WORKOUTS_DEDICATED) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Dedicated (12 in 30)", "12+ workouts in 30 days", "🔥", Milestone.MilestoneType.CONSISTENCY, today);
        }
        if (recentWorkouts >= MILESTONE_RECENT_WORKOUTS_CONSISTENCY_KING) {
            ensureAutoMilestone(userId, existingByTitle, milestones, addedTitles, expectedAutoTitles,
                    "Consistency King", "20+ workouts in 30 days", "👑", Milestone.MilestoneType.CONSISTENCY, today);
        }
        
        log.info("Processed milestones for user {}: {} auto-generated milestones found", userId, milestones.size());
        // TODO: Consider caching milestone calculations for better performance
        
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
            if (!addedTitles.contains(existing.getTitle())) {
                milestones.add(new PersonalRecordsDto.Milestone(
                        existing.getTitle(),
                        existing.getDescription() != null ? existing.getDescription() : "",
                        getMilestoneIcon(existing.getType())
                ));
                addedTitles.add(existing.getTitle());
            }
        }

        return new PersonalRecordsDto(exercisePRs, milestones);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
                log.info("Saved auto-generated milestone '{}' for user {} with id {}", title, userId, saved.getId());
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

    private List<WorkoutSession> findFinishedSessionsInRange(UUID userId, LocalDate from, LocalDate to) {
        LocalDateTime fromTs = from.atStartOfDay();
        LocalDateTime toTs = to.plusDays(1).atStartOfDay().minusNanos(1);
        return sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(
                userId, WorkoutSession.SessionStatus.FINISHED, fromTs, toTs);
    }

    private String getMilestoneIcon(Milestone.MilestoneType type) {
        return switch (type) {
            case VOLUME -> "💪";
            case CONSISTENCY -> "👑";
            case STRENGTH -> "🏋️";
            case ENDURANCE -> "🏃";
            case PERSONAL_RECORD -> "🎯";
        };
    }

    private static BigDecimal calculateSetVolume(WorkoutSet set) {
        if (set.getWeight() == null || set.getReps() == null) {
            return BigDecimal.ZERO;
        }
        return set.getWeight().multiply(BigDecimal.valueOf(set.getReps()));
    }

    private static BigDecimal calculateTotalVolume(List<WorkoutSet> sets) {
        return sets.stream()
                .map(AnalyticsService::calculateSetVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void findMaxWeightPR(List<WorkoutSet> sets, Map<UUID, WorkoutSession> sessionMap,
                                  UUID exerciseId, String exerciseName, List<PersonalRecordsDto.ExercisePR> prs) {
        BigDecimal maxWeight = sets.stream()
                .map(WorkoutSet::getWeight)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        if (maxWeight.compareTo(BigDecimal.ZERO) > 0) {
            sets.stream()
                    .filter(s -> s.getWeight() != null && s.getWeight().equals(maxWeight))
                    .findFirst()
                    .ifPresent(maxSet -> {
                        LocalDate achievedDate = sessionMap.get(maxSet.getSessionId()).getStartedAt().toLocalDate();
                        prs.add(new PersonalRecordsDto.ExercisePR(
                                exerciseId, exerciseName, "Max Weight", maxWeight, maxSet.getReps(), achievedDate));
                    });
        }
    }

    private void findMaxRepsPR(List<WorkoutSet> sets, Map<UUID, WorkoutSession> sessionMap,
                                UUID exerciseId, String exerciseName, List<PersonalRecordsDto.ExercisePR> prs) {
        Integer maxReps = sets.stream()
                .map(WorkoutSet::getReps)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);

        if (maxReps > 0) {
            sets.stream()
                    .filter(s -> s.getReps() != null && s.getReps().equals(maxReps))
                    .findFirst()
                    .ifPresent(maxRepSet -> {
                        LocalDate achievedDate = sessionMap.get(maxRepSet.getSessionId()).getStartedAt().toLocalDate();
                        prs.add(new PersonalRecordsDto.ExercisePR(
                                exerciseId, exerciseName, "Max Reps", maxRepSet.getWeight(), maxReps, achievedDate));
                    });
        }
    }


    private static class DayAcc {
        int sessions = 0;
        int sets = 0;
        int reps = 0;
        BigDecimal volume = BigDecimal.ZERO;
    }
}
