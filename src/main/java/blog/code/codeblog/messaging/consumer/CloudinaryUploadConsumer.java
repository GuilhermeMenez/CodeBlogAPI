package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.service.CloudinaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CloudinaryUploadConsumer {

    @Autowired
    private CloudinaryService cloudinaryService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @RabbitListener(queues = "${app.rabbitmq.queue.image-upload}")
    public void consumeImageUploadEvent(ImageUploadEvent event) {
        log.info("Received image upload event: {}", event);

        try {
            ImageUploadResponseDTO response = switch (event.getFlag()) {
                case PROFILE ->
                        cloudinaryService.uploadFile(event.getContent(), event.getFlag(), event.getUserId().toString(), null);
                case POST ->
                        cloudinaryService.uploadFile(event.getContent(), event.getFlag(), event.getUserId().toString(), event.getPostId().toString());
            };
            log.info("Image uploaded. PublicId: {}", response.publicId());
            eventPublisher.publishEvent(new ImageUploadCompletedEvent(event.getFlag(), event.getUserId(), event.getPostId(), response.imageUrl(), response.publicId()));
        } catch (IOException e) {
            throw new AmqpRejectAndDontRequeueException("Failed to upload image: " + event.getOriginalFilename(), e);
        }
    }



}
