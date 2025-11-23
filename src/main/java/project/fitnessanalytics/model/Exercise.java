package project.fitnessanalytics.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercises")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder @EqualsAndHashCode(of = "id")
public class Exercise {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", columnDefinition = "char(36)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "owner_user_id", columnDefinition = "char(36)")
    private UUID ownerUserId;

    @Column(length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_muscle", length = 40)
    private MuscleGroup primaryMuscle;

    @Enumerated(EnumType.STRING)
    @Column(name = "equipment", length = 32)
    private Equipment equipment;

    @Column(name = "created_on")
    private LocalDateTime createdOn;
}