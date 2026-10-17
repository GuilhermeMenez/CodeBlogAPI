package blog.code.codeblog.facade;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.model.User;
import blog.code.codeblog.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Component
public class NotificationFacade {
    @Autowired
    NotificationService notificationService;


    public SseEmitter subscribe(User user) {
        return notificationService.subscribe(user.getId());
    }

    public List<NotificationResponseDTO> getUnread(User user) {
        return notificationService.getUnread(user.getId());
    }

    public void markAllAsRead(User user) {
        notificationService.markAllAsRead(user.getId());
    }

}
