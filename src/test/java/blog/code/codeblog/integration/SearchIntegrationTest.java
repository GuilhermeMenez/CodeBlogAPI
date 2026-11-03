package blog.code.codeblog.integration;

import blog.code.codeblog.command.search.SearchPostsCommand;
import blog.code.codeblog.command.search.SearchUsersCommand;
import blog.code.codeblog.command.search.SuggestCommand;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.search.PostSuggestionDTO;
import blog.code.codeblog.dto.search.SearchSuggestionsDTO;
import blog.code.codeblog.dto.search.UserSuggestionDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.integration.support.ElasticsearchContainerSupport;
import blog.code.codeblog.mapper.SearchDocumentMapper;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.search.PostDocument;
import blog.code.codeblog.model.search.UserDocument;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.repository.search.PostDocumentRepository;
import blog.code.codeblog.repository.search.UserDocumentRepository;
import blog.code.codeblog.service.search.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

// @Transactional: as linhas do Postgres (H2) são desfeitas no rollback; os documentos do ES são removidos no @AfterEach
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchIntegrationTest extends ElasticsearchContainerSupport {

    @Autowired
    private SearchService searchService;
    @Autowired
    private ElasticsearchOperations operations;
    @Autowired
    private PostDocumentRepository postDocumentRepository;
    @Autowired
    private UserDocumentRepository userDocumentRepository;
    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;

    private final List<UUID> createdPostIds = new ArrayList<>();
    private final List<UUID> createdUserIds = new ArrayList<>();

    private User guilherme;
    private Post javascriptAvancado;
    private Post javaIniciantes;
    private Post programacaoFuncional;
    private Post quarkusNoTitulo;
    private Post quarkusNoConteudo;

    @BeforeEach
    void setUp() {
        createIndexIfMissing(PostDocument.class);
        createIndexIfMissing(UserDocument.class);

        guilherme = user("Guilherme Menezes");
        user("Jane Doe");
        user("Janaína Souza");
        user("Jaqueline Lima");

        javascriptAvancado = post(guilherme, "JavaScript avançado", "Closures, promises e async/await.");
        javaIniciantes = post(guilherme, "Java para iniciantes", "Classes, objetos e herança.");
        programacaoFuncional = post(guilherme, "Programação funcional", "Funções puras e imutabilidade.");
        quarkusNoTitulo = post(guilherme, "Quarkus na prática", "Um framework Java nativo para nuvem.");
        quarkusNoConteudo = post(guilherme, "Frameworks modernos", "Comparando Spring Boot com quarkus e Micronaut.");

        refresh();
    }

    @AfterEach
    void tearDown() {
        // remove apenas o que este teste criou — nada de apagar o índice inteiro
        postDocumentRepository.deleteAllById(createdPostIds);
        userDocumentRepository.deleteAllById(createdUserIds);
        refresh();
    }

    @Test
    @DisplayName("'javscript' (typo) finds 'JavaScript avançado'")
    void typoFindsJavaScript() {
        assertTrue(searchPostIds("javscript").contains(javascriptAvancado.getId()));
    }

    @Test
    @DisplayName("'programacao' (without accents) finds 'Programação funcional'")
    void withoutAccentsFindsProgramacao() {
        assertTrue(searchPostIds("programacao").contains(programacaoFuncional.getId()));
    }

    @Test
    @DisplayName("'gui' finds the user 'Guilherme'")
    void prefixFindsUser() {
        List<UUID> ids = searchService.searchUsers(SearchUsersCommand.builder().query("gui").page(0).size(50).build())
                .content().stream().map(UserFollowDTO::id).toList();

        assertTrue(ids.contains(guilherme.getId()));
    }

    @Test
    @DisplayName("'jav' (incomplete word) finds 'JavaScript avançado' through title.suggest")
    void prefixFindsJavaScript() {
        assertTrue(searchPostIds("jav").contains(javascriptAvancado.getId()));
    }

    @Test
    @DisplayName("'progr' (incomplete word) finds 'Programação funcional'")
    void prefixFindsProgramacao() {
        assertTrue(searchPostIds("progr").contains(programacaoFuncional.getId()));
    }

    @Test
    @DisplayName("suggest with limit 2 returns at most 2 posts and 2 users, with title and name filled in")
    void suggestRespectsLimit() {
        SearchSuggestionsDTO suggestions = searchService.suggest(SuggestCommand.builder().query("ja").limit(2).build());

        assertFalse(suggestions.posts().isEmpty());
        assertFalse(suggestions.users().isEmpty());
        assertTrue(suggestions.posts().size() <= 2);
        assertTrue(suggestions.users().size() <= 2);
        suggestions.posts().stream().map(PostSuggestionDTO::title).forEach(SearchIntegrationTest::assertNotBlank);
        suggestions.users().stream().map(UserSuggestionDTO::name).forEach(SearchIntegrationTest::assertNotBlank);
    }

    @Test
    @DisplayName("a match in the title ranks above a match only in the content")
    void titleMatchRanksAboveContentMatch() {
        List<UUID> ids = searchPostIds("quarkus");

        assertTrue(ids.contains(quarkusNoTitulo.getId()));
        assertTrue(ids.contains(quarkusNoConteudo.getId()));
        assertTrue(ids.indexOf(quarkusNoTitulo.getId()) < ids.indexOf(quarkusNoConteudo.getId()));
    }

    @Test
    @DisplayName("after deleteById the document no longer shows up in the results")
    void deletedDocumentIsNotReturned() {
        assertTrue(searchPostIds("iniciantes").contains(javaIniciantes.getId()));

        postDocumentRepository.deleteById(javaIniciantes.getId());
        refresh();

        assertFalse(searchPostIds("iniciantes").contains(javaIniciantes.getId()));
    }

    private List<UUID> searchPostIds(String query) {
        return searchService.searchPosts(SearchPostsCommand.builder().query(query).page(0).size(50).build())
                .content().stream().map(PostResponseDTO::postId).toList();
    }

    private User user(String name) {
        User user = userRepository.saveAndFlush(
                new User(name, UUID.randomUUID() + "@integration.test", "password"));
        userDocumentRepository.save(SearchDocumentMapper.toUserDocument(user));
        createdUserIds.add(user.getId());
        return user;
    }

    private Post post(User author, String title, String content) {
        Post post = postRepository.saveAndFlush(Post.builder()
                .title(title)
                .content(content)
                .authorName(author.getLogin())
                .date(LocalDate.now())
                .user(author)
                .build());
        postDocumentRepository.save(SearchDocumentMapper.toPostDocument(post));
        createdPostIds.add(post.getId());
        return post;
    }

    private void createIndexIfMissing(Class<?> type) {
        IndexOperations ops = operations.indexOps(type);
        if (!ops.exists()) {
            ops.createWithMapping();
        }
    }

    private void refresh() {
        operations.indexOps(PostDocument.class).refresh();
        operations.indexOps(UserDocument.class).refresh();
    }

    private static void assertNotBlank(String value) {
        assertNotNull(value);
        assertFalse(value.isBlank());
    }
}
