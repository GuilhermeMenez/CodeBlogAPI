package blog.code.codeblog.config;

import blog.code.codeblog.dto.PageResponseDTO;
import blog.code.codeblog.dto.post.PostAuthorDTO;
import blog.code.codeblog.dto.post.PostResponseDTO;
import blog.code.codeblog.dto.user.UserFollowDTO;
import blog.code.codeblog.dto.user.UserResponseDTO;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
    abstract static class RedisCacheMixin {}

    @Value("${cache.ttl.followers:300000}")
    private long followersTtl;

    @Value("${cache.ttl.following:300000}")
    private long followingTtl;

    @Value("${cache.ttl.user:1800000}")
    private long userTtl;

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${spring.data.redis.username:default}")
    private String redisUsername;

    public static final String FOLLOWERS_CACHE = "followers-list";
    public static final String FOLLOWING_CACHE  = "following-list";
    public static final String USER_CACHE       = "user";
    public static final String POST_CACHE       = "post";
    public static final String USER_POSTS_CACHE = "user-posts";
    public static final String POST_COMMENTS_CACHE = "postComments";
    public static final String FEED_CACHE = "feed";


    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setUsername(redisUsername);
        redisConfig.setPassword(redisPassword);

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .build();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofSeconds(10))
                .build();

        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    // Não é @Bean: um bean JsonMapper impediria o Boot de auto-configurar o JsonMapper usado pelo MVC
    public JsonMapper redisObjectMapper() {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("blog.code.codeblog")
                .allowIfSubType("java.util")
                .build();

        return JsonMapper.builder()
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY)
                .addMixIn(PageResponseDTO.class, RedisCacheMixin.class)
                .addMixIn(UserFollowDTO.class, RedisCacheMixin.class)
                .addMixIn(UserResponseDTO.class, RedisCacheMixin.class)
                .addMixIn(PostResponseDTO.class, RedisCacheMixin.class)
                .addMixIn(PostAuthorDTO.class,   RedisCacheMixin.class)
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {

        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(redisObjectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();

        return template;
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(redisObjectMapper());

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put(FOLLOWERS_CACHE, defaultConfig.entryTtl(Duration.ofMillis(followersTtl)));
        cacheConfigs.put(FOLLOWING_CACHE,  defaultConfig.entryTtl(Duration.ofMillis(followingTtl)));
        cacheConfigs.put(USER_CACHE,       defaultConfig.entryTtl(Duration.ofMillis(userTtl)));
        cacheConfigs.put(POST_CACHE,       defaultConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigs.put(USER_POSTS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(10)));
        cacheConfigs.put(POST_COMMENTS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigs.put(FEED_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(2)));



        RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
                RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}