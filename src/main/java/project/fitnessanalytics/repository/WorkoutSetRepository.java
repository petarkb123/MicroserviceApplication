package project.fitnessanalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.fitnessanalytics.model.WorkoutSet;
import java.util.List;
import java.util.UUID;

public interface WorkoutSetRepository extends JpaRepository<WorkoutSet, UUID> {
    List<WorkoutSet> findAllBySessionIdIn(List<UUID> sessionIds);
    void deleteBySessionId(UUID sessionId);
    void deleteByExerciseId(UUID exerciseId);
}
