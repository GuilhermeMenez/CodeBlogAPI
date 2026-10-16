package blog.code.codeblog.messaging.publisher;

import blog.code.codeblog.messaging.event.ImageUploadEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CloudinaryUploadPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public CloudinaryUploadPublisher(RabbitTemplate rabbitTemplate,
                                     @Value("${app.rabbitmq.exchange}") String exchange,
                                     @Value("${app.rabbitmq.routing-key.image-upload}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void  publishImageUpload(ImageUploadEvent message) {
        log.info("Publishing message to RabbitMQ exchange: {}", exchange);
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }

}

