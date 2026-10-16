package blog.code.codeblog.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.dead-letter-exchange}")
    private String deadLetterExchange;


    @Value("${app.rabbitmq.routing-key.follow}")
    private String followRoutingKey;

    @Value("${app.rabbitmq.routing-key.image-upload}")
    private String imageUploadRoutingKey;


    @Value("${app.rabbitmq.queue.follow}")
    private String followQueue;

    @Value("${app.rabbitmq.queue.image-upload}")
    private String imageUploadQueue;


    @Value("${app.rabbitmq.dlq.follow}")
    private String followDlq;

    @Value("${app.rabbitmq.dlq.image-upload}")
    private String imageUploadDlq;

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(deadLetterExchange, true, false);
    }



    @Bean
    public Queue followNotificationQueue() {
        return queueWithDeadLetter(followQueue, followDlq);
    }

    @Bean
    public Queue followDeadLetterQueue() {
        return QueueBuilder.durable(followDlq).build();
    }

    @Bean
    public Binding followBinding() {
        return BindingBuilder
                .bind(followNotificationQueue())
                .to(userEventsExchange())
                .with(followRoutingKey);
    }

    @Bean
    public Binding followDlqBinding() {
        return BindingBuilder
                .bind(followDeadLetterQueue())
                .to(deadLetterExchange())
                .with(followDlq);
    }


    @Bean
    public Queue imageUploadQueue() {
        return queueWithDeadLetter(imageUploadQueue, imageUploadDlq);
    }

    @Bean
    public Queue imageUploadDeadLetterQueue() {
        return QueueBuilder.durable(imageUploadDlq).build();
    }

    @Bean
    public Binding imageUploadBinding() {
        return BindingBuilder
                .bind(imageUploadQueue())
                .to(userEventsExchange())
                .with(imageUploadRoutingKey);
    }

    @Bean
    public Binding imageUploadDlqBinding() {
        return BindingBuilder
                .bind(imageUploadDeadLetterQueue())
                .to(deadLetterExchange())
                .with(imageUploadDlq);
    }

    private Queue queueWithDeadLetter(String queueName, String dlqName) {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", deadLetterExchange)
                .withArgument("x-dead-letter-routing-key", dlqName)
                .build();
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
