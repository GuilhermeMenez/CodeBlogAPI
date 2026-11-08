package blog.code.codeblog.service;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SseNotificationHubTest {

    private SseNotificationHub hub;
    private UUID userId;
    private NotificationResponseDTO payload;

    @BeforeEach
    void setUp() {
        hub = new SseNotificationHub();
        userId = UUID.randomUUID();
        payload = NotificationResponseDTO.builder()
                .id(UUID.randomUUID())
                .followerId(UUID.randomUUID())
                .message("Fulano começou a te seguir")
                .createdAt(LocalDateTime.now())
                .read(false)
                .build();
    }

    @Test
    @DisplayName("subscribe returns an emitter with a 30 minute timeout")
    void subscribeReturnsEmitterWithTimeout() {
        SseEmitter emitter = hub.subscribe(userId);

        assertNotNull(emitter);
        assertEquals(Duration.ofMinutes(30).toMillis(), emitter.getTimeout());
    }

    @Test
    @DisplayName("push to a user without open connections does nothing")
    void pushWithoutConnectionsDoesNotThrow() {
        assertDoesNotThrow(() -> hub.push(userId, payload));
    }

    @Test
    @DisplayName("push to an open connection does not throw")
    void pushToOpenConnectionDoesNotThrow() {
        hub.subscribe(userId);

        assertDoesNotThrow(() -> hub.push(userId, payload));
    }

    @Test
    @DisplayName("push and heartbeat swallow failures from already completed emitters")
    void pushAndHeartbeatSwallowCompletedEmitterFailures() {
        hub.subscribe(userId).complete();
        hub.subscribe(UUID.randomUUID());

        assertDoesNotThrow(() -> hub.push(userId, payload));
        assertDoesNotThrow(() -> hub.heartbeat());
        assertDoesNotThrow(() -> hub.push(userId, payload),
                "the dead emitter must have been removed on the first failure");
    }
}
