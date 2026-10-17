package blog.code.codeblog.messaging.event;

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
public class UserFollowedEvent implements Serializable {

    private UUID followerId;
    private String followerName;

    private UUID followedId;
    private String followedName;

    private LocalDateTime occurredAt;
}