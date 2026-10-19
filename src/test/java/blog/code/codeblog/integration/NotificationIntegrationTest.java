package blog.code.codeblog.integration;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.enums.UserRoles;
import blog.code.codeblog.model.Notification;
import blog.code.codeblog.model.User;
import blog.code.codeblog.repository.NotificationRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.service.SseNotificationHub;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationIntegrationTest {

    private static final String TEST_SECRET = "MY-SECRET-KEY";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SseNotificationHub sseNotificationHub;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(new User("Notified User", "notified+" + UUID.randomUUID() + "@example.com",
                "$2a$10$7EqJtq98hPqEX7fNZaFWoO5cLsVxun7q2Pa/ZG3WAs8Nq0P5FNGga", UserRoles.COSTUMER));
        token = generateTokenFor(user);
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        userRepository.delete(user);
    }

    private String generateTokenFor(User user) {
        return JWT.create()
                .withIssuer("CodeBlog")
                .withSubject(user.getLogin())
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", user.getId().toString())
                .withClaim("name", user.getName())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(TEST_SECRET));
    }

    @Test
    @DisplayName("Should return 401 when opening the notification stream without a token")
    void streamWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/notifications/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should open the stream and deliver pushed notifications as SSE events")
    void streamDeliversPushedNotification() throws Exception {
        MvcResult result = mockMvc.perform(get("/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        sseNotificationHub.push(user.getId(), NotificationResponseDTO.builder()
                .id(UUID.randomUUID())
                .followerId(UUID.randomUUID())
                .message("Fulano começou a te seguir")
                .createdAt(LocalDateTime.now())
                .read(false)
                .build());

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains(":connected"), "Expected the initial comment, got: " + body);
        assertTrue(body.contains("event:notification"), "Expected a notification event, got: " + body);
        assertTrue(body.contains("Fulano começou a te seguir"), "Expected the message, got: " + body);
    }

    @Test
    @DisplayName("Should list unread notifications and mark them as read")
    void listUnreadAndMarkAsRead() throws Exception {
        notificationRepository.saveAndFlush(Notification.builder()
                .followedId(user.getId())
                .followId(UUID.randomUUID())
                .message("Fulano começou a te seguir")
                .build());

        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].message", equalTo("Fulano começou a te seguir")))
                .andExpect(jsonPath("$[0].read", equalTo(false)));

        mockMvc.perform(patch("/notifications/read")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
