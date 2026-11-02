package blog.code.codeblog.repository.search;

import blog.code.codeblog.model.search.PostDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.UUID;

public interface PostDocumentRepository extends ElasticsearchRepository<PostDocument, UUID> {
    void deleteByAuthorId(UUID authorId);
}