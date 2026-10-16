package blog.code.codeblog.dto.notification;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record NotificationResponseDTO(
        UUID id,
        UUID followerId,
        String message,
        LocalDateTime createdAt,
        boolean read)
{ }
