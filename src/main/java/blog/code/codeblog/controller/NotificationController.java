package blog.code.codeblog.controller;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.facade.NotificationFacade;
import blog.code.codeblog.service.provider.UserProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    @Autowired
    NotificationFacade notificationFacade;
    @Autowired
    UserProvider userProvider;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        log.info("Notification stream request received");
        return notificationFacade.subscribe(userProvider.getCurrentUser());
    }

    @GetMapping("/unread")
    @ResponseStatus(HttpStatus.OK)
    public List<NotificationResponseDTO> getUnread() {
        log.info("Get unread notifications request received");
        return notificationFacade.getUnread(userProvider.getCurrentUser());
    }

    @PatchMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead() {
        log.info("Mark all notifications as read request received");
        notificationFacade.markAllAsRead(userProvider.getCurrentUser());
    }
}
