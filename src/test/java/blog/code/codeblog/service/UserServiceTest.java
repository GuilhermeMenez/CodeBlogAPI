package blog.code.codeblog.service;

import blog.code.codeblog.command.user.*;
import blog.code.codeblog.dto.user.UpdateUserResponseDTO;
import blog.code.codeblog.enums.AuthFlow;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.messaging.event.UserFollowedEvent;
import blog.code.codeblog.messaging.publisher.CloudinaryUploadPublisher;
import blog.code.codeblog.messaging.publisher.UserFollowPublisher;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.UserFollow;
import blog.code.codeblog.repository.UserFollowRepository;
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
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFollowRepository userFollowRepository;
    @Mock
    private PasswordEncoder bCryptPasswordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private CloudinaryUploadPublisher cloudinaryUploadPublisher;
    @Mock
    private UserFollowPublisher userFollowPublisher;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should find user by ID successfully")
    void findByIdSuccess() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.setId(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        Optional<User> result = userService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        verify(userRepository).findById(id);
    }

    @Test
    @DisplayName("Should return empty when user not found by ID")
    void findByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        Optional<User> result = userService.findById(id);
        assertFalse(result.isPresent());
        verify(userRepository).findById(id);
    }

    @Test
    @DisplayName("Should find user by id as DTO successfully")
    void findUserByIdAsDtoSuccess() {
        UUID userId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        user.setName("John Doe");
        user.setLogin("john@email.com");
        user.setUrlProfilePic("https://cloudinary.com/profile.jpg");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userFollowRepository.countFollowersByUserId(userId)).thenReturn(5L);
        when(userFollowRepository.countFollowingByUserId(userId)).thenReturn(10L);

        var result = userService.findUserById(userId);

        assertNotNull(result);
        assertEquals(userId, result.id());
        assertEquals("John Doe", result.name());
        assertEquals("john@email.com", result.login());
        assertEquals("https://cloudinary.com/profile.jpg", result.urlProfilePic());
        assertEquals(5L, result.followersCount());
        assertEquals(10L, result.followingCount());

        verify(userRepository).findById(userId);
        verify(userFollowRepository).countFollowersByUserId(userId);
        verify(userFollowRepository).countFollowingByUserId(userId);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when user not found by id as DTO")
    void findUserByIdAsDtoNotFound() {
        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.findUserById(userId));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("Should find user by login successfully")
    void findByLoginSuccess() {
        User user = new User();
        user.setLogin("email@email.com");
        when(userRepository.findByLogin("email@email.com")).thenReturn(user);
        User result = userService.findByLogin("email@email.com");
        assertNotNull(result);
        assertEquals("email@email.com", result.getLogin());
        verify(userRepository).findByLogin("email@email.com");
    }

    @Test
    @DisplayName("Should return null when user not found by login")
    void findByLoginNotFound() {
        when(userRepository.findByLogin("notfound@email.com")).thenReturn(null);
        User result = userService.findByLogin("notfound@email.com");
        assertNull(result);
        verify(userRepository).findByLogin("notfound@email.com");
    }


    @Test
    @DisplayName("Should save user successfully with PASSWORD flow")
    void saveUserSuccess() {
        CreateUserCommand cmd = CreateUserCommand.builder()
                .name("John")
                .email("john@email.com")
                .credential("password123")
                .flow(AuthFlow.PASSWORD)
                .profileImage(null)
                .build();

        when(bCryptPasswordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = assertDoesNotThrow(() -> userService.saveUser(cmd));

        assertNotNull(saved);
        assertEquals("john@email.com", saved.getLogin());
        assertEquals("encodedPassword", saved.getPassword());
        verify(bCryptPasswordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
        verifyNoInteractions(cloudinaryUploadPublisher);
    }

    @Test
    @DisplayName("Should publish profile image upload event when user is saved with image")
    void saveUserWithProfileImagePublishesEvent() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile("profileImage", "avatar.png", "image/png", new byte[]{1, 2, 3});

        CreateUserCommand cmd = CreateUserCommand.builder()
                .name("John")
                .email("john@email.com")
                .credential("password123")
                .flow(AuthFlow.PASSWORD)
                .profileImage(image)
                .build();

        when(bCryptPasswordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        User saved = assertDoesNotThrow(() -> userService.saveUser(cmd));

        ArgumentCaptor<ImageUploadEvent> captor = ArgumentCaptor.forClass(ImageUploadEvent.class);
        verify(cloudinaryUploadPublisher).publishImageUpload(captor.capture());
        ImageUploadEvent event = captor.getValue();

        assertEquals(userId, saved.getId());
        assertEquals(FlowImageFlag.PROFILE, event.getFlag());
        assertEquals(userId, event.getUserId());
        assertNull(event.getPostId());
        assertArrayEquals(new byte[]{1, 2, 3}, event.getContent());
        assertEquals("avatar.png", event.getOriginalFilename());
        assertNotNull(event.getOccurredAt());
    }



    @Test
    @DisplayName("Should save user successfully with OTP flow generating random password")
    void saveUserSuccessOtpFlow() {
        CreateUserCommand cmd = CreateUserCommand.builder()
                .name("John")
                .email("john@email.com")
                .credential(null)
                .flow(AuthFlow.OTP)
                .profileImage(null)
                .build();

        when(bCryptPasswordEncoder.encode(any(String.class))).thenReturn("encodedRandom");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = assertDoesNotThrow(() -> userService.saveUser(cmd));

        assertNotNull(saved);
        assertEquals("encodedRandom", saved.getPassword());
        verify(userRepository).save(any(User.class));
        verify(bCryptPasswordEncoder).encode(any(String.class));
    }


    @Test
    @DisplayName("Should update user successfully")
    void updateUserSuccess() {
        UUID id = UUID.randomUUID();

        User existingUser = new User();
        existingUser.setId(id);
        existingUser.setName("Old Name");
        existingUser.setLogin("old@email.com");
        existingUser.setPassword("oldPassword");

        UpdateUserCommand command = UpdateUserCommand.builder()
                .userId(id)
                .name("New Name")
                .email("new@email.com")
                .password("newPassword")
                .build();

        when(userRepository.findById(id)).thenReturn(Optional.of(existingUser));
        when(bCryptPasswordEncoder.encode("newPassword")).thenReturn("encodedNewPassword");

        UpdateUserResponseDTO response = userService.updateUser(command);

        assertEquals("New Name", response.name());
        assertEquals("new@email.com", response.email());

        verify(userRepository).findById(id);
        verify(bCryptPasswordEncoder).encode("newPassword");

        verifyNoMoreInteractions(userRepository);
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void updateUserNotFound() {
        UUID id = UUID.randomUUID();
        UpdateUserCommand command = UpdateUserCommand.builder()
                .userId(id)
                .name("Name")
                .email("email@email.com")
                .password("password")
                .build();
        when(userRepository.findById(id)).thenReturn(Optional.empty());
        RuntimeException exception = assertThrows(RuntimeException.class, () -> userService.updateUser(command));
        assertEquals("User not found", exception.getMessage());
        verify(userRepository).findById(id);
    }

    @Test
    @DisplayName("Should delete user successfully")
    void deleteUserSuccess() {
        UUID id = UUID.randomUUID();
        DeleteUserCommand command = DeleteUserCommand.builder().userId(id).build();
        when(userRepository.existsById(id)).thenReturn(true);
        doNothing().when(userRepository).deleteById(id);
        userService.deleteUser(command);
        verify(userRepository).existsById(id);
        verify(userRepository).deleteById(id);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void deleteUserNotFound() {
        UUID id = UUID.randomUUID();
        DeleteUserCommand command = DeleteUserCommand.builder().userId(id).build();
        when(userRepository.existsById(id)).thenReturn(false);
        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class, () -> userService.deleteUser(command));
        assertEquals("User not found with id: " + id, exception.getMessage());
        verify(userRepository).existsById(id);
        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should follow user successfully")
    void followUserSuccess() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        User follower = new User();
        follower.setId(followerId);
        User followed = new User();
        followed.setId(followedId);

        FollowCommand command = FollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followedId)).thenReturn(Optional.of(followed));
        when(userFollowRepository.save(any(UserFollow.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> userService.follow(command));

        verify(userFollowRepository).save(any(UserFollow.class));

        ArgumentCaptor<UserFollowedEvent> captor = ArgumentCaptor.forClass(UserFollowedEvent.class);
        verify(userFollowPublisher).publishUserFollowed(captor.capture());
        assertEquals(followerId, captor.getValue().getFollowerId());
        assertEquals(followedId, captor.getValue().getFollowedId());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when already following")
    void followUserAlreadyFollowing() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        User follower = new User();
        follower.setId(followerId);
        User followed = new User();
        followed.setId(followedId);

        FollowCommand command = FollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followedId)).thenReturn(Optional.of(followed));
        when(userFollowRepository.save(any(UserFollow.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.follow(command));
        assertEquals("User already follows this user", exception.getMessage());
    }

    @Test
    @DisplayName("Should unfollow user successfully")
    void unfollowUserSuccess() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        UnfollowCommand command = UnfollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userFollowRepository
                .deleteByFollower_IdAndFollowed_Id(followerId, followedId))
                .thenReturn(1);

        assertDoesNotThrow(() -> userService.unfollow(command));

        verify(userFollowRepository)
                .deleteByFollower_IdAndFollowed_Id(followerId, followedId);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when not following")
    void unfollowUserNotFollowing() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        UnfollowCommand command = UnfollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userFollowRepository.deleteByFollower_IdAndFollowed_Id(followerId, followedId)).thenReturn(0);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userService.unfollow(command));
        assertEquals("User does not follow this user", exception.getMessage());
        verify(userFollowRepository).deleteByFollower_IdAndFollowed_Id(followerId, followedId);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when follower and followed are the same user")
    void followSameUserThrowsException() {
        UUID sameId = UUID.randomUUID();

        FollowCommand command = FollowCommand.builder().followerId(sameId).followedId(sameId).build();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userService.follow(command));
        assertEquals("Cannot follow yourself", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when follower not found")
    void followFollowerNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        FollowCommand command = FollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.follow(command));
        assertEquals(followerId + " not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when followed not found")
    void followFollowedNotFound() {
        UUID followerId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();
        User follower = new User();
        follower.setId(followerId);

        FollowCommand command = FollowCommand.builder().followerId(followerId).followedId(followedId).build();

        when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
        when(userRepository.findById(followedId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.follow(command));
        assertEquals(followedId + " not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should get user reference by ID")
    void getReferenceSuccess() {
        UUID id = UUID.randomUUID();
        User user = new User();
        when(userRepository.getReferenceById(id)).thenReturn(user);
        User result = userService.getReference(id);
        assertEquals(user, result);
        verify(userRepository).getReferenceById(id);
    }

    @Test
    @DisplayName("Should save profile pic when upload completed event is received")
    void onProfileImageUploadedSuccess() {
        UUID userId = UUID.randomUUID();
        String profilePicUrl = "https://cloudinary.com/profile.jpg";
        String profilePicId = "profile_pics/test-image";
        User existingUser = new User();
        existingUser.setId(userId);
        existingUser.setLogin("user@email.com");

        ImageUploadCompletedEvent event = new ImageUploadCompletedEvent(FlowImageFlag.PROFILE, userId, null, profilePicUrl, profilePicId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.onProfileImageUploaded(event);

        assertEquals(profilePicUrl, existingUser.getUrlProfilePic());
        assertEquals(profilePicId, existingUser.getProfilePicId());
        verify(userRepository).findById(userId);
        verify(userRepository).save(existingUser);
        verifyNoInteractions(tokenService);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when upload completed event references non-existent user")
    void onProfileImageUploadedUserNotFound() {
        UUID userId = UUID.randomUUID();
        ImageUploadCompletedEvent event = new ImageUploadCompletedEvent(FlowImageFlag.PROFILE, userId, null,
                "https://cloudinary.com/profile.jpg", "profile_pics/test-image");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.onProfileImageUploaded(event));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository).findById(userId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete profile pic successfully")
    void deleteProfilePicSuccess() {
        String publicId = "profile_pics/test-image";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUrlProfilePic("https://cloudinary.com/profile.jpg");
        user.setProfilePicId(publicId);

        when(userRepository.findByProfilePicId(publicId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean result = userService.deleteProfilePic(publicId);

        assertTrue(result);
        assertNull(user.getUrlProfilePic());
        assertNull(user.getProfilePicId());
        verify(userRepository).findByProfilePicId(publicId);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should return false when profile pic not found for deletion")
    void deleteProfilePicNotFound() {
        String publicId = "profile_pics/nonexistent-image";

        when(userRepository.findByProfilePicId(publicId)).thenReturn(Optional.empty());

        boolean result = userService.deleteProfilePic(publicId);

        assertFalse(result);
        verify(userRepository).findByProfilePicId(publicId);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get followers successfully with pagination")
    void getFollowersSuccess() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        GetFollowersCommand command = GetFollowersCommand.builder().userId(userId).page(0).size(10).build();

        User follower1 = new User();
        follower1.setId(UUID.randomUUID());
        follower1.setName("Follower 1");
        follower1.setLogin("follower1@email.com");
        follower1.setUrlProfilePic("https://cloudinary.com/pic1.jpg");

        User follower2 = new User();
        follower2.setId(UUID.randomUUID());
        follower2.setName("Follower 2");
        follower2.setLogin("follower2@email.com");

        Page<User> followersPage = new PageImpl<>(List.of(follower1, follower2), pageable, 2);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(userFollowRepository.findFollowersByUserId(userId, pageable)).thenReturn(followersPage);

        var result = userService.getFollowers(command);

        assertNotNull(result);
        assertEquals(2, result.totalElements());
        verify(userRepository).existsById(userId);
        verify(userFollowRepository).findFollowersByUserId(userId, pageable);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when getting followers for non-existent user")
    void getFollowersUserNotFound() {
        UUID userId = UUID.randomUUID();
        GetFollowersCommand command = GetFollowersCommand.builder().userId(userId).page(0).size(10).build();

        when(userRepository.existsById(userId)).thenReturn(false);

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.getFollowers(command));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository).existsById(userId);
    }

    @Test
    @DisplayName("Should get following successfully with pagination")
    void getFollowingSuccess() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        GetFollowingCommand command = GetFollowingCommand.builder().userId(userId).page(0).size(10).build();

        User following1 = new User();
        following1.setId(UUID.randomUUID());
        following1.setName("Following 1");
        following1.setLogin("following1@email.com");
        following1.setUrlProfilePic("https://cloudinary.com/pic1.jpg");

        User following2 = new User();
        following2.setId(UUID.randomUUID());
        following2.setName("Following 2");
        following2.setLogin("following2@email.com");

        Page<User> followingPage = new PageImpl<>(List.of(following1, following2), pageable, 2);

        when(userRepository.existsById(userId)).thenReturn(true);
        when(userFollowRepository.findFollowingByUserId(userId, pageable)).thenReturn(followingPage);

        var result = userService.getFollowing(command);

        assertNotNull(result);
        assertEquals(2, result.totalElements());
        verify(userRepository).existsById(userId);
        verify(userFollowRepository).findFollowingByUserId(userId, pageable);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when getting following for non-existent user")
    void getFollowingUserNotFound() {
        UUID userId = UUID.randomUUID();
        GetFollowingCommand command = GetFollowingCommand.builder().userId(userId).page(0).size(10).build();

        when(userRepository.existsById(userId)).thenReturn(false);

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userService.getFollowing(command));

        assertEquals("User not found with id: " + userId, exception.getMessage());
        verify(userRepository).existsById(userId);
    }
}
