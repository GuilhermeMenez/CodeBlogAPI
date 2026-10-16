
package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.messaging.event.UserFollowedEvent;
import blog.code.codeblog.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserFollowConsumer {

    @Autowired
    private NotificationService notificationService;

    @RabbitListener(queues = "${app.rabbitmq.queue.follow}")
    public void handleUserFollowed(UserFollowedEvent event) {
        log.info("[handleUserFollowed] Received follow notification. '{}' followed '{}'",
                event.getFollowerName(), event.getFollowedName());

        notificationService.notifyNewFollower(event.getFollowedId(), event.getFollowerId(), event.getFollowerName());

        log.info("[handleUserFollowed] Notification processed for user: {}", event.getFollowedId());
    }
}
