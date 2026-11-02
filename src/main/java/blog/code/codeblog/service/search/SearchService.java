package blog.code.codeblog.service.search;

import blog.code.codeblog.command.search.SearchPostsCommand;
import blog.code.codeblog.command.search.SearchUsersCommand;
import blog.code.codeblog.command.search.SuggestCommand;
import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.PostSuggestionDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.search.UserSuggestionDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.mapper.PageMapper;
import blog.code.codeblog.mapper.PostMapper;
import blog.code.codeblog.mapper.UserMapper;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchService {

    @Autowired
    ElasticsearchOperations operations;
    @Autowired
    PostRepository postRepository;
    @Autowired
    UserRepository userRepository;

    public PageResponseDTO<PostResponseDTO> searchPosts(SearchPostsCommand command) {
        log.info("[searchPosts] Searching posts. query: '{}' (page: {}, size: {})", command.getQuery(), command.getPage(), command.getSize());
        Pageable pageable = PageRequest.of(command.getPage(), command.getSize());
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b
                        // 1) termos completos, tolerando erro de digitação: "javscript" → "JavaScript"
                        .should(s -> s.multiMatch(m -> m
                                .query(command.getQuery())
                                .fields("title^3", "title.en^3", "authorName^2", "content", "content.en")
                                .type(TextQueryType.BestFields)
                                .tieBreaker(0.3)
                                .fuzziness("AUTO")
                                .prefixLength(1)))
                        // 2) palavra incompleta enquanto digita: "jav" → "JavaScript"
                        .should(s -> s.multiMatch(m -> m
                                .query(command.getQuery())
                                .fields("title.suggest", "title.suggest._2gram", "title.suggest._3gram")
                                .type(TextQueryType.BoolPrefix)
                                .boost(2.0f)))
                        .minimumShouldMatch("1")))
                .withPageable(pageable)
                .withSourceFilter(FetchSourceFilter.of(b -> b.withIncludes("id")))   // só precisamos dos IDs
                .build();

        SearchHits<PostDocument> hits = operations.search(query, PostDocument.class);
        List<UUID> ids = hits.stream().map(h -> h.getContent().getId()).toList();

        Map<UUID, Post> byId = postRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<PostResponseDTO> content = ids.stream()
                .map(byId::get).filter(Objects::nonNull)               // descarta órfãos (deletados e ainda não sincronizados)
                .map(PostMapper::toPostResponseDTO)
                .toList();

        return PageMapper.toPageResponseDTO(content, command.getPage(), command.getSize(), hits.getTotalHits());
    }

    public PageResponseDTO<UserFollowDTO> searchUsers(SearchUsersCommand command) {
        log.info("[searchUsers] Searching users. query: '{}' (page: {}, size: {})", command.getQuery(), command.getPage(), command.getSize());
        Pageable pageable = PageRequest.of(command.getPage(), command.getSize());
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(command.getQuery())
                        .fields("name", "name._2gram", "name._3gram")
                        .type(TextQueryType.BoolPrefix)
                        .fuzziness("AUTO")))
                .withPageable(pageable)
                .withSourceFilter(FetchSourceFilter.of(b -> b.withIncludes("id")))
                .build();

        SearchHits<UserDocument> hits = operations.search(query, UserDocument.class);
        List<UUID> ids = hits.stream().map(h -> h.getContent().getId()).toList();

        Map<UUID, User> byId = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<UserFollowDTO> content = ids.stream()
                .map(byId::get).filter(Objects::nonNull)
                .map(UserMapper::toUserFollowDTO)
                .toList();

        return PageMapper.toPageResponseDTO(content, command.getPage(), command.getSize(), hits.getTotalHits());
    }

    public SearchSuggestionsDTO suggest(SuggestCommand command) {
        log.info("[suggest] Suggesting. query: '{}' (limit: {})", command.getQuery(), command.getLimit());
        List<PostSuggestionDTO> posts = operations.search(
                        suggestQuery(command, "id", "title", "title.suggest", "title.suggest._2gram", "title.suggest._3gram"),
                        PostDocument.class).stream()
                .map(h -> new PostSuggestionDTO(h.getContent().getId(), h.getContent().getTitle()))
                .toList();

        List<UserSuggestionDTO> users = operations.search(
                        suggestQuery(command, "id", "name", "name", "name._2gram", "name._3gram"),
                        UserDocument.class).stream()
                .map(h -> new UserSuggestionDTO(h.getContent().getId(), h.getContent().getName()))
                .toList();

        return new SearchSuggestionsDTO(posts, users);   // sem hidratação pelo Postgres
    }

    private NativeQuery suggestQuery(SuggestCommand command, String idField, String sourceField, String... fields) {
        return NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(command.getQuery())
                        .fields(List.of(fields))
                        .type(TextQueryType.BoolPrefix)))
                .withSourceFilter(FetchSourceFilter.of(b -> b.withIncludes(idField, sourceField)))
                .withMaxResults(command.getLimit())
                .withTrackTotalHits(false)                 // não precisa contar o total
                .withTimeout(Duration.ofMillis(500))       // sugestão atrasada não serve para nada
                .build();
    }
}
