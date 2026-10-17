package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class CloudinaryUploadConsumerTest {

    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private CloudinaryUploadConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private ImageUploadEvent event(FlowImageFlag flag, UUID userId, UUID postId, byte[] content) {
        return ImageUploadEvent.builder()
                .flag(flag)
                .userId(userId)
                .postId(postId)
                .content(content)
                .originalFilename("image.png")
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private ImageUploadResponseDTO uploadResponse(String imageUrl, String publicId) {
        return ImageUploadResponseDTO.builder()
                .message("Upload completed")
                .imageUrl(imageUrl)
                .publicId(publicId)
                .build();
    }

    @Test
    @DisplayName("Should upload profile image without postId and publish completed event")
    void consumeProfileImageShouldUploadAndPublishCompletedEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        byte[] content = {1, 2, 3};

        when(cloudinaryService.uploadFile(eq(content), eq(FlowImageFlag.PROFILE), eq(userId.toString()), isNull()))
                .thenReturn(uploadResponse("https://cloudinary.com/profile.jpg", "profile_pics/profile"));

        consumer.consumeImageUploadEvent(event(FlowImageFlag.PROFILE, userId, null, content));

        ArgumentCaptor<ImageUploadCompletedEvent> captor = ArgumentCaptor.forClass(ImageUploadCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ImageUploadCompletedEvent completed = captor.getValue();

        assertEquals(FlowImageFlag.PROFILE, completed.flag());
        assertEquals(userId, completed.userId());
        assertNull(completed.postId());
        assertEquals("https://cloudinary.com/profile.jpg", completed.imageUrl());
        assertEquals("profile_pics/profile", completed.publicId());
    }

    @Test
    @DisplayName("Should upload post image with postId and publish completed event")
    void consumePostImageShouldUploadAndPublishCompletedEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        byte[] content = {4, 5, 6};

        when(cloudinaryService.uploadFile(content, FlowImageFlag.POST, userId.toString(), postId.toString()))
                .thenReturn(uploadResponse("https://cloudinary.com/post.jpg", "post_pics/post"));

        consumer.consumeImageUploadEvent(event(FlowImageFlag.POST, userId, postId, content));

        ArgumentCaptor<ImageUploadCompletedEvent> captor = ArgumentCaptor.forClass(ImageUploadCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ImageUploadCompletedEvent completed = captor.getValue();

        assertEquals(FlowImageFlag.POST, completed.flag());
        assertEquals(userId, completed.userId());
        assertEquals(postId, completed.postId());
        assertEquals("https://cloudinary.com/post.jpg", completed.imageUrl());
        assertEquals("post_pics/post", completed.publicId());
    }

    @Test
    @DisplayName("Should reject message without requeue and not publish completed event when upload fails")
    void consumeShouldRejectWhenUploadFails() throws IOException {
        UUID userId = UUID.randomUUID();

        when(cloudinaryService.uploadFile(any(byte[].class), any(), any(), any()))
                .thenThrow(new IOException("Cloudinary unavailable"));

        AmqpRejectAndDontRequeueException exception = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.consumeImageUploadEvent(event(FlowImageFlag.PROFILE, userId, null, new byte[]{1})));

        assertEquals("Failed to upload image: image.png", exception.getMessage());
        assertInstanceOf(IOException.class, exception.getCause());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("Should fail without uploading when post image event has no postId")
    void consumePostImageWithoutPostIdShouldFail() {
        ImageUploadEvent event = event(FlowImageFlag.POST, UUID.randomUUID(), null, new byte[]{1});

        assertThrows(NullPointerException.class, () -> consumer.consumeImageUploadEvent(event));

        verifyNoInteractions(cloudinaryService);
        verifyNoInteractions(eventPublisher);
    }
}
