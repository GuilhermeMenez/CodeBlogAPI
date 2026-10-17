package blog.code.codeblog.mapper;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.model.Notification;
import lombok.experimental.UtilityClass;

@UtilityClass
public class NotificationMapper {

    public NotificationResponseDTO toNotificationResponseDTO(Notification notification) {
        if (notification == null) return null;

        return NotificationResponseDTO.builder()
                .id(notification.getId())
                .followerId(notification.getFollowId())
                .message(notification.getMessage())
                .createdAt(notification.getCreatedAt())
                .read(notification.isRead())
                .build();
    }

}
