package blog.code.codeblog.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should allow unauthenticated access to /auth/login")
    void shouldPermitLoginWithoutAuthentication() throws Exception {

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    assertTrue(
                            status != 403,
                            "Login endpoint should not be blocked by security"
                    );
                });
    }

    @Test
    @DisplayName("Should allow unauthenticated access to /auth/register")
    void shouldPermitRegisterWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    assertTrue(
                            status != 401 && status != 403,
                            "Register endpoint should not require authentication"
                    );
                });
    }


    @Test
    @DisplayName("Should require authentication for protected endpoints")
    void shouldRequireAuthenticationForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/protected-endpoint"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should allow unauthenticated access to Swagger endpoints")
    void shouldPermitSwaggerEndpoints() throws Exception {

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(
                            status != 401 && status != 403,
                            "Swagger UI should not require authentication"
                    );
                });

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(
                            status != 401 && status != 403,
                            "OpenAPI docs should not require authentication"
                    );
                });
    }



    @Test
    @DisplayName("Should include CORS headers on response")
    void shouldIncludeCorsHeaders() throws Exception {
        mockMvc.perform(get("/auth/login")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status != 403);

                    assertTrue(
                            result.getResponse().getHeaderNames().stream()
                                    .anyMatch(h -> h.toLowerCase().startsWith("access-control-allow")),
                            "CORS headers should be present in the response"
                    );
                });
    }



    @Test
    @DisplayName("Should not require CSRF token for POST requests")
    void shouldNotRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    // CSRF failure would be 403
                    assertTrue(
                            status != 403,
                            "POST requests should not fail due to CSRF"
                    );
                });
    }
}