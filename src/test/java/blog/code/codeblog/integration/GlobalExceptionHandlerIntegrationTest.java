package blog.code.codeblog.integration;

import blog.code.codeblog.model.User;
import blog.code.codeblog.enums.UserRoles;
import blog.code.codeblog.repository.UserRepository;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GlobalExceptionHandlerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_SECRET = "MY-SECRET-KEY";



    private String generateTokenFor(User user) {
        Algorithm algorithm = Algorithm.HMAC256(TEST_SECRET);
        return JWT.create()
                .withIssuer("CodeBlog")
                .withSubject(user.getLogin())
                .withJWTId(UUID.randomUUID().toString())
                .withClaim("id", user.getId().toString())
                .withClaim("name", user.getName())
                .withExpiresAt(new Date(System.currentTimeMillis() + 60 * 60 * 1000)) // 1 hora
                .sign(algorithm);
    }

    private String generateValidToken() {
        User user = userRepository.findByLogin("mockuser@example.com");
        if (user == null) {
            user = new User("Mock User", "mockuser@example.com", "$2a$10$7EqJtq98hPqEX7fNZaFWoO5cLsVxun7q2Pa/ZG3WAs8Nq0P5FNGga", UserRoles.COSTUMER);
            userRepository.saveAndFlush(user);
        }
        return generateTokenFor(user);
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated POST to protected endpoint")
    void shouldReturn401ForUnauthenticatedPostToProtectedEndpoint() throws Exception {
        mockMvc.perform(post("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
    }

    @Test
    @DisplayName("Should return 400 when request body contains malformed JSON")
    void shouldReturn400WhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", equalTo("Invalid or malformed request body")))
                .andExpect(jsonPath("$.status", equalTo(400)));
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated access to any protected endpoint")
    void shouldReturn401ForUnauthenticatedAccessToProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")))
                .andExpect(jsonPath("$.path", equalTo("/users")));
    }

    @Test
    @DisplayName("Should return 401 for invalid credentials with sanitized message")
    void shouldReturn401ForInvalidCredentials() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"invalid@example.com\",\"credential\":\"invalid\",\"flow\":\"PASSWORD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status", equalTo(401)))
                .andExpect(jsonPath("$.error", equalTo("Invalid credentials")));
    }

    @Test
    @DisplayName("Should return 401 for protected endpoint with invalid token")
    void shouldReturn401ForProtectedEndpointWithInvalidToken() throws Exception {
        mockMvc.perform(get("/users")
                .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", equalTo("Unauthorized")));
    }



    @Test
    @DisplayName("Should return 404 for authenticated user accessing non-existent endpoint (protected by security)")
    void shouldReturn404ForAuthenticatedUserOnNonExistentEndpoint() throws Exception {
        String token = generateValidToken();
        mockMvc.perform(get("/nao-existe")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", containsString("Resource not found")));
    }

    @Test
    @DisplayName("Should return 200 for GET /post/posts with valid token")
    void shouldReturn200ForGetPostPostsWithValidToken() throws Exception {
        String uniqueLogin = "mockuser+" + UUID.randomUUID() + "@example.com";
        User user = new User("Mock User", uniqueLogin, "$2a$10$7QJ8QwQwQwQwQwQwQwQwQeQwQwQwQwQwQwQwQwQwQwQwQwQwQwQw", UserRoles.COSTUMER);
        user = userRepository.saveAndFlush(user);

        String token = generateTokenFor(user);
        var result = mockMvc.perform(get("/post/posts")
                .header("Authorization", "Bearer " + token))
                .andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(200, status, "Expected 200 but got: " + status + "\nResponse: " + content);
    }

}
