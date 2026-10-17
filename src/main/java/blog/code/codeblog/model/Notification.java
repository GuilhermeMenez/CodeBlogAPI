package blog.code.codeblog.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Table(name = "tb_notification",
    indexes = {
        @Index(name = "idx_notification_followed_read", columnList = "followed_id, is_read"),
        @Index(name = "idx_notification_followed_created_at", columnList = "followed_id, created_at DESC")
    }
)
@Entity
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "followed_id", nullable = false, columnDefinition = "uuid")
    private UUID followedId;

    @Column(name = "folowwer_id", nullable = false, columnDefinition = "uuid")
    private UUID followId;

    @Column(nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;


    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
