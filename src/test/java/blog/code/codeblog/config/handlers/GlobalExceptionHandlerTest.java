package blog.code.codeblog.config.handlers;

import blog.code.codeblog.error.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    @Mock
    private HttpServletRequest request;
    @InjectMocks
    private GlobalExceptionHandler handler;

    @Test
    @DisplayName("Should handle generic exception and return 500")
    void shouldHandleGenericException() {
        when(request.getRequestURI()).thenReturn("/test");
        Exception ex = new Exception("error");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request);
        assertErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "/test");
    }

    @Test
    @DisplayName("Should handle BadCredentialsException and return 401")
    void shouldHandleBadCredentials() {
        when(request.getRequestURI()).thenReturn("/login");
        BadCredentialsException ex = new BadCredentialsException("bad creds");
        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(ex, request);
        assertErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid credentials", "/login");
    }

    @Test
    @DisplayName("Should handle AccessDeniedException and return 403")
    void shouldHandleAccessDenied() {
        when(request.getRequestURI()).thenReturn("/admin");
        AccessDeniedException ex = new AccessDeniedException("denied");
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex, request);
        assertErrorResponse(response, HttpStatus.FORBIDDEN, "Access denied", "/admin");
    }

    @Test
    @DisplayName("Should handle NoResourceFoundException and return 404")
    void shouldHandleNoResourceFound() {
        when(request.getRequestURI()).thenReturn("/notfound");
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/notfound", "notfound");
        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFoundException(ex, request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body, "ErrorResponse body should not be null");
        assertEquals("Resource not found", body.getError());
        assertEquals("/notfound", body.getPath());
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException and return 400")
    void shouldHandleValidationException() {
        when(request.getRequestURI()).thenReturn("/validate");
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Validation error in submitted data", response.getBody().getError());
        assertEquals("/validate", response.getBody().getPath());
    }

    @Test
    @DisplayName("Should handle HttpMessageNotReadableException and return 400")
    void shouldHandleHttpMessageNotReadable() {
        when(request.getRequestURI()).thenReturn("/json");
        HttpInputMessage inputMessage = mock(HttpInputMessage.class);
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", inputMessage);
        ResponseEntity<ErrorResponse> response =
                handler.handleHttpMessageNotReadable(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid or malformed request body", response.getBody().getError());
        assertEquals("/json", response.getBody().getPath());
    }


    @Test
    @DisplayName("Should handle MissingServletRequestParameterException and return 400")
    void shouldHandleMissingServletRequestParameter() {
        when(request.getRequestURI()).thenReturn("/param");
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("id", "String");
        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameter(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getError().contains("Missing required parameter"));
        assertEquals("/param", response.getBody().getPath());
    }

    @Test
    @DisplayName("Should handle ConstraintViolationException and return 400")
    void shouldHandleConstraintViolation() {
        when(request.getRequestURI()).thenReturn("/constraint");
        ConstraintViolationException ex = new ConstraintViolationException("violation", null);
        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Constraint violation in parameters", response.getBody().getError());
        assertEquals("/constraint", response.getBody().getPath());
    }



    @Test
    @DisplayName("Should handle EntityNotFoundException and return 404")
    void shouldHandleEntityNotFound() {
        when(request.getRequestURI()).thenReturn("/entity");
        EntityNotFoundException ex = new EntityNotFoundException("not found");
        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(ex, request);
        assertErrorResponse(response, HttpStatus.NOT_FOUND, "Entity not found", "/entity");
    }

    @Test
    @DisplayName("Should handle DataIntegrityViolationException and return 409")
    void shouldHandleDataIntegrityViolation() {
        when(request.getRequestURI()).thenReturn("/conflict");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("conflict");
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request);
        assertErrorResponse(response, HttpStatus.CONFLICT, "Data integrity violation", "/conflict");
    }

    @Test
    @DisplayName("Should handle UsernameNotFoundException and return 404")
    void shouldHandleUsernameNotFound() {
        when(request.getRequestURI()).thenReturn("/user");
        UsernameNotFoundException ex = new UsernameNotFoundException("not found");
        ResponseEntity<ErrorResponse> response = handler.handleUsernameNotFound(ex, request);
        assertErrorResponse(response, HttpStatus.NOT_FOUND, "Username not found", "/user");
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException and return 400")
    void shouldHandleIllegalArgumentException() {
        when(request.getRequestURI()).thenReturn("/illegal");
        IllegalArgumentException ex = new IllegalArgumentException("illegal");
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex, request);
        assertErrorResponse(response, HttpStatus.BAD_REQUEST, "Illegal Arguments", "/illegal");
    }

    private void assertErrorResponse(ResponseEntity<ErrorResponse> response, HttpStatus status, String error, String path) {
        assertEquals(status, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body, "ErrorResponse body should not be null");
        assertEquals(error, body.getError());
        assertEquals(path, body.getPath());
    }
}
