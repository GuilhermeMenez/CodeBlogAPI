package blog.code.codeblog.service.search;

import blog.code.codeblog.enums.SearchEntityType;
import blog.code.codeblog.enums.SearchIndexAction;
import blog.code.codeblog.messaging.event.SearchIndexEvent;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.repository.search.PostDocumentRepository;
import blog.code.codeblog.repository.search.UserDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchIndexServiceTest {

    private static final int BATCH_SIZE = 2;

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostDocumentRepository postDocumentRepository;
    @Mock
    private UserDocumentRepository userDocumentRepository;

    @InjectMocks
    private SearchIndexService searchIndexService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(searchIndexService, "batchSize", BATCH_SIZE);
    }

    @Test
    @DisplayName("handle POST saves the document when the post exists in the database")
    void handlePostExistsSavesDocument() {
        User author = user("Guilherme");
        Post post = post(author, "JavaScript avançado");
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));

        searchIndexService.handle(event(SearchEntityType.POST, post.getId(), SearchIndexAction.UPSERT, false));

        ArgumentCaptor<PostDocument> captor = ArgumentCaptor.forClass(PostDocument.class);
        verify(postDocumentRepository).save(captor.capture());
        PostDocument document = captor.getValue();
        assertEquals(post.getId(), document.getId());
        assertEquals("JavaScript avançado", document.getTitle());
        assertEquals(author.getId(), document.getAuthorId());
        assertEquals("Guilherme", document.getAuthorName());
        verify(postDocumentRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("handle POST removes the document when the post no longer exists, whatever the action")
    void handlePostMissingDeletesDocument() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        searchIndexService.handle(event(SearchEntityType.POST, postId, SearchIndexAction.UPSERT, false));

        verify(postDocumentRepository).deleteById(postId);
        verify(postDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handle USER saves only the user document when reindexAuthorPosts is false")
    void handleUserExistsSavesUserOnly() {
        User user = user("Guilherme");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        searchIndexService.handle(event(SearchEntityType.USER, user.getId(), SearchIndexAction.UPSERT, false));

        ArgumentCaptor<UserDocument> captor = ArgumentCaptor.forClass(UserDocument.class);
        verify(userDocumentRepository).save(captor.capture());
        assertEquals(user.getId(), captor.getValue().getId());
        assertEquals("Guilherme", captor.getValue().getName());
        verifyNoInteractions(postRepository, postDocumentRepository);
    }

    @Test
    @DisplayName("handle USER removes the user and all of their posts when the user no longer exists")
    void handleUserMissingDeletesUserAndPosts() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        searchIndexService.handle(event(SearchEntityType.USER, userId, SearchIndexAction.DELETE, false));

        verify(userDocumentRepository).deleteById(userId);
        verify(postDocumentRepository).deleteByAuthorId(userId);
        verify(userDocumentRepository, never()).save(any());
    }

    @Test
    @DisplayName("handle USER with reindexAuthorPosts goes through every page of the author's posts")
    void handleUserReindexesAllAuthorPostPages() {
        User author = user("Novo Nome");
        List<Post> firstPage = List.of(post(author, "Post 1"), post(author, "Post 2"));
        List<Post> secondPage = List.of(post(author, "Post 3"));
        when(userRepository.findById(author.getId())).thenReturn(Optional.of(author));
        when(postRepository.findByAuthorId(eq(author.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(firstPage, PageRequest.of(0, BATCH_SIZE), 3))
                .thenReturn(new PageImpl<>(secondPage, PageRequest.of(1, BATCH_SIZE), 3));

        searchIndexService.handle(event(SearchEntityType.USER, author.getId(), SearchIndexAction.UPSERT, true));

        verify(postRepository, times(2)).findByAuthorId(eq(author.getId()), any(Pageable.class));
        verify(postDocumentRepository, times(2)).saveAll(anyList());
        verify(userDocumentRepository).save(any(UserDocument.class));
    }

    @Test
    @DisplayName("reindexAll goes through more than one batch of posts and users, calling saveAll for each")
    void reindexAllProcessesEveryBatch() {
        User author = user("Autor");
        when(postRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(author, "A"), post(author, "B")), PageRequest.of(0, BATCH_SIZE), 3))
                .thenReturn(new PageImpl<>(List.of(post(author, "C")), PageRequest.of(1, BATCH_SIZE), 3));
        when(userRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(author), PageRequest.of(0, BATCH_SIZE), 1));

        searchIndexService.reindexAll();

        ArgumentCaptor<List<PostDocument>> postBatches = ArgumentCaptor.captor();
        verify(postDocumentRepository, times(2)).saveAll(postBatches.capture());
        assertEquals(2, postBatches.getAllValues().get(0).size());
        assertEquals(1, postBatches.getAllValues().get(1).size());
        verify(userDocumentRepository, times(1)).saveAll(anyList());
    }

    private static SearchIndexEvent event(SearchEntityType type, UUID id, SearchIndexAction action, boolean reindexAuthorPosts) {
        return SearchIndexEvent.builder()
                .entityType(type)
                .entityId(id)
                .action(action)
                .reindexAuthorPosts(reindexAuthorPosts)
                .occurredAt(LocalDateTime.now())
                .build();
    }

    private static User user(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        return user;
    }

    private static Post post(User author, String title) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setTitle(title);
        post.setContent("conteúdo de " + title);
        post.setDate(LocalDate.now());
        post.setUser(author);
        return post;
    }
}
