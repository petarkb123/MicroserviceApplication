package project.fitnessanalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.fitnessanalytics.model.Exercise;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findAllByIdIn(Collection<UUID> ids);
}
