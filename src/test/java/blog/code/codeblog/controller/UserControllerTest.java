package blog.code.codeblog.controller;

import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.follow.FollowUnfollowRequestDTO;
import blog.code.codeblog.dto.user.UpdateUserRequestDTO;
import blog.code.codeblog.dto.user.UpdateUserResponseDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.dto.user.UserResponseDTO;
import blog.code.codeblog.facade.UserFacade;
import blog.code.codeblog.model.User;
import blog.code.codeblog.service.provider.UserProvider;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserFacade userFacade;

    @Mock
    private UserProvider userProvider;

    @InjectMocks
    private UserController userController;

    private UUID testUserId;
    private User currentUser;
    private UserResponseDTO testUserResponseDTO;

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        currentUser = new User();
        currentUser.setId(testUserId);
        testUserResponseDTO = UserResponseDTO.builder()
                .id(testUserId)
                .name("testuser")
                .login("testuser@email.com")
                .urlProfilePic("https://pic.jpg")
                .followersCount(10L)
                .followingCount(5L)
                .build();
    }

    @Test
    @DisplayName("Should delete current user successfully")
    void deleteUserShouldSucceed() {
        when(userProvider.getCurrentUser()).thenReturn(currentUser);
        doNothing().when(userFacade).deleteUser(currentUser);

        assertDoesNotThrow(() -> userController.deleteUser());
        verify(userFacade, times(1)).deleteUser(currentUser);
    }

    @Test
    @DisplayName("Should return user when user exists by id")
    void findUserByIdShouldReturnUserWhenUserExists() {
        when(userFacade.findUserById(testUserId)).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userController.findUserById(testUserId);

        assertNotNull(result);
        assertEquals("testuser", result.name());
        assertEquals("testuser@email.com", result.login());
        assertEquals(10L, result.followersCount());
        assertEquals(5L, result.followingCount());
        verify(userFacade, times(1)).findUserById(testUserId);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when user does not exist by id")
    void findUserByIdShouldThrowWhenUserDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(userFacade.findUserById(id))
                .thenThrow(new EntityNotFoundException("User not found with id: " + id));

        EntityNotFoundException exception = assertThrows(EntityNotFoundException.class,
                () -> userController.findUserById(id));

        assertEquals("User not found with id: " + id, exception.getMessage());
        verify(userFacade, times(1)).findUserById(id);
    }

    @Test
    @DisplayName("Should update current user and return updated user")
    void updateUserShouldReturnUpdatedUserWhenUserExists() {
        UpdateUserRequestDTO updateDTO = new UpdateUserRequestDTO("updateduser", "test@example.com", "newpassword", null);
        UpdateUserResponseDTO updatedResponse = new UpdateUserResponseDTO("updateduser", "test@example.com");
        when(userProvider.getCurrentUser()).thenReturn(currentUser);
        when(userFacade.updateUser(updateDTO, currentUser)).thenReturn(updatedResponse);

        UpdateUserResponseDTO response = userController.updateUser(updateDTO);

        assertNotNull(response);
        assertEquals("updateduser", response.name());
        assertEquals("test@example.com", response.email());
        verify(userFacade, times(1)).updateUser(updateDTO, currentUser);
    }

    @Test
    @DisplayName("Should follow user on behalf of current user")
    void followShouldDelegateToFacade() {
        FollowUnfollowRequestDTO request = new FollowUnfollowRequestDTO(testUserId);
        when(userProvider.getCurrentUser()).thenReturn(currentUser);

        assertDoesNotThrow(() -> userController.follow(request));
        verify(userFacade, times(1)).follow(request, currentUser);
    }

    @Test
    @DisplayName("Should unfollow user on behalf of current user")
    void unfollowShouldDelegateToFacade() {
        FollowUnfollowRequestDTO request = new FollowUnfollowRequestDTO(testUserId);
        when(userProvider.getCurrentUser()).thenReturn(currentUser);

        assertDoesNotThrow(() -> userController.unfollow(request));
        verify(userFacade, times(1)).unfollow(request, currentUser);
    }

    @Test
    @DisplayName("Should return followers page successfully")
    void getFollowersShouldReturnPageOfFollowers() {
        int page = 0;
        int size = 10;

        UserFollowDTO follower1 = UserFollowDTO.builder()
                .id(UUID.randomUUID())
                .name("Follower 1")
                .login("follower1@email.com")
                .urlProfilePic(null)
                .build();
        UserFollowDTO follower2 = UserFollowDTO.builder()
                .id(UUID.randomUUID())
                .name("Follower 2")
                .login("follower2@email.com")
                .urlProfilePic("https://pic.jpg")
                .build();
        PageResponseDTO<UserFollowDTO> followersPage = PageResponseDTO.<UserFollowDTO>builder()
                .content(List.of(follower1, follower2))
                .currentPage(0)
                .totalPages(1)
                .totalElements(2)
                .size(10)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(userFacade.getFollowers(testUserId, page, size)).thenReturn(followersPage);

        PageResponseDTO<UserFollowDTO> result = userController.getFollowers(testUserId, page, size);

        assertNotNull(result);
        assertEquals(2, result.totalElements());
        assertEquals("Follower 1", result.content().get(0).name());
        assertEquals("Follower 2", result.content().get(1).name());
        verify(userFacade, times(1)).getFollowers(testUserId, page, size);
    }

    @Test
    @DisplayName("Should return following page successfully")
    void getFollowingShouldReturnPageOfFollowing() {
        int page = 0;
        int size = 10;

        UserFollowDTO following1 = UserFollowDTO.builder()
                .id(UUID.randomUUID())
                .name("Following 1")
                .login("following1@email.com")
                .urlProfilePic(null)
                .build();
        UserFollowDTO following2 = UserFollowDTO.builder()
                .id(UUID.randomUUID())
                .name("Following 2")
                .login("following2@email.com")
                .urlProfilePic("https://pic.jpg")
                .build();
        PageResponseDTO<UserFollowDTO> followingPage = PageResponseDTO.<UserFollowDTO>builder()
                .content(List.of(following1, following2))
                .currentPage(0)
                .totalPages(1)
                .totalElements(2)
                .size(10)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(userFacade.getFollowing(testUserId, page, size)).thenReturn(followingPage);

        PageResponseDTO<UserFollowDTO> result = userController.getFollowing(testUserId, page, size);

        assertNotNull(result);
        assertEquals(2, result.totalElements());
        assertEquals("Following 1", result.content().get(0).name());
        assertEquals("Following 2", result.content().get(1).name());
        verify(userFacade, times(1)).getFollowing(testUserId, page, size);
    }

    @Test
    @DisplayName("Should get current user information successfully")
    void getMeShouldReturnCurrentUser() {
        when(userProvider.getCurrentUser()).thenReturn(currentUser);
        when(userFacade.getUserInformation(currentUser)).thenReturn(testUserResponseDTO);

        UserResponseDTO result = userController.getMe();

        assertNotNull(result);
        assertEquals(testUserId, result.id());
        assertEquals("testuser", result.name());
        assertEquals("testuser@email.com", result.login());
        assertEquals("https://pic.jpg", result.urlProfilePic());
        assertEquals(10L, result.followersCount());
        assertEquals(5L, result.followingCount());

        verify(userFacade, times(1)).getUserInformation(currentUser);
    }

    @Test
    @DisplayName("Should get current user information with minimal data")
    void getMeShouldReturnCurrentUserWithMinimalData() {
        UserResponseDTO minimalUserDTO = UserResponseDTO.builder()
                .id(testUserId)
                .name("newuser")
                .login("newuser@email.com")
                .urlProfilePic(null)
                .followersCount(0L)
                .followingCount(0L)
                .build();

        when(userProvider.getCurrentUser()).thenReturn(currentUser);
        when(userFacade.getUserInformation(currentUser)).thenReturn(minimalUserDTO);

        UserResponseDTO result = userController.getMe();

        assertNotNull(result);
        assertEquals(testUserId, result.id());
        assertEquals("newuser", result.name());
        assertEquals("newuser@email.com", result.login());
        assertNull(result.urlProfilePic());
        assertEquals(0L, result.followersCount());
        assertEquals(0L, result.followingCount());

        verify(userFacade, times(1)).getUserInformation(currentUser);
    }

}
