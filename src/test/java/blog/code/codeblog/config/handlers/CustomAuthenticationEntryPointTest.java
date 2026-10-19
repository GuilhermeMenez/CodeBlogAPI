package blog.code.codeblog.config.handlers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomAuthenticationEntryPointTest {

    @Test
    @DisplayName("Should return 401 with correct JSON body on authentication failure")
    void shouldReturn401WithCorrectJsonBody() throws Exception {
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = mock(AuthenticationException.class);
        when(request.getRequestURI()).thenReturn("/api/protected");
        when(request.getServletPath()).thenReturn("/api/protected");
        when(request.getMethod()).thenReturn("GET");
        when(authException.getMessage()).thenReturn("Full authentication is required");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            @Override
            public void write(int b) { out.write(b); }
        });

        entryPoint.commence(request, response, authException);

        verify(response).setContentType("application/json");
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        String json = out.toString(StandardCharsets.UTF_8);
        Map<?,?> map = JsonMapper.shared().readValue(json, Map.class);
        assertEquals("Unauthorized", map.get("error"));
        assertEquals("Full authentication is required", map.get("message"));
        assertEquals("/api/protected", map.get("path"));
    }

    @Test
    @DisplayName("Should log unauthorized error with correct details")
    void shouldLogUnauthorizedError() throws Exception {
        CustomAuthenticationEntryPoint entryPoint = new CustomAuthenticationEntryPoint();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AuthenticationException authException = mock(AuthenticationException.class);
        when(request.getRequestURI()).thenReturn("/api/secure");
        when(request.getServletPath()).thenReturn("/api/secure");
        when(request.getMethod()).thenReturn("POST");
        when(authException.getMessage()).thenReturn("No auth");
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {}
            @Override
            public void write(int b) {}
        });

        assertDoesNotThrow(() -> entryPoint.commence(request, response, authException));
    }
}
