
package blog.code.codeblog.messaging.publisher;

import blog.code.codeblog.messaging.event.UserFollowedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserFollowPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public UserFollowPublisher(RabbitTemplate rabbitTemplate,
                               @Value("${app.rabbitmq.exchange}") String exchange,
                               @Value("${app.rabbitmq.routing-key.follow}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publishUserFollowed(UserFollowedEvent event) {
        log.info("[publishUserFollowed] Publishing event. followerId: {}, followedId: {}",
                event.getFollowerId(), event.getFollowedId());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("[publishUserFollowed] Event published successfully. followedId: {}", event.getFollowedId());
        } catch (Exception e) {
            log.error("[publishUserFollowed] Failed to publish event. followedId: {}. Error: {}",
                    event.getFollowedId(), e.getMessage(), e);
        }
    }
}