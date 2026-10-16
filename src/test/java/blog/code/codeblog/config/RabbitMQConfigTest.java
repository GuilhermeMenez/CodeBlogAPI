package blog.code.codeblog.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RabbitMQConfigTest {

    private static final String EXCHANGE = "user.events.exchange";
    private static final String DEAD_LETTER_EXCHANGE = "user.events.dlx";
    private static final String FOLLOW_QUEUE = "user.follow.notification.queue";
    private static final String IMAGE_UPLOAD_QUEUE = "image.upload.queue";
    private static final String FOLLOW_DLQ = "user.follow.notification.dlq";
    private static final String IMAGE_UPLOAD_DLQ = "image.upload.dlq";
    private static final String FOLLOW_ROUTING_KEY = "user.followed";
    private static final String IMAGE_UPLOAD_ROUTING_KEY = "image.upload";

    @Mock
    private ConnectionFactory connectionFactory;

    private RabbitMQConfig config;

    @BeforeEach
    void setUp() {
        config = new RabbitMQConfig();
        setField(config, "exchange", EXCHANGE);
        setField(config, "deadLetterExchange", DEAD_LETTER_EXCHANGE);
        setField(config, "followRoutingKey", FOLLOW_ROUTING_KEY);
        setField(config, "imageUploadRoutingKey", IMAGE_UPLOAD_ROUTING_KEY);
        setField(config, "followQueue", FOLLOW_QUEUE);
        setField(config, "imageUploadQueue", IMAGE_UPLOAD_QUEUE);
        setField(config, "followDlq", FOLLOW_DLQ);
        setField(config, "imageUploadDlq", IMAGE_UPLOAD_DLQ);
    }


    @Test
    @DisplayName("userEventsExchange is a durable, non auto-delete topic exchange")
    void userEventsExchangeIsDurableTopicExchange() {
        TopicExchange exchange = config.userEventsExchange();

        assertEquals(EXCHANGE, exchange.getName());
        assertEquals(ExchangeTypes.TOPIC, exchange.getType());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    @DisplayName("deadLetterExchange is a durable, non auto-delete topic exchange")
    void deadLetterExchangeIsDurableTopicExchange() {
        TopicExchange exchange = config.deadLetterExchange();

        assertEquals(DEAD_LETTER_EXCHANGE, exchange.getName());
        assertEquals(ExchangeTypes.TOPIC, exchange.getType());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }


    @Test
    @DisplayName("followNotificationQueue is durable and dead-letters to the follow DLQ")
    void followNotificationQueueDeadLettersToFollowDlq() {
        Queue queue = config.followNotificationQueue();

        assertDurableQueue(queue, FOLLOW_QUEUE);
        assertEquals(Map.of(
                        "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", FOLLOW_DLQ),
                queue.getArguments());
    }

    @Test
    @DisplayName("followDeadLetterQueue is durable and has no dead-letter arguments")
    void followDeadLetterQueueIsPlainDurableQueue() {
        Queue queue = config.followDeadLetterQueue();

        assertDurableQueue(queue, FOLLOW_DLQ);
        assertTrue(queue.getArguments().isEmpty());
    }

    @Test
    @DisplayName("imageUploadQueue is durable and dead-letters to the image upload DLQ")
    void imageUploadQueueDeadLettersToImageUploadDlq() {
        Queue queue = config.imageUploadQueue();

        assertDurableQueue(queue, IMAGE_UPLOAD_QUEUE);
        assertEquals(Map.of(
                        "x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", IMAGE_UPLOAD_DLQ),
                queue.getArguments());
    }

    @Test
    @DisplayName("imageUploadDeadLetterQueue is durable and has no dead-letter arguments")
    void imageUploadDeadLetterQueueIsPlainDurableQueue() {
        Queue queue = config.imageUploadDeadLetterQueue();

        assertDurableQueue(queue, IMAGE_UPLOAD_DLQ);
        assertTrue(queue.getArguments().isEmpty());
    }


    @Test
    @DisplayName("followBinding routes the follow routing key from the events exchange to the follow queue")
    void followBindingRoutesToFollowQueue() {
        assertQueueBinding(config.followBinding(), FOLLOW_QUEUE, EXCHANGE, FOLLOW_ROUTING_KEY);
    }

    @Test
    @DisplayName("followDlqBinding routes the follow DLQ key from the DLX to the follow DLQ")
    void followDlqBindingRoutesToFollowDlq() {
        assertQueueBinding(config.followDlqBinding(), FOLLOW_DLQ, DEAD_LETTER_EXCHANGE, FOLLOW_DLQ);
    }

    @Test
    @DisplayName("imageUploadBinding routes the image upload routing key from the events exchange to the image upload queue")
    void imageUploadBindingRoutesToImageUploadQueue() {
        assertQueueBinding(config.imageUploadBinding(), IMAGE_UPLOAD_QUEUE, EXCHANGE, IMAGE_UPLOAD_ROUTING_KEY);
    }

    @Test
    @DisplayName("imageUploadDlqBinding routes the image upload DLQ key from the DLX to the image upload DLQ")
    void imageUploadDlqBindingRoutesToImageUploadDlq() {
        assertQueueBinding(config.imageUploadDlqBinding(), IMAGE_UPLOAD_DLQ, DEAD_LETTER_EXCHANGE, IMAGE_UPLOAD_DLQ);
    }

    @Test
    @DisplayName("dead-letter routing key of each main queue matches its DLQ binding")
    void deadLetterRoutingKeysMatchDlqBindings() {
        assertEquals(config.followDlqBinding().getRoutingKey(),
                config.followNotificationQueue().getArguments().get("x-dead-letter-routing-key"));
        assertEquals(config.imageUploadDlqBinding().getRoutingKey(),
                config.imageUploadQueue().getArguments().get("x-dead-letter-routing-key"));
    }


    @Test
    @DisplayName("jsonMessageConverter returns a Jackson2JsonMessageConverter")
    void jsonMessageConverterIsJackson() {
        assertInstanceOf(Jackson2JsonMessageConverter.class, config.jsonMessageConverter());
    }

    @Test
    @DisplayName("rabbitTemplate uses the given connection factory and message converter")
    void rabbitTemplateUsesConnectionFactoryAndConverter() {
        MessageConverter converter = config.jsonMessageConverter();

        RabbitTemplate template = config.rabbitTemplate(connectionFactory, converter);

        assertNotNull(template);
        assertSame(connectionFactory, template.getConnectionFactory());
        assertSame(converter, template.getMessageConverter());
    }



    private static void assertDurableQueue(Queue queue, String expectedName) {
        assertEquals(expectedName, queue.getName());
        assertTrue(queue.isDurable());
        assertFalse(queue.isExclusive());
        assertFalse(queue.isAutoDelete());
    }

    private static void assertQueueBinding(Binding binding, String queue, String exchange, String routingKey) {
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
        assertEquals(queue, binding.getDestination());
        assertEquals(exchange, binding.getExchange());
        assertEquals(routingKey, binding.getRoutingKey());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
