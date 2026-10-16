package blog.code.codeblog.service;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.model.Notification;
import blog.code.codeblog.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SseNotificationHub sseNotificationHub;

    @InjectMocks
    private NotificationService notificationService;

    private UUID followedId;
    private UUID followerId;

    @BeforeEach
    void setUp() {
        followedId = UUID.randomUUID();
        followerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("notifyNewFollower saves the notification and pushes it to the followed user")
    void notifyNewFollowerSavesAndPushes() {
        UUID notificationId = UUID.randomUUID();
        when(notificationRepository.existsByFollowedIdAndFollowIdAndReadFalse(followedId, followerId)).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(notificationId);
            n.setCreatedAt(LocalDateTime.now());
            return n;
        });

        notificationService.notifyNewFollower(followedId, followerId, "Fulano");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertEquals(followedId, saved.getValue().getFollowedId());
        assertEquals(followerId, saved.getValue().getFollowId());
        assertEquals("Fulano começou a te seguir", saved.getValue().getMessage());
        assertFalse(saved.getValue().isRead());

        ArgumentCaptor<NotificationResponseDTO> pushed = ArgumentCaptor.forClass(NotificationResponseDTO.class);
        verify(sseNotificationHub).push(eq(followedId), pushed.capture());
        assertEquals(notificationId, pushed.getValue().id());
        assertEquals(followerId, pushed.getValue().followerId());
        assertEquals("Fulano começou a te seguir", pushed.getValue().message());
    }

    @Test
    @DisplayName("notifyNewFollower ignores a duplicated event when an unread notification already exists")
    void notifyNewFollowerIgnoresDuplicate() {
        when(notificationRepository.existsByFollowedIdAndFollowIdAndReadFalse(followedId, followerId)).thenReturn(true);

        notificationService.notifyNewFollower(followedId, followerId, "Fulano");

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(sseNotificationHub);
    }

    @Test
    @DisplayName("subscribe delegates to the hub")
    void subscribeDelegatesToHub() {
        SseEmitter emitter = new SseEmitter();
        when(sseNotificationHub.subscribe(followedId)).thenReturn(emitter);

        assertSame(emitter, notificationService.subscribe(followedId));
    }

    @Test
    @DisplayName("getUnread maps the unread notifications to DTOs keeping the order")
    void getUnreadMapsToDto() {
        Notification newer = notification("newer");
        Notification older = notification("older");
        when(notificationRepository.findByFollowedIdAndReadFalseOrderByCreatedAtDesc(followedId))
                .thenReturn(List.of(newer, older));

        List<NotificationResponseDTO> result = notificationService.getUnread(followedId);

        assertEquals(List.of("newer", "older"), result.stream().map(NotificationResponseDTO::message).toList());
        assertEquals(newer.getId(), result.getFirst().id());
    }

    @Test
    @DisplayName("markAllAsRead delegates to the repository")
    void markAllAsReadDelegatesToRepository() {
        when(notificationRepository.markAllAsReadByFollowedId(followedId)).thenReturn(2);

        notificationService.markAllAsRead(followedId);

        verify(notificationRepository).markAllAsReadByFollowedId(followedId);
    }

    private Notification notification(String message) {
        return Notification.builder()
                .id(UUID.randomUUID())
                .followedId(followedId)
                .followId(followerId)
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
