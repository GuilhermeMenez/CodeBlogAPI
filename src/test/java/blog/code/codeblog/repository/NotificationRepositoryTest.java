package blog.code.codeblog.repository;

import blog.code.codeblog.model.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureCache
@ActiveProfiles("test")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("Deve buscar apenas as notificações não lidas do usuário, da mais recente para a mais antiga")
    void findUnreadByFollowedIdOrderedByCreatedAtDesc() {
        UUID followedId = UUID.randomUUID();
        Notification older = save(followedId, "older", false);
        Notification newer = save(followedId, "newer", false);
        save(followedId, "already read", true);
        save(UUID.randomUUID(), "other user", false);

        setCreatedAt(older, LocalDateTime.now().minusMinutes(5));
        setCreatedAt(newer, LocalDateTime.now());

        List<Notification> result = notificationRepository.findByFollowedIdAndReadFalseOrderByCreatedAtDesc(followedId);

        assertEquals(List.of("newer", "older"), result.stream().map(Notification::getMessage).toList());
    }

    @Test
    @DisplayName("Deve marcar como lidas apenas as notificações não lidas do usuário")
    void markAllAsReadByFollowedId() {
        UUID followedId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        save(followedId, "first", false);
        save(followedId, "second", false);
        save(followedId, "already read", true);
        save(otherUserId, "other user", false);

        int updated = notificationRepository.markAllAsReadByFollowedId(followedId);

        assertEquals(2, updated);
        assertTrue(notificationRepository.findByFollowedIdAndReadFalseOrderByCreatedAtDesc(followedId).isEmpty());
        assertEquals(1, notificationRepository.findByFollowedIdAndReadFalseOrderByCreatedAtDesc(otherUserId).size());
    }

    private Notification save(UUID followedId, String message, boolean read) {
        return notificationRepository.saveAndFlush(Notification.builder()
                .followedId(followedId)
                .followId(UUID.randomUUID())
                .message(message)
                .read(read)
                .build());
    }

    private void setCreatedAt(Notification notification, LocalDateTime createdAt) {
        notification.setCreatedAt(createdAt);
        notificationRepository.saveAndFlush(notification);
    }
}
