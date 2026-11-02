package blog.code.codeblog.messaging.event;

import blog.code.codeblog.enums.SearchEntityType;
import blog.code.codeblog.enums.SearchIndexAction;
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
public class SearchIndexEvent implements Serializable {

    private SearchEntityType entityType;
    private UUID entityId;
    private SearchIndexAction action;
    private boolean reindexAuthorPosts;

    private LocalDateTime occurredAt;
}
