package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.messaging.event.UserFollowedEvent;
import blog.code.codeblog.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserFollowConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private UserFollowConsumer consumer;

    @Test
    @DisplayName("handleUserFollowed notifies the followed user about the new follower")
    void handleUserFollowedNotifiesFollowedUser() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        UserFollowedEvent event = UserFollowedEvent.builder()
                .followerId(followerId)
                .followerName("Fulano")
                .followedId(followedId)
                .followedName("Ciclano")
                .occurredAt(LocalDateTime.now())
                .build();

        consumer.handleUserFollowed(event);

        verify(notificationService).notifyNewFollower(followedId, followerId, "Fulano");
    }
}
