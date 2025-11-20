package project.fitnessanalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.fitnessanalytics.model.milestone.Milestone;

import java.util.List;
import java.util.UUID;

public interface MilestoneRepository extends JpaRepository<Milestone, UUID> {
    List<Milestone> findByUserIdOrderByAchievedDateDesc(UUID userId);
}
