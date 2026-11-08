package blog.code.codeblog.messaging.publisher;

import blog.code.codeblog.enums.SearchEntityType;
import blog.code.codeblog.enums.SearchIndexAction;
import blog.code.codeblog.messaging.event.SearchIndexEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SearchIndexPublisherTest {

    private static final String EXCHANGE = "user.events.exchange";
    private static final String ROUTING_KEY = "search.index";

    private RabbitTemplate rabbitTemplate;
    private SearchIndexPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new SearchIndexPublisher(rabbitTemplate, EXCHANGE, ROUTING_KEY);
    }

    private SearchIndexEvent event() {
        return SearchIndexEvent.builder()
                .entityType(SearchEntityType.POST)
                .entityId(UUID.randomUUID())
                .action(SearchIndexAction.UPSERT)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should send search index event to configured exchange and routing key")
    void publishSearchIndexShouldSendToExchangeWithRoutingKey() {
        SearchIndexEvent event = event();

        publisher.publishSearchIndex(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, ROUTING_KEY, event);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should swallow broker failures so the original request is not affected")
    void publishSearchIndexShouldSwallowBrokerFailure() {
        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishSearchIndex(event()));
    }
}
