package blog.code.codeblog.service;

import blog.code.codeblog.dto.notification.NotificationResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


@Slf4j
@Service
public class SseNotificationHub {

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(e -> remove(userId, emitter));

        send(userId, emitter, SseEmitter.event().comment("connected"));

        log.info("[subscribe] SSE connection opened for userId: {}", userId);
        return emitter;
    }

    public void push(UUID userId, NotificationResponseDTO payload) {
        for (SseEmitter emitter : emitters.getOrDefault(userId, List.of())) {
            send(userId, emitter, SseEmitter.event().name("notification").data(payload));
        }
    }

    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) ->
                userEmitters.forEach(emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            log.warn("[send] Dead SSE connection removed for userId: {}", userId);
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
