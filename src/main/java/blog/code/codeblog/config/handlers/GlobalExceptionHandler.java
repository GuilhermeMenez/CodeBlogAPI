package blog.code.codeblog.config.handlers;

import blog.code.codeblog.error.ErrorResponse;
import blog.code.codeblog.execptions.TooManyRequests;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;



import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;


@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {

        log.error("Internal Server Error", ex);

        ErrorResponse response = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeout(AsyncRequestTimeoutException ex, HttpServletRequest request) {
        log.debug("Async request timed out: {} - {}", request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException ex, HttpServletRequest request) {
        log.debug("Client disconnected: {} - {}", request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(
            IOException ex,
            HttpServletRequest request) {

        log.error("I/O error: {}", ex.getMessage(), ex);

        ErrorResponse response = createErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Error processing file",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex, HttpServletRequest request) {

        log.error("Invalid login attempt: {}", ex.getMessage());

        ErrorResponse response = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {

        log.warn("Access denied for: {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = createErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpServletRequest request) {
        
        log.warn("Resource not found: {} - {}", request.getRequestURI(), ex.getMessage());

        ErrorResponse response = createErrorResponse(
                HttpStatus.NOT_FOUND,
                "Resource not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            AuthenticationCredentialsNotFoundException ex, HttpServletRequest request) {

        log.error("not authorized {}", ex.getMessage());

        ErrorResponse response = createErrorResponse(
                HttpStatus.UNAUTHORIZED,
                "Invalid credentials",
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation error: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Validation error in submitted data",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Invalid JSON received: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid or malformed request body",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing required parameter: {}", ex.getParameterName());
        ErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Missing required parameter: " + ex.getParameterName(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Constraint violation in parameters",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }



    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn("Entity not found: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.NOT_FOUND,
                "Entity not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                "Data integrity violation",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUsernameNotFound(UsernameNotFoundException ex, HttpServletRequest request) {
        log.warn("User Name not found: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.NOT_FOUND,
                "Username not found",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal Arguments: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.CONFLICT,
                "Illegal Arguments",
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


    @ExceptionHandler(TooManyRequests.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequests ex, HttpServletRequest request) {
        log.warn("Too many requests: {}", ex.getMessage());
        ErrorResponse response = createErrorResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }


    private ErrorResponse createErrorResponse(HttpStatus status, String error, String path) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(error)
                .path(path)
                .build();
    }
}