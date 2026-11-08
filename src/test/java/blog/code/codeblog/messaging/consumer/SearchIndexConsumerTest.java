package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.enums.SearchEntityType;
import blog.code.codeblog.enums.SearchIndexAction;
import blog.code.codeblog.messaging.event.SearchIndexEvent;
import blog.code.codeblog.service.search.SearchIndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchIndexConsumerTest {

    @Mock
    private SearchIndexService searchIndexService;

    @InjectMocks
    private SearchIndexConsumer consumer;

    private static SearchIndexEvent event() {
        return SearchIndexEvent.builder()
                .entityType(SearchEntityType.USER)
                .entityId(UUID.randomUUID())
                .action(SearchIndexAction.UPSERT)
                .reindexAuthorPosts(true)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("handleSearchIndex delegates the event to SearchIndexService")
    void handleSearchIndexDelegatesToService() {
        SearchIndexEvent event = event();

        consumer.handleSearchIndex(event);

        verify(searchIndexService).handle(event);
    }

    @Test
    @DisplayName("handleSearchIndex propagates failures so the listener can retry and dead-letter the message")
    void handleSearchIndexPropagatesFailures() {
        SearchIndexEvent event = event();
        doThrow(new RuntimeException("Elasticsearch down")).when(searchIndexService).handle(event);

        assertThrows(RuntimeException.class, () -> consumer.handleSearchIndex(event));
    }
}
