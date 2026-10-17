package blog.code.codeblog.integration;

import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.enums.UserRoles;
import blog.code.codeblog.integration.support.RabbitMQContainerSupport;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.messaging.publisher.CloudinaryUploadPublisher;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.service.CloudinaryService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ImageUploadMessagingIntegrationTest extends RabbitMQContainerSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private CloudinaryUploadPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private CloudinaryService cloudinaryService;

    @Value("${app.rabbitmq.queue.image-upload}")
    private String imageUploadQueue;

    @Value("${app.rabbitmq.dlq.image-upload}")
    private String imageUploadDlq;

    private final List<UUID> createdPosts = new ArrayList<>();
    private final List<UUID> createdUsers = new ArrayList<>();

    @BeforeEach
    void purgeQueues() {
        amqpAdmin.purgeQueue(imageUploadQueue, false);
        amqpAdmin.purgeQueue(imageUploadDlq, false);
    }

    @AfterEach
    void cleanUp() {
        createdPosts.forEach(postRepository::deleteById);
        createdUsers.forEach(userRepository::deleteById);
        createdPosts.clear();
        createdUsers.clear();
    }

    private User saveUser() {
        User user = userRepository.save(User.builder()
                .name("Rabbit User")
                .login("rabbit-" + UUID.randomUUID() + "@test.com")
                .password("secret")
                .role(UserRoles.COSTUMER)
                .build());
        createdUsers.add(user.getId());
        return user;
    }

    private Post savePost(User author) {
        Post post = postRepository.save(Post.builder()
                .title("Post with image")
                .content("Content")
                .authorName(author.getName())
                .date(LocalDate.now())
                .user(author)
                .build());
        createdPosts.add(0, post.getId());
        return post;
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

    private <T> T awaitPresent(Supplier<Optional<T>> supplier) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> value = supplier.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(100);
        }
        return fail("Condition not met within " + TIMEOUT);
    }

    @Test
    @DisplayName("Profile image event should travel through the queue and update the user's profile pic")
    void profileImageEventShouldUpdateUser() throws Exception {
        User user = saveUser();
        byte[] content = {0, 1, 2, -1, 127, -128};
        String imageUrl = "https://cloudinary.com/profile.jpg";
        String publicId = "profile_pics/" + user.getId();

        when(cloudinaryService.uploadFile(any(byte[].class), eq(FlowImageFlag.PROFILE), eq(user.getId().toString()), isNull()))
                .thenReturn(uploadResponse(imageUrl, publicId));

        publisher.publishImageUpload(event(FlowImageFlag.PROFILE, user.getId(), null, content));

        User updated = awaitPresent(() -> userRepository.findById(user.getId())
                .filter(u -> u.getUrlProfilePic() != null));

        assertEquals(imageUrl, updated.getUrlProfilePic());
        assertEquals(publicId, updated.getProfilePicId());

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(cloudinaryService, timeout(TIMEOUT.toMillis()))
                .uploadFile(bytes.capture(), eq(FlowImageFlag.PROFILE), eq(user.getId().toString()), isNull());
        assertArrayEquals(content, bytes.getValue());
    }

    @Test
    @DisplayName("Post image event should travel through the queue and add the image to the post")
    void postImageEventShouldAddImageToPost() throws Exception {
        User author = saveUser();
        Post post = savePost(author);
        String imageUrl = "https://cloudinary.com/post.jpg";
        String publicId = "post_pics/" + post.getId();

        when(cloudinaryService.uploadFile(any(byte[].class), eq(FlowImageFlag.POST), eq(author.getId().toString()), eq(post.getId().toString())))
                .thenReturn(uploadResponse(imageUrl, publicId));

        publisher.publishImageUpload(event(FlowImageFlag.POST, author.getId(), post.getId(), new byte[]{1, 2, 3}));

        Post updated = awaitPresent(() -> postRepository.findByImagePublicId(publicId));

        assertEquals(post.getId(), updated.getId());
        verify(cloudinaryService, timeout(TIMEOUT.toMillis()).times(1))
                .uploadFile(any(byte[].class), eq(FlowImageFlag.POST), any(), any());
    }

    @Test
    @DisplayName("Failed upload should send the message to the dead letter queue without updating the user")
    void failedUploadShouldGoToDeadLetterQueue() throws Exception {
        User user = saveUser();

        when(cloudinaryService.uploadFile(any(byte[].class), any(), any(), any()))
                .thenThrow(new IOException("Cloudinary unavailable"));

        publisher.publishImageUpload(event(FlowImageFlag.PROFILE, user.getId(), null, new byte[]{1}));

        Message deadLetter = rabbitTemplate.receive(imageUploadDlq, TIMEOUT.toMillis());

        assertNotNull(deadLetter, "Message should be routed to " + imageUploadDlq);
        ImageUploadEvent rejected = assertInstanceOf(ImageUploadEvent.class,
                rabbitTemplate.getMessageConverter().fromMessage(deadLetter));
        assertEquals(user.getId(), rejected.getUserId());
        assertNull(userRepository.findById(user.getId()).orElseThrow().getUrlProfilePic());
    }
}
