package blog.code.codeblog.messaging.publisher;

import blog.code.codeblog.messaging.event.SearchIndexEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class SearchIndexPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public SearchIndexPublisher(RabbitTemplate rabbitTemplate,
                                @Value("${app.rabbitmq.exchange}") String exchange,
                                @Value("${app.rabbitmq.routing-key.search-index}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publishSearchIndex(SearchIndexEvent event) {
        log.info("[publishSearchIndex] Publishing event. type: {}, id: {}, action: {}",
                event.getEntityType(), event.getEntityId(), event.getAction());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
            log.info("[publishSearchIndex] Event published successfully. id: {}", event.getEntityId());
        } catch (Exception e) {
            log.error("[publishSearchIndex] Failed to publish event. id: {}. Error: {}",
                    event.getEntityId(), e.getMessage(), e);
        }
    }
}
