package blog.code.codeblog.messaging.event;

import blog.code.codeblog.enums.FlowImageFlag;
import lombok.Builder;

import java.util.UUID;


@Builder
public record ImageUploadCompletedEvent(
        FlowImageFlag flag,
        UUID userId,
        UUID postId,
        String imageUrl,
        String publicId)
        { }