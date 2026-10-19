package blog.code.codeblog.config;

import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostAuthorDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.user.UserResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

    private static final long FOLLOWERS_TTL_MS = 300_000L;
    private static final long FOLLOWING_TTL_MS = 300_000L;
    private static final long USER_TTL_MS = 1_800_000L;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    private RedisConfig config;
    private JsonMapper redisObjectMapper;

    @BeforeEach
    void setUp() {
        config = new RedisConfig();
        setField(config, "followersTtl", FOLLOWERS_TTL_MS);
        setField(config, "followingTtl", FOLLOWING_TTL_MS);
        setField(config, "userTtl", USER_TTL_MS);
        redisObjectMapper = config.redisObjectMapper();
    }


    @Test
    @DisplayName("redisObjectMapper writes java.time dates as ISO strings")
    void redisObjectMapperWritesDatesAsIsoStrings() {
        assertEquals("\"2024-05-10\"", redisObjectMapper.writeValueAsString(LocalDate.of(2024, 5, 10)));
    }

    @Test
    @DisplayName("redisObjectMapper writes the @class type id for project DTOs")
    void redisObjectMapperWritesTypeId() {
        UserResponseDTO dto = new UserResponseDTO(
                UUID.randomUUID(), "User", "user@test.com", "http://pic", 1, 2);

        String json = redisObjectMapper.writeValueAsString(dto);

        assertTrue(json.contains("\"@class\":\"" + UserResponseDTO.class.getName() + "\""),
                "Expected an @class type id in " + json);
    }

    @Test
    @DisplayName("redisObjectMapper round-trips a PageResponseDTO back into the concrete DTO types")
    void redisObjectMapperRoundTripsPageResponseDTO() {
        PostResponseDTO post = new PostResponseDTO(
                UUID.randomUUID(),
                "Title",
                "Content",
                new PostAuthorDTO(UUID.randomUUID(), "Author"),
                LocalDate.of(2024, 5, 10),
                Map.of("img", "url"));

        PageResponseDTO<PostResponseDTO> page = PageResponseDTO.<PostResponseDTO>builder()
                .content(List.of(post))
                .currentPage(0)
                .totalPages(1)
                .totalElements(1)
                .size(10)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        String json = redisObjectMapper.writeValueAsString(page);
        Object restored = redisObjectMapper.readValue(json, Object.class);

        assertInstanceOf(PageResponseDTO.class, restored);

        @SuppressWarnings("unchecked")
        PageResponseDTO<PostResponseDTO> result = (PageResponseDTO<PostResponseDTO>) restored;

        assertEquals(1, result.content().size());
        assertInstanceOf(PostResponseDTO.class, result.content().getFirst());
        assertEquals(post, result.content().getFirst());
        assertEquals(page.totalElements(), result.totalElements());
        assertFalse(result.empty());
    }

    @Test
    @DisplayName("redisObjectMapper allows java.util collections used by the caches")
    void redisObjectMapperAllowsJavaUtilCollections() {
        List<UserResponseDTO> list = new ArrayList<>(List.of(
                new UserResponseDTO(UUID.randomUUID(), "User", "user@test.com", "http://pic", 1, 2)));

        Object restored = redisObjectMapper.readValue(redisObjectMapper.writeValueAsString(list), Object.class);

        assertInstanceOf(List.class, restored);
        assertInstanceOf(UserResponseDTO.class, ((List<?>) restored).get(0));
    }

    @Test
    @DisplayName("redisObjectMapper rejects type ids outside the polymorphic allowlist")
    void redisObjectMapperRejectsTypesOutsideAllowlist() {
        String maliciousJson = "{\"@class\":\"java.io.File\",\"path\":\"/etc/passwd\"}";

        assertThrows(JacksonException.class,
                () -> redisObjectMapper.readValue(maliciousJson, Object.class),
                "Types outside blog.code.codeblog/java.util must not be deserialized");
    }



    @Test
    @DisplayName("redisTemplate returns template with correct serializers and connection factory")
    void redisTemplateReturnsTemplateWithCorrectSerializers() {
        RedisTemplate<String, Object> template = config.redisTemplate(redisConnectionFactory);

        assertNotNull(template);
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(GenericJacksonJsonRedisSerializer.class, template.getValueSerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());
        assertInstanceOf(GenericJacksonJsonRedisSerializer.class, template.getHashValueSerializer());
        assertEquals(redisConnectionFactory, template.getConnectionFactory());
    }


    @Test
    @DisplayName("redisConnectionFactory applies host, port, credentials and timeouts")
    void redisConnectionFactoryAppliesConfiguredProperties() {
        setField(config, "redisHost", "redis.internal");
        setField(config, "redisPort", 6380);
        setField(config, "redisPassword", "test-password");
        setField(config, "redisUsername", "app-user");

        RedisConnectionFactory factory = config.redisConnectionFactory();

        assertInstanceOf(LettuceConnectionFactory.class, factory);
        LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) factory;

        assertEquals("redis.internal", lettuceFactory.getHostName());
        assertEquals(6380, lettuceFactory.getPort());
        assertEquals("app-user", lettuceFactory.getStandaloneConfiguration().getUsername());
        assertEquals("test-password", lettuceFactory.getPassword());

        assertEquals(Duration.ofSeconds(10), lettuceFactory.getClientConfiguration().getCommandTimeout());
        assertEquals(Duration.ofSeconds(10), lettuceFactory.getClientConfiguration()
                .getClientOptions()
                .orElseThrow()
                .getSocketOptions()
                .getConnectTimeout());
    }

    @Test
    @DisplayName("cacheManager registers exactly the configured caches")
    void cacheManagerRegistersConfiguredCaches() {
        RedisCacheManager manager = newCacheManager();
        manager.initializeCaches();

        assertEquals(
                Set.of(RedisConfig.FOLLOWERS_CACHE,
                        RedisConfig.FOLLOWING_CACHE,
                        RedisConfig.USER_CACHE,
                        RedisConfig.POST_CACHE,
                        RedisConfig.USER_POSTS_CACHE,
                        RedisConfig.POST_COMMENTS_CACHE,
                        RedisConfig.FEED_CACHE),
                Set.copyOf(manager.getCacheNames()));
    }

    @Test
    @DisplayName("cacheManager applies the TTL configured for each cache")
    void cacheManagerAppliesConfiguredTtlPerCache() {
        RedisCacheManager manager = newCacheManager();

        assertEquals(Duration.ofMillis(FOLLOWERS_TTL_MS), ttlOf(manager, RedisConfig.FOLLOWERS_CACHE));
        assertEquals(Duration.ofMillis(FOLLOWING_TTL_MS), ttlOf(manager, RedisConfig.FOLLOWING_CACHE));
        assertEquals(Duration.ofMillis(USER_TTL_MS), ttlOf(manager, RedisConfig.USER_CACHE));
        assertEquals(Duration.ofMinutes(15), ttlOf(manager, RedisConfig.POST_CACHE));
        assertEquals(Duration.ofMinutes(10), ttlOf(manager, RedisConfig.USER_POSTS_CACHE));
        assertEquals(Duration.ofMinutes(5), ttlOf(manager, RedisConfig.POST_COMMENTS_CACHE));
        assertEquals(Duration.ofMinutes(2), ttlOf(manager, RedisConfig.FEED_CACHE));
    }

    @Test
    @DisplayName("cacheManager falls back to a 10 minute TTL for caches without explicit config")
    void cacheManagerAppliesDefaultTtl() {
        assertEquals(Duration.ofMinutes(10), ttlOf(newCacheManager(), "some-unconfigured-cache"));
    }

    @Test
    @DisplayName("cacheManager never expires when the TTL property resolves to zero")
    void cacheManagerWithZeroTtlNeverExpires() {
        setField(config, "userTtl", 0L);

        assertEquals(Duration.ZERO, ttlOf(newCacheManager(), RedisConfig.USER_CACHE),
                "A TTL of 0 means no expiration at all - the property must never resolve to 0");
    }

    @Test
    @DisplayName("cacheManager disables caching of null values")
    void cacheManagerDisablesNullValues() {
        RedisCacheConfiguration cacheConfig = cacheConfigOf(newCacheManager(), RedisConfig.USER_CACHE);

        assertFalse(cacheConfig.getAllowCacheNullValues());
    }

    @Test
    @DisplayName("cacheManager uses String keys and JSON values")
    void cacheManagerUsesExpectedSerializers() {
        RedisCacheConfiguration cacheConfig = cacheConfigOf(newCacheManager(), RedisConfig.USER_CACHE);

        assertEquals("some-key", new String(readBytes(
                cacheConfig.getKeySerializationPair().write("some-key")), StandardCharsets.UTF_8));

        UserResponseDTO dto = new UserResponseDTO(
                UUID.randomUUID(), "User", "user@test.com", "http://pic", 1, 2);
        String serializedValue = new String(readBytes(
                cacheConfig.getValueSerializationPair().write(dto)), StandardCharsets.UTF_8);

        assertTrue(serializedValue.contains("\"@class\":\"" + UserResponseDTO.class.getName() + "\""),
                "Cache values should be JSON carrying the type id, got " + serializedValue);
    }


    @Test
    @DisplayName("cache constants have expected values")
    void cacheConstantsHaveExpectedValues() {
        assertEquals("followers-list", RedisConfig.FOLLOWERS_CACHE);
        assertEquals("following-list", RedisConfig.FOLLOWING_CACHE);
        assertEquals("user", RedisConfig.USER_CACHE);
        assertEquals("post", RedisConfig.POST_CACHE);
        assertEquals("user-posts", RedisConfig.USER_POSTS_CACHE);
        assertEquals("postComments", RedisConfig.POST_COMMENTS_CACHE);
        assertEquals("feed", RedisConfig.FEED_CACHE);
    }



    private RedisCacheManager newCacheManager() {
        RedisCacheManager manager = config.cacheManager(redisConnectionFactory);
        manager.initializeCaches();
        return manager;
    }

    private static RedisCacheConfiguration cacheConfigOf(RedisCacheManager manager, String cacheName) {
        RedisCache cache = (RedisCache) manager.getCache(cacheName);
        assertNotNull(cache, "Cache should exist: " + cacheName);
        return cache.getCacheConfiguration();
    }

    private static Duration ttlOf(RedisCacheManager manager, String cacheName) {
        return cacheConfigOf(manager, cacheName)
                .getTtlFunction()
                .getTimeToLive("any-key", "any-value");
    }

    private static byte[] readBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }
}
