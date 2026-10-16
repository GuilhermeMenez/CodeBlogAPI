package blog.code.codeblog.service;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import blog.code.codeblog.mapper.NotificationMapper;
import blog.code.codeblog.model.Notification;
import blog.code.codeblog.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Salva a notificação no banco (fonte da verdade, usada por quem estava offline)
 * e a empurra em tempo real pelo {@link SseNotificationHub} para quem está online.
 */
@Slf4j
@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private SseNotificationHub sseNotificationHub;

    public void notifyNewFollower(UUID followedId, UUID followerId, String followerName) {
        if (notificationRepository.existsByFollowedIdAndFollowIdAndReadFalse(followedId, followerId)) {
            log.info("[notifyNewFollower] Unread notification already exists, skipping. followedId: {}, followerId: {}",
                    followedId, followerId);
            return;
        }

        Notification saved = notificationRepository.save(Notification.builder()
                .followedId(followedId)
                .followId(followerId)
                .message(followerName + " começou a te seguir")
                .build());
        log.info("[notifyNewFollower] Notification saved. id: {}, followedId: {}", saved.getId(), followedId);

        sseNotificationHub.push(followedId, NotificationMapper.toNotificationResponseDTO(saved));
    }

    public SseEmitter subscribe(UUID userId) {
        return sseNotificationHub.subscribe(userId);
    }

    public List<NotificationResponseDTO> getUnread(UUID userId) {
        return notificationRepository.findByFollowedIdAndReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(NotificationMapper::toNotificationResponseDTO)
                .toList();
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        int updated = notificationRepository.markAllAsReadByFollowedId(userId);
        log.info("[markAllAsRead] {} notifications marked as read for userId: {}", updated, userId);
    }
}
