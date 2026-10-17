package blog.code.codeblog.messaging.event;

import blog.code.codeblog.enums.FlowImageFlag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadEvent implements Serializable {

    private FlowImageFlag flag;

    private UUID userId;
    private UUID postId;

    private byte[] content;
    private String originalFilename;

    private LocalDateTime occurredAt;
}
