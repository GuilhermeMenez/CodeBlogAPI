package blog.code.codeblog.service.search;

import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.search.bootstrap-reindex", havingValue = "true")
public class SearchIndexInitializer {

    @Autowired
    ElasticsearchOperations operations;
    @Autowired
    SearchIndexService searchIndexService;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            boolean created = createIfMissing(PostDocument.class) | createIfMissing(UserDocument.class);
            if (created) searchIndexService.reindexAll();
        } catch (Exception e) {
            log.warn("[init] Elasticsearch unavailable, skipping index bootstrap. Error: {}", e.getMessage());
        }
    }

    private boolean createIfMissing(Class<?> type) {
        IndexOperations ops = operations.indexOps(type);
        if (ops.exists()) return false;
        ops.createWithMapping();
        log.info("[createIfMissing] Index created: {}", ops.getIndexCoordinates().getIndexName());
        return true;
    }
}
