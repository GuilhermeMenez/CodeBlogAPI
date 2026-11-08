package blog.code.codeblog.repository.search;

import blog.code.codeblog.model.search.UserDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.UUID;

public interface UserDocumentRepository extends ElasticsearchRepository<UserDocument, UUID> {}