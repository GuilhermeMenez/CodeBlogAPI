package blog.code.codeblog.service.search;

import blog.code.codeblog.mapper.SearchDocumentMapper;
import blog.code.codeblog.messaging.event.SearchIndexEvent;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.repository.search.PostDocumentRepository;
import blog.code.codeblog.repository.search.UserDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
public class SearchIndexService {

    @Autowired
    PostRepository postRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PostDocumentRepository postDocumentRepository;
    @Autowired
    UserDocumentRepository userDocumentRepository;

    @Value("${app.search.reindex-batch-size:500}")
    int batchSize;

    @Transactional(readOnly = true)
    public void handle(SearchIndexEvent event) {
        switch (event.getEntityType()) {
            case POST -> indexPost(event.getEntityId());
            case USER -> indexUser(event.getEntityId(), event.isReindexAuthorPosts());
        }
    }

    @Transactional(readOnly = true)
    public void reindexAll() {
        log.info("[reindexAll] Starting full reindex");
        reindex(postRepository::findAll, SearchDocumentMapper::toPostDocument, postDocumentRepository);
        reindex(userRepository::findAll, SearchDocumentMapper::toUserDocument, userDocumentRepository);
        log.info("[reindexAll] Full reindex finished");
    }

    void indexPost(UUID postId) {
        postRepository.findById(postId).ifPresentOrElse(
                post -> postDocumentRepository.save(SearchDocumentMapper.toPostDocument(post)),
                () -> postDocumentRepository.deleteById(postId));
    }

    void indexUser(UUID userId, boolean reindexAuthorPosts) {
        userRepository.findById(userId).ifPresentOrElse(user -> {
            userDocumentRepository.save(SearchDocumentMapper.toUserDocument(user));
            if (reindexAuthorPosts) reindexPostsOf(userId);
        }, () -> {
            userDocumentRepository.deleteById(userId);
            postDocumentRepository.deleteByAuthorId(userId);
        });
    }

    private void reindexPostsOf(UUID authorId) {
        reindex(pageable -> postRepository.findByAuthorId(authorId, pageable),
                SearchDocumentMapper::toPostDocument, postDocumentRepository);
    }

    private <E, D> void reindex(Function<Pageable, Page<E>> source, Function<E, D> mapper,
                                ElasticsearchRepository<D, UUID> target) {
        Pageable pageable = PageRequest.of(0, batchSize, Sort.by("id"));
        Page<E> page;
        do {
            page = source.apply(pageable);
            target.saveAll(page.map(mapper).getContent());
            pageable = page.nextPageable();
        } while (page.hasNext());
    }
}
