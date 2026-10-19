package blog.code.codeblog.repository;

import blog.code.codeblog.model.Comment;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.enums.UserRoles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureCache
class CommentRepositoryTest {
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PostRepository postRepository;

    @Test
    @DisplayName("Deve persistir e buscar comentários por post")
    void persistAndFindByPost() {
        User user = new User("Comentador", "comentador@email.com", "senha", UserRoles.COSTUMER);
        userRepository.save(user);
        Post post = new Post();
        post.setAuthorName(user.getName());
        post.setDate(LocalDate.now());
        post.setContent("Post para comentário");
        post.setTitle("Título do Post");
        post.setUser(user);
        postRepository.save(post);
        Comment comment = new Comment();
        comment.setAutor(user.getName());
        comment.setPost(post);
        comment.setContent("Comentário de teste");
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUser(user);
        commentRepository.save(comment);
        List<Comment> comments = commentRepository.findAll();
        assertFalse(comments.isEmpty());
        assertEquals(post.getId(), comments.getFirst().getPost().getId());
    }
}
