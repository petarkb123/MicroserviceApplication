package project.fitnessanalytics.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import project.fitnessanalytics.dto.*;
import project.fitnessanalytics.model.Equipment;
import project.fitnessanalytics.model.Exercise;
import project.fitnessanalytics.model.MuscleGroup;
import project.fitnessanalytics.model.WorkoutSession;
import project.fitnessanalytics.model.WorkoutSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import project.fitnessanalytics.model.milestone.Milestone;
import project.fitnessanalytics.repository.ExerciseRepository;
import project.fitnessanalytics.repository.MilestoneRepository;
import project.fitnessanalytics.repository.WeeklySummarySnapshotRepository;
import project.fitnessanalytics.repository.WorkoutSessionRepository;
import project.fitnessanalytics.repository.WorkoutSetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private WorkoutSessionRepository sessionRepo;

    @Mock
    private WorkoutSetRepository setRepo;

    @Mock
    private ExerciseRepository exerciseRepo;

    @Mock
    private MilestoneRepository milestoneRepo;

    @Mock
    private WeeklySummarySnapshotRepository weeklySummarySnapshotRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AnalyticsService analyticsService;

    private UUID userId;
    private Exercise exercise1, exercise2;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        exercise1 = new Exercise();
        exercise1.setId(UUID.randomUUID());
        exercise1.setName("Bench Press");
        exercise1.setPrimaryMuscle(MuscleGroup.CHEST);
        exercise1.setEquipment(Equipment.BARBELL);
        exercise1.setCreatedOn(LocalDateTime.now());

        exercise2 = new Exercise();
        exercise2.setId(UUID.randomUUID());
        exercise2.setName("Squats");
        exercise2.setPrimaryMuscle(MuscleGroup.LEGS);
        exercise2.setEquipment(Equipment.BARBELL);
        exercise2.setCreatedOn(LocalDateTime.now());
    }

    @Test
    void getWeeklyStats_returnsCorrectStats() {
        LocalDate start = LocalDate.now().minusDays(3);
        LocalDate end = LocalDate.now();

        WorkoutSession session1 = new WorkoutSession();
        session1.setId(UUID.randomUUID());
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(1));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        WorkoutSession session2 = new WorkoutSession();
        session2.setId(UUID.randomUUID());
        session2.setUserId(userId);
        session2.setStartedAt(LocalDateTime.now());
        session2.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(session1, session2));

        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Arrays.asList(
                createSet(session1.getId(), exercise1.getId(), 10, 100.0),
                createSet(session1.getId(), exercise1.getId(), 8, 100.0),
                createSet(session2.getId(), exercise2.getId(), 12, 150.0)
        ));

        var result = analyticsService.getWeeklyStats(userId, start, end);

        assertNotNull(result);
        assertEquals(start, result.from());
        assertEquals(end, result.to());
        assertTrue(result.days().size() > 0);
    }

    @Test
    void getWeeklyStats_withNoSessions_returnsEmptyStats() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        var result = analyticsService.getWeeklyStats(userId, start, end);

        assertNotNull(result);
        assertEquals(0, result.days().get(0).sessions());
    }

    @Test
    void getPersonalRecords_returnsCorrectPRs() {
        WorkoutSession session1 = new WorkoutSession();
        UUID sessionId = UUID.randomUUID();
        session1.setId(sessionId);
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(5));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSession.SessionStatus.FINISHED))
                .thenReturn(Arrays.asList(session1));

        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Arrays.asList(
                createSet(sessionId, exercise1.getId(), 10, 200.0),
                createSet(sessionId, exercise1.getId(), 15, 150.0)
        ));

        when(exerciseRepo.findAllByIdIn(anyCollection())).thenReturn(Arrays.asList(exercise1));

        var result = analyticsService.getPersonalRecords(userId);

        assertNotNull(result);
        assertFalse(result.exercisePRs().isEmpty());
    }

    @Test
    void getPersonalRecords_withNoData_returnsEmpty() {
        when(sessionRepo.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSession.SessionStatus.FINISHED))
                .thenReturn(Collections.emptyList());

        var result = analyticsService.getPersonalRecords(userId);

        assertNotNull(result);
        assertTrue(result.exercisePRs().isEmpty());
        assertTrue(result.milestones().isEmpty());
    }

    @Test
    void getTrainingFrequency_calculatesCorrectFrequency() {
        LocalDate start = LocalDate.now().minusDays(14);
        LocalDate end = LocalDate.now();

        WorkoutSession session1 = new WorkoutSession();
        session1.setId(UUID.randomUUID());
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(5));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(session1));

        var result = analyticsService.getTrainingFrequency(userId, start, end);

        assertNotNull(result);
        assertEquals(1, result.totalWorkouts());
    }

    @Test
    void getTrainingFrequency_withNoWorkouts_returnsZeroFrequency() {
        LocalDate start = LocalDate.now().minusDays(14);
        LocalDate end = LocalDate.now();

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        var result = analyticsService.getTrainingFrequency(userId, start, end);

        assertNotNull(result);
        assertEquals(0, result.totalWorkouts());
    }

    @Test
    void getSessionSummaries_returnsCorrectSummaries() {
        LocalDate start = LocalDate.now().minusDays(7);
        LocalDate end = LocalDate.now();

        WorkoutSession session1 = new WorkoutSession();
        UUID sessionId1 = UUID.randomUUID();
        session1.setId(sessionId1);
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(2));
        session1.setFinishedAt(LocalDateTime.now().minusDays(2).plusHours(1));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(session1));

        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Arrays.asList(
                createSet(sessionId1, exercise1.getId(), 10, 100.0),
                createSet(sessionId1, exercise1.getId(), 8, 100.0)
        ));

        var result = analyticsService.getSessionSummaries(userId, start, end);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void getExerciseVolumeTrends_returnsTrends() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();

        WorkoutSession session1 = new WorkoutSession();
        UUID sessionId1 = UUID.randomUUID();
        session1.setId(sessionId1);
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(10));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(session1));

        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Arrays.asList(
                createSet(sessionId1, exercise1.getId(), 10, 200.0),
                createSet(sessionId1, exercise1.getId(), 8, 180.0)
        ));

        when(exerciseRepo.findAllByIdIn(anyCollection())).thenReturn(Arrays.asList(exercise1));

        var result = analyticsService.getExerciseVolumeTrends(userId, start, end);

        assertNotNull(result);
    }

    @Test
    void getProgressiveOverload_returnsProgress() {
        LocalDate start = LocalDate.now().minusDays(60);
        LocalDate end = LocalDate.now();

        WorkoutSession session1 = new WorkoutSession();
        UUID sessionId1 = UUID.randomUUID();
        session1.setId(sessionId1);
        session1.setUserId(userId);
        session1.setStartedAt(LocalDateTime.now().minusDays(20));
        session1.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(any(), any(), any(), any()))
                .thenReturn(Arrays.asList(session1));

        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Arrays.asList(
                createSet(sessionId1, exercise1.getId(), 10, 200.0)
        ));

        when(exerciseRepo.findAllByIdIn(anyCollection())).thenReturn(Arrays.asList(exercise1));

        var result = analyticsService.getProgressiveOverload(userId, start, end);

        assertNotNull(result);
    }

    @Test
    void recomputeWeeklyStats_savesSnapshot() throws Exception {
        LocalDate start = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        LocalDate end = start.plusDays(6);

        WorkoutSession session = new WorkoutSession();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStartedAt(start.atStartOfDay().plusHours(1));
        session.setStatus(WorkoutSession.SessionStatus.FINISHED);

        when(sessionRepo.findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(eq(userId), eq(WorkoutSession.SessionStatus.FINISHED), any(), any()))
                .thenReturn(List.of(session));
        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(List.of(
                createSet(session.getId(), exercise1.getId(), 8, 120.0)
        ));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(weeklySummarySnapshotRepository.findByUserIdAndWeekStart(userId, start)).thenReturn(Optional.empty());

        RecomputeWeeklyRequest request = new RecomputeWeeklyRequest(start, end);
        WeeklySummaryResponse response = analyticsService.recomputeWeeklyStats(userId, request);

        assertNotNull(response);
        verify(weeklySummarySnapshotRepository).save(any());
    }

    private WorkoutSet createSet(UUID sessionId, UUID exerciseId, int reps, double weight) {
        WorkoutSet set = new WorkoutSet();
        set.setId(UUID.randomUUID());
        set.setSessionId(sessionId);
        set.setExerciseId(exerciseId);
        set.setReps(reps);
        set.setWeight(BigDecimal.valueOf(weight));
        return set;
    }

    @Test
    void createMilestone_success() {
        CreateMilestoneRequest request = new CreateMilestoneRequest(
                userId,
                "First 100kg bench",
                "Achieved bench press milestone",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD
        );

        Milestone milestone = Milestone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("First 100kg bench")
                .description("Achieved bench press milestone")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.PERSONAL_RECORD)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.save(any(Milestone.class))).thenReturn(milestone);

        MilestoneDto result = analyticsService.createMilestone(userId, request);

        assertNotNull(result);
        assertEquals("First 100kg bench", result.title());
        assertEquals(userId, request.userId());
    }

    @Test
    void getUserMilestones_returnsList() {
        Milestone milestone1 = Milestone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("First 100kg bench")
                .description("Achieved bench press milestone")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.PERSONAL_RECORD)
                .systemGenerated(false)
                .build();

        Milestone milestone2 = Milestone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("10 weeks in a row")
                .description("Consistent training")
                .achievedDate(LocalDate.now().minusDays(10))
                .type(Milestone.MilestoneType.CONSISTENCY)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId))
                .thenReturn(Arrays.asList(milestone1, milestone2));

        List<MilestoneDto> result = analyticsService.getUserMilestones(userId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("First 100kg bench", result.get(0).title());
    }

    @Test
    void updateMilestone_success() {
        UUID milestoneId = UUID.randomUUID();
        UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                "Updated title",
                "Updated description",
                LocalDate.now().plusDays(1),
                CreateMilestoneRequest.MilestoneType.CONSISTENCY
        );

        Milestone milestone = Milestone.builder()
                .id(milestoneId)
                .userId(userId)
                .title("Original title")
                .description("Original description")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.PERSONAL_RECORD)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.of(milestone));
        when(milestoneRepo.save(any(Milestone.class))).thenReturn(milestone);

        MilestoneDto result = analyticsService.updateMilestone(milestoneId, userId, request);

        assertNotNull(result);
        assertEquals("Updated title", result.title());
        verify(milestoneRepo).save(any(Milestone.class));
    }

    @Test
    void updateMilestone_notFound_throwsException() {
        UUID milestoneId = UUID.randomUUID();
        UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                "Updated title",
                "Updated description",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD
        );

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.empty());

        assertThrows(project.fitnessanalytics.common.exception.ResourceNotFoundException.class, () ->
                analyticsService.updateMilestone(milestoneId, userId, request));
        verify(milestoneRepo, never()).save(any(Milestone.class));
    }

    @Test
    void updateMilestone_wrongUser_throwsException() {
        UUID milestoneId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UpdateMilestoneRequest request = new UpdateMilestoneRequest(
                "Updated title",
                "Updated description",
                LocalDate.now(),
                CreateMilestoneRequest.MilestoneType.PERSONAL_RECORD
        );

        Milestone milestone = Milestone.builder()
                .id(milestoneId)
                .userId(otherUserId)
                .title("Original title")
                .description("Original description")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.PERSONAL_RECORD)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.of(milestone));

        assertThrows(project.fitnessanalytics.common.exception.UnauthorizedOperationException.class, () ->
                analyticsService.updateMilestone(milestoneId, userId, request));
        verify(milestoneRepo, never()).save(any(Milestone.class));
    }

    @Test
    void deleteMilestone_success() {
        UUID milestoneId = UUID.randomUUID();
        Milestone milestone = Milestone.builder()
                .id(milestoneId)
                .userId(userId)
                .title("Milestone to delete")
                .description("Description")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.VOLUME)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.of(milestone));
        doNothing().when(milestoneRepo).delete(any(Milestone.class));

        analyticsService.deleteMilestone(milestoneId, userId);

        verify(milestoneRepo).delete(milestone);
    }

    @Test
    void deleteMilestone_notFound_throwsException() {
        UUID milestoneId = UUID.randomUUID();
        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.empty());

        assertThrows(project.fitnessanalytics.common.exception.ResourceNotFoundException.class, () ->
                analyticsService.deleteMilestone(milestoneId, userId));
        verify(milestoneRepo, never()).delete(any(Milestone.class));
    }

    @Test
    void deleteMilestone_wrongUser_throwsException() {
        UUID milestoneId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Milestone milestone = Milestone.builder()
                .id(milestoneId)
                .userId(otherUserId)
                .title("Milestone to delete")
                .description("Description")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.VOLUME)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.of(milestone));

        assertThrows(project.fitnessanalytics.common.exception.UnauthorizedOperationException.class, () ->
                analyticsService.deleteMilestone(milestoneId, userId));
        verify(milestoneRepo, never()).delete(any(Milestone.class));
    }

    @Test
    void deleteMilestone_systemGenerated_throwsException() {
        UUID milestoneId = UUID.randomUUID();
        Milestone milestone = Milestone.builder()
                .id(milestoneId)
                .userId(userId)
                .title("Centurion")
                .description("100+ workout sessions completed")
                .achievedDate(LocalDate.now())
                .type(Milestone.MilestoneType.CONSISTENCY)
                .systemGenerated(true)
                .build();

        when(milestoneRepo.findById(milestoneId)).thenReturn(Optional.of(milestone));

        assertThrows(project.fitnessanalytics.common.exception.UnauthorizedOperationException.class, () ->
                analyticsService.deleteMilestone(milestoneId, userId));
        verify(milestoneRepo, never()).delete(any(Milestone.class));
    }

    @Test
    void autoMilestonesRemovedWhenRequirementsDrop() {
        List<WorkoutSession> sessions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            WorkoutSession session = new WorkoutSession();
            session.setId(UUID.randomUUID());
            session.setUserId(userId);
            session.setStatus(WorkoutSession.SessionStatus.FINISHED);
            session.setStartedAt(LocalDateTime.now().minusDays(i));
            sessions.add(session);
        }

        when(sessionRepo.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSession.SessionStatus.FINISHED)).thenReturn(sessions);
        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(Collections.emptyList());
        when(exerciseRepo.findAllByIdIn(anyCollection())).thenReturn(Collections.emptyList());

        Milestone autoMilestone = Milestone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Getting Started")
                .description("25+ workout sessions completed")
                .achievedDate(LocalDate.now().minusDays(5))
                .type(Milestone.MilestoneType.CONSISTENCY)
                .systemGenerated(true)
                .build();

        Milestone customMilestone = Milestone.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("Custom Goal")
                .description("My personal goal")
                .achievedDate(LocalDate.now().minusDays(2))
                .type(Milestone.MilestoneType.PERSONAL_RECORD)
                .systemGenerated(false)
                .build();

        when(milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId))
                .thenReturn(List.of(autoMilestone, customMilestone),
                        List.of(autoMilestone, customMilestone));

        analyticsService.getPersonalRecords(userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Milestone>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(milestoneRepo).deleteAll(captor.capture());
        verify(milestoneRepo).flush();

        List<Milestone> removed = new ArrayList<>();
        captor.getValue().forEach(removed::add);

        assertEquals(1, removed.size());
        assertEquals("Getting Started", removed.get(0).getTitle());
    }

    @Test
    void getPersonalRecords_highVolume_addsAllAutoMilestones() {
        List<WorkoutSession> sessions = new ArrayList<>();
        List<WorkoutSet> sets = new ArrayList<>();
        Exercise exercise = new Exercise();
        exercise.setId(UUID.randomUUID());
        exercise.setName("Bench Press");
        exercise.setPrimaryMuscle(MuscleGroup.CHEST);
        exercise.setEquipment(Equipment.BARBELL);

        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < 120; i++) {
            WorkoutSession session = new WorkoutSession();
            session.setId(UUID.randomUUID());
            session.setUserId(userId);
            session.setStatus(WorkoutSession.SessionStatus.FINISHED);
            session.setStartedAt(now.minusDays(i % 28));
            sessions.add(session);

            WorkoutSet set = new WorkoutSet();
            set.setId(UUID.randomUUID());
            set.setSessionId(session.getId());
            set.setExerciseId(exercise.getId());
            set.setReps(10);
            set.setWeight(BigDecimal.valueOf(1000));
            sets.add(set);
        }

        when(sessionRepo.findByUserIdAndStatusOrderByStartedAtDesc(userId, WorkoutSession.SessionStatus.FINISHED))
                .thenReturn(sessions);
        when(setRepo.findAllBySessionIdIn(anyList())).thenReturn(sets);
        when(exerciseRepo.findAllByIdIn(anyCollection())).thenReturn(List.of(exercise));
        when(milestoneRepo.findByUserIdOrderByAchievedDateDesc(userId)).thenReturn(Collections.emptyList());
        when(milestoneRepo.save(any(Milestone.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PersonalRecordsDto dto = analyticsService.getPersonalRecords(userId);

        List<String> titles = dto.milestones().stream()
                .map(PersonalRecordsDto.Milestone::title)
                .toList();

        assertTrue(titles.containsAll(List.of(
                "Getting Started",
                "Dedicated (50 Sessions)",
                "Centurion",
                "100K Club",
                "Half Million",
                "Million Pound Club",
                "Dedicated (12 in 30)",
                "Consistency King"
        )));
    }
}

