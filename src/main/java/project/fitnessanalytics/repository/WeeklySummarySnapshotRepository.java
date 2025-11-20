package project.fitnessanalytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import project.fitnessanalytics.model.summary.WeeklySummarySnapshot;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WeeklySummarySnapshotRepository extends JpaRepository<WeeklySummarySnapshot, UUID> {
    Optional<WeeklySummarySnapshot> findByUserIdAndWeekStart(UUID userId, LocalDate weekStart);
}


