package blog.code.codeblog.messaging.consumer;

import blog.code.codeblog.messaging.event.SearchIndexEvent;
import blog.code.codeblog.service.search.SearchIndexService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SearchIndexConsumer {

    @Autowired
    private SearchIndexService searchIndexService;

    @RabbitListener(queues = "${app.rabbitmq.queue.search-index}")
    public void handleSearchIndex(SearchIndexEvent event) {
        log.info("[handleSearchIndex] Received. type: {}, id: {}, action: {}",
                event.getEntityType(), event.getEntityId(), event.getAction());

        searchIndexService.handle(event);

        log.info("[handleSearchIndex] Indexed. type: {}, id: {}", event.getEntityType(), event.getEntityId());
    }
}
