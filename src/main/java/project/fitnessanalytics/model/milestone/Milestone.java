package project.fitnessanalytics.model.milestone;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milestones",
        indexes = @Index(name = "ix_milestone_user", columnList = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder @EqualsAndHashCode(of = "id")
public class Milestone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "char(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "user_id", nullable = false, columnDefinition = "char(36)")
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "achieved_date", nullable = false)
    private LocalDate achievedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone_type", length = 50)
    private MilestoneType type;

    @Builder.Default
    @Column(name = "system_generated", nullable = false, columnDefinition = "tinyint(1) default 0")
    private boolean systemGenerated = false;

    public enum MilestoneType {
        VOLUME, CONSISTENCY, STRENGTH, ENDURANCE, PERSONAL_RECORD
    }
}

