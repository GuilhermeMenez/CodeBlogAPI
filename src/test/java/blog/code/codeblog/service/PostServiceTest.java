package blog.code.codeblog.service;

import blog.code.codeblog.command.post.CreatePostCommand;
import blog.code.codeblog.command.post.DeletePostCommand;
import blog.code.codeblog.command.post.DeletePostImageCommand;
import blog.code.codeblog.command.post.GetAllUserPostsCommand;
import blog.code.codeblog.command.post.GetPostCommentsCommand;
import blog.code.codeblog.command.post.PutPostCommand;
import blog.code.codeblog.command.post.SavePostImageCommand;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.messaging.publisher.CloudinaryUploadPublisher;
import blog.code.codeblog.model.Comment;
import blog.code.codeblog.model.Post;
import blog.code.codeblog.model.User;
import blog.code.codeblog.repository.CommentRepository;
import blog.code.codeblog.repository.PostRepository;
import blog.code.codeblog.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private CloudinaryUploadPublisher cloudinaryUploadPublisher;
    @InjectMocks
    private PostService postService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create post successfully and return postId")
    void createPostShouldReturnId() {
        UUID userId = UUID.randomUUID();
        User author = new User();
        author.setId(userId);
        author.setName("Author");

        CreatePostCommand command = CreatePostCommand.builder()
                .title("Test Title")
                .content("Test Content")
                .authorName("Author")
                .author(author)
                .images(null)
                .build();

        Post mockPost = new Post();
        mockPost.setId(UUID.randomUUID());
        mockPost.setTitle(command.getTitle());
        mockPost.setContent(command.getContent());
        mockPost.setDate(LocalDate.now());
        mockPost.setUser(author);

        when(postRepository.save(any(Post.class))).thenReturn(mockPost);

        String result = postService.createPost(command);

        assertEquals(mockPost.getId().toString(), result);
        verify(postRepository).save(any(Post.class));
        verifyNoInteractions(cloudinaryUploadPublisher);
    }

    @Test
    @DisplayName("Should publish one upload event per image when creating post with images")
    void createPostWithImagesShouldPublishEvents() {
        UUID userId = UUID.randomUUID();
        User author = new User();
        author.setId(userId);
        author.setName("Author");

        List<MultipartFile> images = List.of(
                new MockMultipartFile("images", "first.png", "image/png", new byte[]{1}),
                new MockMultipartFile("images", "second.png", "image/png", new byte[]{2})
        );

        CreatePostCommand command = CreatePostCommand.builder()
                .title("Test Title")
                .content("Test Content")
                .authorName("Author")
                .author(author)
                .images(images)
                .build();

        Post mockPost = new Post();
        mockPost.setId(UUID.randomUUID());
        mockPost.setUser(author);

        when(postRepository.save(any(Post.class))).thenReturn(mockPost);
        when(postRepository.findById(mockPost.getId())).thenReturn(Optional.of(mockPost));

        String result = postService.createPost(command);

        assertEquals(mockPost.getId().toString(), result);
        verify(cloudinaryUploadPublisher, times(2)).publishImageUpload(any(ImageUploadEvent.class));
    }

    @Test
    @DisplayName("Should find post by id and return PostResponseDTO")
    void findByIdShouldReturnResponseDTO() {
        UUID postId = UUID.randomUUID();
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Author");
        Post post = new Post();
        post.setId(postId);
        post.setTitle("Title");
        post.setContent("Content");
        post.setDate(LocalDate.now());
        post.setUser(user);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        PostResponseDTO result = postService.findById(postId);
        assertNotNull(result);
        assertEquals(postId, result.postId());
        assertEquals("Title", result.title());
        assertEquals("Content", result.content());
        assertEquals(user.getId(), result.author().getId());
        assertEquals(user.getName(), result.author().getName());
        verify(postRepository).findById(postId);
    }

    @Test
    @DisplayName("Should throw RuntimeException when post not found by id")
    void findByIdNotFoundShouldThrow() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () -> postService.findById(postId));
        assertEquals("Post not found", exception.getMessage());
        verify(postRepository).findById(postId);
    }

    @Test
    @DisplayName("Should update post successfully and return PostResponseDTO")
    void updatePostShouldReturnResponseDTO() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PutPostCommand command = PutPostCommand.builder()
                .postId(postId)
                .title("Updated Title")
                .content("Updated Content")
                .authorId(userId)
                .build();
        User user = new User();
        user.setId(userId);
        user.setName("Author");
        Post post = new Post();
        post.setId(postId);
        post.setTitle("Old Title");
        post.setContent("Old Content");
        post.setDate(LocalDate.now().minusDays(1));
        post.setUser(user);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);
        PostResponseDTO result = postService.updatePost(command);
        assertNotNull(result);
        assertEquals(postId, result.postId());
        assertEquals("Updated Title", result.title());
        assertEquals("Updated Content", result.content());
        assertEquals(user.getId(), result.author().getId());
        assertEquals(user.getName(), result.author().getName());
        verify(postRepository).findById(postId);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when post not found on update")
    void updatePostNotFoundShouldThrow() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PutPostCommand command = PutPostCommand.builder()
                .postId(postId)
                .title("Title")
                .content("Content")
                .authorId(userId)
                .build();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> postService.updatePost(command));
        assertEquals("Post not found", exception.getMessage());
        verify(postRepository).findById(postId);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user not authorized to update post")
    void updatePostUnauthorizedShouldThrow() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        PutPostCommand command = PutPostCommand.builder()
                .postId(postId)
                .title("Title")
                .content("Content")
                .authorId(userId)
                .build();
        User owner = new User();
        owner.setId(otherUserId);
        Post post = new Post();
        post.setId(postId);
        post.setUser(owner);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> postService.updatePost(command));
        assertEquals("User not authorized for this action", exception.getMessage());
        verify(postRepository).findById(postId);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("Should delete post successfully when authorized")
    void deletePostShouldSucceed() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DeletePostCommand command = DeletePostCommand.builder().postId(postId).authorId(userId).build();
        User user = new User();
        user.setId(userId);
        Post post = new Post();
        post.setId(postId);
        post.setUser(user);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        doNothing().when(postRepository).deleteById(postId);
        assertDoesNotThrow(() -> postService.deletePost(command));
        verify(postRepository).findById(postId);
        verify(postRepository).deleteById(postId);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when post not found on delete")
    void deletePostNotFoundShouldThrow() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DeletePostCommand command = DeletePostCommand.builder().postId(postId).authorId(userId).build();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> postService.deletePost(command));
        assertEquals("Post not found", exception.getMessage());
        verify(postRepository).findById(postId);
        verify(postRepository, never()).deleteById(postId);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user not authorized to delete post")
    void deletePostUnauthorizedShouldThrow() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        DeletePostCommand command = DeletePostCommand.builder().postId(postId).authorId(userId).build();
        User owner = new User();
        owner.setId(otherUserId);
        Post post = new Post();
        post.setId(postId);
        post.setUser(owner);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        AccessDeniedException exception = assertThrows(AccessDeniedException.class, () -> postService.deletePost(command));
        assertEquals("User not authorized for this action", exception.getMessage());
        verify(postRepository).findById(postId);
        verify(postRepository, never()).deleteById(postId);
    }

    @Test
    @DisplayName("Should get all posts for user")
    void getAllUserPostsShouldReturnPosts() {
        UUID userId = UUID.randomUUID();
        int page = 0;
        int size = 10;
        User user = new User();
        user.setId(userId);
        user.setName("Test User");

        GetAllUserPostsCommand command = GetAllUserPostsCommand.builder().user(user).page(page).size(size).build();

        Post post1 = new Post();
        post1.setId(UUID.randomUUID());
        post1.setTitle("First Post");
        post1.setContent("Content 1");
        post1.setAuthorName("Test User");
        post1.setDate(LocalDate.now());
        post1.setUser(user);

        Post post2 = new Post();
        post2.setId(UUID.randomUUID());
        post2.setTitle("Second Post");
        post2.setContent("Content 2");
        post2.setAuthorName("Test User");
        post2.setDate(LocalDate.now());
        post2.setUser(user);

        Page<Post> postPage = new PageImpl<>(List.of(post1, post2), PageRequest.of(page, size), 2);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(postRepository.findByAuthorId(eq(userId), any())).thenReturn(postPage);

        var result = postService.getAllUserPosts(command);

        assertNotNull(result);
        assertEquals(2, result.content().size());
        assertEquals("First Post", result.content().get(0).title());
        assertEquals("Second Post", result.content().get(1).title());
        assertEquals(0, result.currentPage());
        assertEquals(1, result.totalPages());
        assertEquals(2, result.totalElements());
        assertTrue(result.first());
        assertTrue(result.last());
        verify(userRepository).existsById(userId);
        verify(postRepository).findByAuthorId(eq(userId), any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when user not found for getAllUserPosts")
    void getAllUserPostsUserNotFoundShouldThrow() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        GetAllUserPostsCommand command = GetAllUserPostsCommand.builder().user(user).page(0).size(10).build();
        when(userRepository.existsById(userId)).thenReturn(false);
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> postService.getAllUserPosts(command));
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).existsById(userId);
    }

    @Test
    @DisplayName("Should delete image from post successfully")
    void deleteImageSuccess() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        String publicId = "post_pics/test-image";

        User user = new User();
        user.setId(userId);

        Post post = new Post();
        post.setId(postId);
        post.setUser(user);
        Map<String, String> images = new HashMap<>();
        images.put(publicId, "https://cloudinary.com/test-image.jpg");
        post.setImages(images);

        DeletePostImageCommand command = DeletePostImageCommand.builder().publicId(publicId).user(user).build();

        when(postRepository.findByImagePublicId(publicId)).thenReturn(Optional.of(post));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = postService.deleteImage(command);

        assertTrue(result);
        assertFalse(post.getImages().containsKey(publicId));
        verify(postRepository).findByImagePublicId(publicId);
        verify(postRepository).findById(postId);
        verify(cloudinaryService).deleteFile(publicId);
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("Should return false when image not found for deletion")
    void deleteImageNotFound() throws IOException {
        String publicId = "post_pics/nonexistent-image";
        DeletePostImageCommand command = DeletePostImageCommand.builder().publicId(publicId).build();

        when(postRepository.findByImagePublicId(publicId)).thenReturn(Optional.empty());

        boolean result = postService.deleteImage(command);

        assertFalse(result);
        verify(postRepository).findByImagePublicId(publicId);
        verify(postRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should publish image upload event and return processing message")
    void savePostImageShouldPublishEvent() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        User author = new User();
        author.setId(userId);

        Post post = new Post();
        post.setId(postId);
        post.setUser(author);

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3});
        SavePostImageCommand command = SavePostImageCommand.builder().postId(postId).file(file).author(author).build();

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        var result = postService.savePostImage(command);

        ArgumentCaptor<ImageUploadEvent> captor = ArgumentCaptor.forClass(ImageUploadEvent.class);
        verify(cloudinaryUploadPublisher).publishImageUpload(captor.capture());
        ImageUploadEvent event = captor.getValue();

        assertEquals("Upload em processamento", result.message());
        assertEquals(FlowImageFlag.POST, event.getFlag());
        assertEquals(userId, event.getUserId());
        assertEquals(postId, event.getPostId());
        assertArrayEquals(new byte[]{1, 2, 3}, event.getContent());
        assertEquals("image.png", event.getOriginalFilename());
        assertNotNull(event.getOccurredAt());
        verify(postRepository, never()).save(any(Post.class));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("Should not publish image upload event when user is not the post author")
    void savePostImageUnauthorizedShouldNotPublish() {
        UUID postId = UUID.randomUUID();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());

        Post post = new Post();
        post.setId(postId);
        post.setUser(owner);

        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[]{1});
        SavePostImageCommand command = SavePostImageCommand.builder().postId(postId).file(file).author(otherUser).build();

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThrows(AccessDeniedException.class, () -> postService.savePostImage(command));
        verifyNoInteractions(cloudinaryUploadPublisher);
    }

    @Test
    @DisplayName("Should add image to post when upload completed event is received")
    void onPostImageUploadedShouldAddImage() {
        UUID postId = UUID.randomUUID();
        Post post = new Post();
        post.setId(postId);
        post.setImages(null);

        ImageUploadCompletedEvent event = new ImageUploadCompletedEvent(FlowImageFlag.POST, UUID.randomUUID(), postId,
                "https://cloudinary.com/post-image.jpg", "post_pics/post-image");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.onPostImageUploaded(event);

        assertEquals("https://cloudinary.com/post-image.jpg", post.getImages().get("post_pics/post-image"));
        verify(postRepository).findById(postId);
        verify(postRepository).save(post);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when upload completed event references non-existent post")
    void onPostImageUploadedPostNotFoundShouldThrow() {
        UUID postId = UUID.randomUUID();
        ImageUploadCompletedEvent event = new ImageUploadCompletedEvent(FlowImageFlag.POST, UUID.randomUUID(), postId,
                "https://cloudinary.com/post-image.jpg", "post_pics/post-image");

        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> postService.onPostImageUploaded(event));

        assertEquals("Post not found", exception.getMessage());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("Should get post reference by ID")
    void getReferenceSuccess() {
        UUID postId = UUID.randomUUID();
        Post post = new Post();
        post.setId(postId);

        when(postRepository.getReferenceById(postId)).thenReturn(post);

        Post result = postService.getReference(postId);

        assertEquals(post, result);
        verify(postRepository).getReferenceById(postId);
    }

    @Test
    @DisplayName("Should retrieve comments for a post successfully")
    void getPostCommentsShouldSucceed() {
        UUID postId = UUID.randomUUID();
        int page = 0;
        int size = 5;
        GetPostCommentsCommand command = GetPostCommentsCommand.builder().postId(postId).page(page).size(size).build();

        Comment comment1 = new Comment();
        comment1.setId(UUID.randomUUID());
        comment1.setContent("First comment");
        comment1.setAutor("Author 1");
        comment1.setCreatedAt(LocalDateTime.now());

        Comment comment2 = new Comment();
        comment2.setId(UUID.randomUUID());
        comment2.setContent("Second comment");
        comment2.setAutor("Author 2");
        comment2.setCreatedAt(LocalDateTime.now());

        Page<Comment> commentsPage = new PageImpl<>(List.of(comment1, comment2), PageRequest.of(page, size), 2);

        when(commentRepository.findByPost_Id(eq(postId), any())).thenReturn(commentsPage);

        var result = postService.getPostComments(command);

        assertNotNull(result);
        assertEquals(2, result.content().size());
        assertEquals("First comment", result.content().get(0).content());
        assertEquals("Second comment", result.content().get(1).content());
        assertEquals(0, result.currentPage());
        assertEquals(1, result.totalPages());
        assertEquals(2, result.totalElements());
        assertTrue(result.first());
        assertTrue(result.last());
        verify(commentRepository).findByPost_Id(eq(postId), any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when no comments exist for the post")
    void getPostCommentsEmptyPage() {
        UUID postId = UUID.randomUUID();
        int page = 0;
        int size = 5;
        GetPostCommentsCommand command = GetPostCommentsCommand.builder().postId(postId).page(page).size(size).build();

        Page<Comment> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);

        when(commentRepository.findByPost_Id(eq(postId), any())).thenReturn(emptyPage);

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> postService.getPostComments(command));
        assertTrue(exception.getMessage().contains("No comments found for postId:"));
        verify(commentRepository).findByPost_Id(eq(postId), any());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when the post does not exist")
    void getPostCommentsPostNotFoundShouldThrow() {
        UUID postId = UUID.randomUUID();
        int page = 0;
        int size = 5;
        GetPostCommentsCommand command = GetPostCommentsCommand.builder().postId(postId).page(page).size(size).build();

        when(commentRepository.findByPost_Id(eq(postId), any())).thenReturn(Page.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> postService.getPostComments(command));

        assertEquals("No comments found for postId: " + postId, exception.getMessage());
        verify(commentRepository).findByPost_Id(eq(postId), any());
    }
}
