package blog.code.codeblog.messaging.publisher;

import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
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

class CloudinaryUploadPublisherTest {

    private static final String EXCHANGE = "user.events.exchange";
    private static final String ROUTING_KEY = "image.upload";

    private RabbitTemplate rabbitTemplate;
    private CloudinaryUploadPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new CloudinaryUploadPublisher(rabbitTemplate, EXCHANGE, ROUTING_KEY);
    }

    private ImageUploadEvent event() {
        return ImageUploadEvent.builder()
                .flag(FlowImageFlag.PROFILE)
                .userId(UUID.randomUUID())
                .content(new byte[]{1, 2, 3})
                .originalFilename("avatar.png")
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should send image upload event to configured exchange and routing key")
    void publishImageUploadShouldSendToExchangeWithRoutingKey() {
        ImageUploadEvent event = event();

        publisher.publishImageUpload(event);

        verify(rabbitTemplate).convertAndSend(EXCHANGE, ROUTING_KEY, event);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("Should propagate exception when broker is unavailable")
    void publishImageUploadShouldPropagateBrokerFailure() {
        doThrow(new AmqpException("Broker unavailable"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        assertThrows(AmqpException.class, () -> publisher.publishImageUpload(event()));
    }
}
