package blog.code.codeblog.messaging.event;

import blog.code.codeblog.config.RabbitMQConfig;
import blog.code.codeblog.enums.FlowImageFlag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploadEventSerializationTest {

    private final MessageConverter converter = new RabbitMQConfig().jsonMessageConverter();

    @Test
    @DisplayName("Should keep all fields after JSON round trip through the message converter")
    void imageUploadEventShouldSurviveRoundTrip() {
        ImageUploadEvent original = ImageUploadEvent.builder()
                .flag(FlowImageFlag.POST)
                .userId(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .content(new byte[]{0, 1, 2, -1, 127, -128})
                .originalFilename("image.png")
                .occurredAt(LocalDateTime.of(2026, 10, 11, 14, 30, 15))
                .build();

        Message message = converter.toMessage(original, new MessageProperties());
        Object converted = converter.fromMessage(message);

        ImageUploadEvent result = assertInstanceOf(ImageUploadEvent.class, converted);
        assertEquals(original.getFlag(), result.getFlag());
        assertEquals(original.getUserId(), result.getUserId());
        assertEquals(original.getPostId(), result.getPostId());
        assertArrayEquals(original.getContent(), result.getContent());
        assertEquals(original.getOriginalFilename(), result.getOriginalFilename());
        assertEquals(original.getOccurredAt(), result.getOccurredAt());
    }

    @Test
    @DisplayName("Should serialize image content as base64 in the JSON body")
    void imageUploadEventShouldSerializeContentAsBase64() {
        ImageUploadEvent event = ImageUploadEvent.builder()
                .flag(FlowImageFlag.PROFILE)
                .userId(UUID.randomUUID())
                .content(new byte[]{1, 2, 3})
                .build();

        Message message = converter.toMessage(event, new MessageProperties());
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        assertEquals(MessageProperties.CONTENT_TYPE_JSON, message.getMessageProperties().getContentType());
        assertTrue(body.contains("\"content\":\"AQID\""), body);
        assertTrue(body.contains("\"postId\":null"), body);
    }
}
