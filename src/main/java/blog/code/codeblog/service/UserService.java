package blog.code.codeblog.service;

import blog.code.codeblog.command.user.*;
import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.cloudinary.ImageUploadResponseDTO;
import blog.code.codeblog.dto.user.UpdateUserResponseDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.dto.user.UserResponseDTO;
import blog.code.codeblog.enums.AuthFlow;
import blog.code.codeblog.enums.FlowImageFlag;
import blog.code.codeblog.mapper.PageMapper;
import blog.code.codeblog.mapper.UserMapper;
import blog.code.codeblog.messaging.event.ImageUploadCompletedEvent;
import blog.code.codeblog.messaging.event.ImageUploadEvent;
import blog.code.codeblog.messaging.event.UserFollowedEvent;
import blog.code.codeblog.messaging.publisher.CloudinaryUploadPublisher;
import blog.code.codeblog.messaging.publisher.UserFollowPublisher;
import blog.code.codeblog.model.User;
import blog.code.codeblog.model.UserFollow;
import blog.code.codeblog.repository.UserFollowRepository;
import blog.code.codeblog.repository.UserRepository;
import blog.code.codeblog.service.interfaces.UserServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static blog.code.codeblog.config.RedisConfig.*;

@Slf4j
@Service
public class UserService implements UserServiceInterface {
    @Autowired
    UserRepository userRepository;

    @Autowired
    UserFollowRepository userFollowRepository;

    @Autowired
    PasswordEncoder bCryptPasswordEncoder;

    @Autowired
    CloudinaryUploadPublisher cloudinaryUploadPublisher;



    @Autowired
    UserFollowPublisher userFollowPublisher;

    @Override
    public Optional<User> findById(UUID id) {
        log.info("[findById] Finding user by id: {}", id);
        return userRepository.findById(id);
    }

    @Override
    @Cacheable(value = USER_CACHE, key = "#id", unless = "#result == null")
    public UserResponseDTO findUserById(UUID id) {
        log.info("[findByIdAsDTO] Finding user by id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("[findByIdAsDTO] User not found. id: {}", id);
                    return new EntityNotFoundException("User not found with id: " + id);
                });
        return UserMapper.toUserResponseDTO(
                user,
                userFollowRepository.countFollowersByUserId(user.getId()),
                userFollowRepository.countFollowingByUserId(user.getId())
        );
    }


    @Override
    public User findByLogin(String login) {
        log.info("[findByLogin] Attempting to find user by login: {}", login);
        return userRepository.findByLogin(login);
    }

    @Override
    public User saveUser(CreateUserCommand user) {
        log.info("[saveUser] Saving user with login: {}", user.getEmail());

        if (user.getFlow() == AuthFlow.OTP) {
            log.warn("[saveUser] Password is empty for user: {}. Generating a random password.", user.getEmail());
            var randomPassword = (UUID.randomUUID().toString());
            user.setCredential(randomPassword);
        }

        String encryptedPassword = bCryptPasswordEncoder.encode(user.getCredential());
        user.setCredential(encryptedPassword);

        User newUser = UserMapper.toUserEntity(user);
        userRepository.save(newUser);

        log.info("[saveUser] User saved successfully. login: {}", user.getEmail());


        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            log.info("[register] Processing profile image for user: {}", newUser.getId());
            try {
                log.info("[saveUser] Publishing profile image upload for user: {}", newUser.getId());
                cloudinaryUploadPublisher.publishImageUpload(ImageUploadEvent.builder()
                        .flag(FlowImageFlag.PROFILE)
                        .userId(newUser.getId())
                        .content(user.getProfileImage().getBytes())
                        .originalFilename(user.getProfileImage().getOriginalFilename())
                        .occurredAt(LocalDateTime.now())
                        .build());
            } catch (IOException e) {
                log.error("[saveUser] Failed to read profile image for user: {}. Error: {}", newUser.getId(), e.getMessage());
            }
        }
        return newUser;
    }

    //todo alterar para permitir o upload de fotos
    @Override
    @Transactional
    @CacheEvict(value = USER_CACHE, key = "#command.userId")
    public UpdateUserResponseDTO updateUser(UpdateUserCommand command) {
        log.info("[updateUser] Attempting to update user with id: {}", command.getUserId());

        User existingUser = userRepository.findById(command.getUserId())
                .orElseThrow(() -> {
                    log.warn("[updateUser] User not found. id: {}", command.getUserId());
                    return new EntityNotFoundException("User not found");
                });

        if (command.getName() != null) existingUser.setName(command.getName());
        if (command.getEmail() != null) existingUser.setLogin(command.getEmail());
        if (command.getPassword() != null) existingUser.setPassword(bCryptPasswordEncoder.encode(command.getPassword()));

        log.info("[updateUser] User updated successfully. id: {}", command.getUserId());
        return UserMapper.toUpdateUserResponseDTO(existingUser);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = USER_CACHE, key = "#command.userId"),
            @CacheEvict(value = FOLLOWERS_CACHE, allEntries = true),
            @CacheEvict(value = FOLLOWING_CACHE, allEntries = true)
    })
    public void deleteUser(DeleteUserCommand command) {
        log.info("[deleteUser] Attempting to delete user with id: {}", command.getUserId());
        if (!userRepository.existsById(command.getUserId())) {
            log.warn("[deleteUser] User not found for deletion. id: {}", command.getUserId());
            throw new EntityNotFoundException("User not found with id: " + command.getUserId());
        }
        userRepository.deleteById(command.getUserId());
        log.info("[deleteUser] User deleted successfully. id: {}", command.getUserId());
    }


    @CacheEvict(value = USER_CACHE, key = "#event.userId()")
    public void onProfileImageUploaded(ImageUploadCompletedEvent event) {
        saveUploadProfilePic(event.userId(), event.imageUrl(), event.publicId());
    }


    private ImageUploadResponseDTO saveUploadProfilePic(UUID userId, String profilePicUrl, String profilePicId) throws AccessDeniedException {
        log.info("[updateProfilePic] Attempting to update profile pic for user with id: {}", userId);

        User existingUser = findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        existingUser.setUrlProfilePic(profilePicUrl);
        existingUser.setProfilePicId(profilePicId);
        userRepository.save(existingUser);

        log.info("[updateProfilePic] Profile pic updated successfully. id for user: {}", existingUser.getLogin());

        return ImageUploadResponseDTO.builder()
                .message("Profile pic uploaded successfully")
                .imageUrl(profilePicUrl)
                .publicId(profilePicId)
                .build();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = FOLLOWERS_CACHE, allEntries = true),
            @CacheEvict(value = FOLLOWING_CACHE, allEntries = true)
    })
    public void follow(FollowCommand command) {
        validateNotSameUser(command.getFollowerId(), command.getFollowedId());
        User follower = findUserOrThrow(command.getFollowerId());
        User followed = findUserOrThrow(command.getFollowedId());

        try {
            userFollowRepository.save(UserFollow.builder()
                    .follower(follower)
                    .followed(followed)
                    .build());
        } catch (DataIntegrityViolationException e) {
            log.warn("[follow] User already follows this user. followerId: {}, followedId: {}", command.getFollowerId(), command.getFollowedId());
            throw new IllegalStateException("User already follows this user");
        }
        log.info("[follow] Follow operation successful. followerId: {}, followedId: {}", command.getFollowerId(), command.getFollowedId());

        userFollowPublisher.publishUserFollowed(UserFollowedEvent.builder()
                .followerId(follower.getId())
                .followerName(follower.getName())
                .followedId(followed.getId())
                .followedName(followed.getName())
                .occurredAt(java.time.LocalDateTime.now())
                .build());

    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = FOLLOWERS_CACHE, allEntries = true),
            @CacheEvict(value = FOLLOWING_CACHE, allEntries = true)
    })
    public void unfollow(UnfollowCommand command) {
        log.info("[unfollow] Attempting to unfollow user. followerId: {}, followedId: {}", command.getFollowerId(), command.getFollowedId());
        validateNotSameUser(command.getFollowerId(), command.getFollowedId());

        int deleted = userFollowRepository.deleteByFollower_IdAndFollowed_Id(command.getFollowerId(), command.getFollowedId());
        if (deleted == 0) {
            log.warn("[unfollow] User does not follow this user. followerId: {}, followedId: {}", command.getFollowerId(), command.getFollowedId());
            throw new IllegalStateException("User does not follow this user");
        }
        log.info("[unfollow] Unfollow operation successful. followerId: {}, followedId: {}", command.getFollowerId(), command.getFollowedId());

    }

    private void validateNotSameUser(UUID followerId, UUID followedId) {
        if (followedId.equals(followerId)) {
            log.warn("[validateNotSameUser] Follower and followed are the same user. id: {}", followerId);
            throw new IllegalArgumentException("Cannot follow yourself");
        }
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("[findUserOrThrow] user not found. id: {}", userId);
                    return new EntityNotFoundException(userId + " not found");
                });
    }

    @Override
    public User getReference(UUID id) {
        log.info("[getReference] Getting reference for user id: {}", id);
        return userRepository.getReferenceById(id);
    }


    //todo jogar isso na fila também
    @Override
    @CacheEvict(value = USER_CACHE, allEntries = true)
    public boolean deleteProfilePic(String publicId) {
        log.info("[deleteProfilePic] Attempting to delete profile pic with publicId: {}", publicId);
        Optional<User> userOpt = userRepository.findByProfilePicId(publicId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setUrlProfilePic(null);
            user.setProfilePicId(null);
            userRepository.save(user);
            log.info("[deleteProfilePic] Profile pic removed from user: {}", user.getId());
            return true;
        }
        return false;
    }


    @Override
    @Cacheable(
            value = FOLLOWERS_CACHE,
            key = "#command.userId + '_' + #command.page + '_' + #command.size",
            unless = "#result.empty == true"
    )
    public PageResponseDTO<UserFollowDTO> getFollowers(GetFollowersCommand command) {
        log.info("[getFollowers] Getting followers for user id: {} (page: {}, size: {})", command.getUserId(), command.getPage(), command.getSize());

        if (!userRepository.existsById(command.getUserId())) {
            log.warn("[getFollowers] User not found. id: {}", command.getUserId());
            throw new EntityNotFoundException("User not found with id: " + command.getUserId());
        }

        Pageable pageable = PageRequest.of(command.getPage(), command.getSize());
        Page<User> followersPage = userFollowRepository.findFollowersByUserId(command.getUserId(), pageable);

        return PageMapper.toPageResponseDTO(followersPage, UserMapper::toUserFollowDTO);
    }

    @Override
    @Cacheable(
            value = FOLLOWING_CACHE,
            key = "#command.userId + '_' + #command.page + '_' + #command.size",
            unless = "#result.empty == true"
    )
    public PageResponseDTO<UserFollowDTO> getFollowing(GetFollowingCommand command) {
        log.info("[getFollowing] Getting following for user id: {} (page: {}, size: {})", command.getUserId(), command.getPage(), command.getSize());

        if (!userRepository.existsById(command.getUserId())) {
            log.warn("[getFollowing] User not found. id: {}", command.getUserId());
            throw new EntityNotFoundException("User not found with id: " + command.getUserId());
        }

        Pageable pageable = PageRequest.of(command.getPage(), command.getSize());
        Page<User> followingPage = userFollowRepository.findFollowingByUserId(command.getUserId(), pageable);

        return PageMapper.toPageResponseDTO(followingPage, UserMapper::toUserFollowDTO);
    }



}
