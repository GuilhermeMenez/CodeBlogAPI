package blog.code.codeblog.service.search;

import blog.code.codeblog.command.search.SearchPostsCommand;
import blog.code.codeblog.command.search.SearchUsersCommand;
import blog.code.codeblog.command.search.SuggestCommand;
import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ElasticsearchOperations operations;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SearchService searchService;

    @Test
    @DisplayName("searchPosts keeps Elasticsearch ordering, drops orphan IDs and exposes totalHits")
    void searchPostsKeepsOrderAndDropsOrphans() {
        User author = user("Guilherme");
        Post first = post(author, "JavaScript avançado");
        Post second = post(author, "Java para iniciantes");
        UUID orphanId = UUID.randomUUID();   // ainda no índice, mas já removido do banco

        SearchHits<PostDocument> hits = hits(42,
                postDocument(second.getId()), postDocument(orphanId), postDocument(first.getId()));
        when(operations.search(any(Query.class), eq(PostDocument.class))).thenReturn(hits);
        // o banco devolve em outra ordem: o service precisa reordenar pela relevância do ES
        when(postRepository.findAllById(List.of(second.getId(), orphanId, first.getId())))
                .thenReturn(List.of(first, second));

        PageResponseDTO<PostResponseDTO> result = searchService.searchPosts(SearchPostsCommand.builder()
                .query("java").page(0).size(10).build());

        assertEquals(List.of(second.getId(), first.getId()),
                result.content().stream().map(PostResponseDTO::postId).toList());
        assertEquals(42, result.totalElements());
        assertEquals(0, result.currentPage());
        assertEquals(10, result.size());
    }

    @Test
    @DisplayName("searchUsers hydrates users from the database in Elasticsearch order")
    void searchUsersHydratesInOrder() {
        User gui = user("Guilherme");
        User guido = user("Guido");

        SearchHits<UserDocument> hits = hits(2, userDocument(guido.getId()), userDocument(gui.getId()));
        when(operations.search(any(Query.class), eq(UserDocument.class))).thenReturn(hits);
        when(userRepository.findAllById(List.of(guido.getId(), gui.getId()))).thenReturn(List.of(gui, guido));

        PageResponseDTO<UserFollowDTO> result = searchService.searchUsers(SearchUsersCommand.builder()
                .query("gui").page(0).size(10).build());

        assertEquals(List.of("Guido", "Guilherme"), result.content().stream().map(UserFollowDTO::name).toList());
        assertEquals(2, result.totalElements());
    }

    @Test
    @DisplayName("suggest builds posts and users from _source only, without touching the database")
    void suggestReadsOnlyFromSource() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostDocument postDocument = PostDocument.builder().id(postId).title("JavaScript avançado").build();
        UserDocument userDocument = UserDocument.builder().id(userId).name("Jane").build();

        SearchHits<PostDocument> postHits = hits(1, postDocument);
        SearchHits<UserDocument> userHits = hits(1, userDocument);
        when(operations.search(any(Query.class), eq(PostDocument.class))).thenReturn(postHits);
        when(operations.search(any(Query.class), eq(UserDocument.class))).thenReturn(userHits);

        SearchSuggestionsDTO result = searchService.suggest(SuggestCommand.builder().query("ja").limit(5).build());

        assertEquals(1, result.posts().size());
        assertEquals(postId, result.posts().getFirst().id());
        assertEquals("JavaScript avançado", result.posts().getFirst().title());
        assertEquals(1, result.users().size());
        assertEquals(userId, result.users().getFirst().id());
        assertEquals("Jane", result.users().getFirst().name());
        verifyNoInteractions(postRepository, userRepository);
    }

    @SafeVarargs
    @SuppressWarnings("unchecked")
    private static <T> SearchHits<T> hits(long totalHits, T... documents) {
        List<SearchHit<T>> searchHits = Arrays.stream(documents).map(document -> {
            SearchHit<T> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(document);
            return hit;
        }).toList();

        SearchHits<T> hits = mock(SearchHits.class);
        lenient().when(hits.stream()).thenAnswer(invocation -> searchHits.stream());
        lenient().when(hits.getTotalHits()).thenReturn(totalHits);
        return hits;
    }

    private static PostDocument postDocument(UUID id) {
        return PostDocument.builder().id(id).build();
    }

    private static UserDocument userDocument(UUID id) {
        return UserDocument.builder().id(id).build();
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setLogin(name.toLowerCase() + "@email.com");
        return user;
    }

    private static Post post(User author, String title) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setTitle(title);
        post.setContent("conteúdo");
        post.setDate(LocalDate.now());
        post.setUser(author);
        return post;
    }
}
