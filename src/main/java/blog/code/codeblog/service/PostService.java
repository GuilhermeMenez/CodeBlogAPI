package blog.code.codeblog.service;

import blog.code.codeblog.command.post.*;
import blog.code.codeblog.mapper.PageMapper;
import blog.code.codeblog.mapper.PostMapper;
import blog.code.codeblog.mapper.CommentMapper;
import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.dto.comment.CommentResponseDTO;
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
import blog.code.codeblog.repository.UserFollowRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.service.interfaces.PostServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static blog.code.codeblog.config.RedisConfig.*;

@Slf4j
@Service
public class PostService implements PostServiceInterface {

    @Autowired
    PostRepository postRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    CloudinaryService cloudinaryService;
    @Autowired
    CommentRepository commentRepository;
    @Autowired
    UserFollowRepository userFollowRepository;
    @Autowired
    CloudinaryUploadPublisher cloudinaryUploadPublisher;

    @Override
    public List<PostResponseDTO> findAll() {
        log.info("[findAll] Retrieving all posts");
        return postRepository.findAll().stream()
                .map(PostMapper::toPostResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(
            value = USER_POSTS_CACHE,
            key = "#command.getUser().getId() + '_' + #command.getPage() + '_' + #command.getSize()",
            unless = "#result.empty == true"
    )
    public PageResponseDTO<PostResponseDTO> getAllUserPosts(GetAllUserPostsCommand command) throws EntityNotFoundException {
        log.info("[getAllUserPosts] Getting all posts for userId: {} (page: {}, size: {})", command.getUser().getId(), command.getPage(), command.getSize());

        if (!userRepository.existsById(command.getUser().getId())) {
            log.warn("[getAllUserPosts] User not found. userId: {}", command.getUser().getId());
            throw new EntityNotFoundException("User not found");
        }

        Pageable pageable = PageRequest.of(command.getPage(), command.getSize());
        Page<Post> postPage = postRepository.findByAuthorId(command.getUser().getId(), pageable);


        return PageMapper.toPageResponseDTO(postPage, PostMapper::toPostResponseDTO);

    }

    @Override
    @Cacheable(value = POST_CACHE, key = "#id", unless = "#result == null")
    public PostResponseDTO findById(UUID id) {
        log.info("[findById] Attempting to find post with id: {}", id);
        return postRepository.findById(id).map(PostMapper::toPostResponseDTO)
                .orElseThrow(() -> {
                    log.warn("[findById] Post not found. id: {}", id);
                    return new RuntimeException("Post not found");
                });
    }


    @Caching(evict = {
            @CacheEvict(value = USER_POSTS_CACHE, allEntries = true),
            @CacheEvict(value = FEED_CACHE, allEntries = true)
    })
    public String createPost(CreatePostCommand postCommand){
        Post savedPost = save(postCommand);
        try {
            processImages(savedPost, postCommand.getImages(), postCommand.getAuthor());
        } catch (IOException e) {
            log.error("[createPost] Error processing images for postId: {}. Error: {}", savedPost.getId(), e.getMessage());
            throw new RuntimeException("Error processing images for post: " + savedPost.getId(), e);
        }
        return savedPost.getId().toString();
    }


    private Post save(CreatePostCommand post) {
        log.info("[save] Attempting to save new post for authorId: {}", post.getAuthor().getId());

        Post savedPost = postRepository.save(PostMapper.toPostEntity(post));

        log.info("[save] Post saved successfully. postId: {}", savedPost.getId());
        return savedPost;
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = POST_CACHE, key = "#deletePostCommand.postId"),
            @CacheEvict(value = USER_POSTS_CACHE, allEntries = true),
            @CacheEvict(value = FEED_CACHE, allEntries = true)
    })
    public void deletePost(DeletePostCommand deletePostCommand) {
        log.info("[deletePost] Attempting to delete post. postId: {}", deletePostCommand.getPostId());
        Post post = getUserPost(deletePostCommand.getPostId(), deletePostCommand.getAuthorId());
        postRepository.deleteById(post.getId());
        log.info("[deletePost] Post deleted successfully. postId: {}", post.getId());
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = POST_CACHE, key = "#putPostCommand.postId"),
            @CacheEvict(value = USER_POSTS_CACHE, allEntries = true)
    })
    public PostResponseDTO updatePost(PutPostCommand putPostCommand) throws EntityNotFoundException {
        log.info("[updatePost] Attempting to update post. postId: {}", putPostCommand.getPostId());
        var existingPost = getUserPost(putPostCommand.getPostId(), putPostCommand.getAuthorId());

        existingPost.setTitle(putPostCommand.getTitle());
        existingPost.setContent(putPostCommand.getContent());
        existingPost.setDate(LocalDate.now());
        postRepository.save(existingPost);
        log.info("[updatePost] Post updated successfully. postId: {}", existingPost.getId());
        return PostMapper.toPostResponseDTO(existingPost);
    }

    public ImageUploadResponseDTO savePostImage(SavePostImageCommand savePostImageCommand) throws IOException {
        Post post = getUserPost(savePostImageCommand.getPostId(), savePostImageCommand.getAuthor().getId());
        log.info("[savePostImage] Publishing image upload for postId: {}", savePostImageCommand.getPostId());

        cloudinaryUploadPublisher.publishImageUpload(ImageUploadEvent.builder()
                .flag(FlowImageFlag.POST)
                .userId(post.getUser().getId())
                .postId(post.getId())
                .content(savePostImageCommand.getFile().getBytes())
                .originalFilename(savePostImageCommand.getFile().getOriginalFilename())
                .occurredAt(LocalDateTime.now())
                .build());

        return ImageUploadResponseDTO.builder()
                .message("Upload em processamento")
                .build();
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = POST_CACHE, key = "#event.postId()"),
            @CacheEvict(value = USER_POSTS_CACHE, allEntries = true)
    })
    public void onPostImageUploaded(ImageUploadCompletedEvent event) {
        Post post = postRepository.findById(event.postId())
                .orElseThrow(() -> {
                    log.warn("[onPostImageUploaded] Post not found. postId: {}", event.postId());
                    return new EntityNotFoundException("Post not found");
                });

        if (post.getImages() == null)
            post.setImages(new HashMap<>());

        post.getImages().put(event.publicId(), event.imageUrl());
        postRepository.save(post);
        log.info("[onPostImageUploaded] Image saved for postId: {}", event.postId());
    }


    public void processImages(Post post, List<MultipartFile> images, User auhtor) throws IOException {
        if (images != null && !images.isEmpty()) {
            log.info("[save] Processing {} images for post: {}", images.size(), post.getId());
            for (MultipartFile image : images) {
                if (image != null) {
                    savePostImage(PostMapper.toSavePostImageCommand(post.getId(), image, auhtor));
                }
            }
        } else {
            log.info("[save] No images found for post: {}", post.getId());
        }
    }


    private Post getUserPost(UUID postId, UUID userId) {
        log.info("[getUserPost] Getting post for user. postId: {}, userId: {}", postId, userId);
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> {
                    log.warn("[getUserPost] Post not found. postId: {}", postId);
                    return new EntityNotFoundException("Post not found");
                });

        if (!post.getUser().getId().equals(userId)) {
            log.warn("[getUserPost] User not authorized for this action. userId: {}, postId: {}", userId, postId);
            throw new AccessDeniedException("User not authorized for this action");
        }
        return post;
    }


    public Post getReference(UUID id) {
        return postRepository.getReferenceById(id);
    }

    @CacheEvict(value = USER_POSTS_CACHE, allEntries = true)
    public boolean deleteImage(DeletePostImageCommand command) throws IOException {
        log.info("[deleteImage] Attempting to delete image with publicId: {}", command.getPublicId());
        Optional<Post> postOpt = postRepository.findByImagePublicId(command.getPublicId());
        if (postOpt.isPresent()) {
            Post post = postOpt.get();
            getUserPost(post.getId(), UUID.fromString(command.getUser().getId().toString()));
            post.getImages().remove(command.getPublicId());
            cloudinaryService.deleteFile(command.getPublicId());
            postRepository.save(post);
            log.info("[deleteImage] Image removed from post: {}", post.getId());
            return true;
        }
        return false;
    }


    @Override
    @Cacheable(value = POST_COMMENTS_CACHE, key = "#command.getPostId() + '_' + #command.getPage() + '_' + #command.getSize()")
    public PageResponseDTO<CommentResponseDTO> getPostComments(GetPostCommentsCommand command) {
        log.info("[getAllComments] Getting all comments for postId: {} (page: {}, size: {})", command.getPostId(), command.getPage(), command.getSize());
        Pageable pageable = PageRequest.of(command.getPage(),command.getSize());

        Page<Comment> commentPage = commentRepository.findByPost_Id(command.getPostId(), pageable);

        if (commentPage.isEmpty()) {
            log.warn("[getAllComments] No comments found for postId: {}", command.getPostId());
            throw new EntityNotFoundException("No comments found for postId: " + command.getPostId());
        }

        return PageMapper.toPageResponseDTO(commentPage, CommentMapper::toCommentResponseDTO);
    }







}
