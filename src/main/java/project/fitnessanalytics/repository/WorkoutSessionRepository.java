package project.fitnessanalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.fitnessanalytics.model.WorkoutSession;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, UUID> {
    List<WorkoutSession> findByUserIdAndStatusAndStartedAtBetweenOrderByStartedAtAsc(UUID userId, WorkoutSession.SessionStatus status, LocalDateTime from, LocalDateTime to);
    List<WorkoutSession> findByUserIdAndStatusOrderByStartedAtDesc(UUID userId, WorkoutSession.SessionStatus status);
}
