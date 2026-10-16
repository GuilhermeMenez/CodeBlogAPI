package blog.code.codeblog.repository;

import blog.code.codeblog.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByFollowedIdAndReadFalseOrderByCreatedAtDesc(UUID followedId);

    boolean existsByFollowedIdAndFollowIdAndReadFalse(UUID followedId, UUID followId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.read = true WHERE n.followedId = :followedId AND n.read = false")
    int markAllAsReadByFollowedId(@Param("followedId") UUID followedId);

}
