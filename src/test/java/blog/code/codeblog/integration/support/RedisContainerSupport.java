package blog.code.codeblog.integration.support;

import blog.code.codeblog.config.RedisConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public abstract class RedisContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(RedisContainerSupport.class);

    private static final int REDIS_PORT = 6379;

    private static final String HOST_PROPERTY = "redis.test.host";
    private static final String PORT_PROPERTY = "redis.test.port";
    private static final String HOST_ENV = "REDIS_TEST_HOST";
    private static final String PORT_ENV = "REDIS_TEST_PORT";

    public static final String TEST_KEY_PREFIX = "integration:test:";

    private static final String SKIP_MESSAGE =
            "No Redis available for integration tests. Install/start Docker, or point "
                    + "to a disposable Redis with -D" + HOST_PROPERTY + "=<host> [-D" + PORT_PROPERTY + "=6379].";

    private static final Endpoint REDIS = resolveRedis();

    private record Endpoint(String host, int port) {}

    private static Endpoint resolveRedis() {
        String externalHost = setting(HOST_PROPERTY, HOST_ENV);
        if (externalHost != null) {
            String externalPort = setting(PORT_PROPERTY, PORT_ENV);
            int port = externalPort != null ? Integer.parseInt(externalPort) : REDIS_PORT;
            log.info("Using external Redis at {}:{}", externalHost, port);
            return new Endpoint(externalHost, port);
        }

        if (!DockerClientFactory.instance().isDockerAvailable()) {
            log.warn(SKIP_MESSAGE);
            return null;
        }

        GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);
        container.start();
        return new Endpoint(container.getHost(), container.getMappedPort(REDIS_PORT));
    }

    private static String setting(String systemProperty, String environmentVariable) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return (value == null || value.isBlank()) ? null : value;
    }

    @BeforeAll
    static void requireRedis() {
        Assumptions.assumeTrue(REDIS != null, SKIP_MESSAGE);
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> {
            Assertions.assertNotNull(REDIS);
            return REDIS.host();
        });
        registry.add("spring.data.redis.port", () -> {
            Assertions.assertNotNull(REDIS);
            return REDIS.port();
        });
    }


    protected static void clearTestKeys(RedisTemplate<String, Object> redisTemplate) {
        List<String> patterns = new ArrayList<>(List.of(TEST_KEY_PREFIX + "*"));
        for (String cacheName : List.of(
                RedisConfig.FOLLOWERS_CACHE,
                RedisConfig.FOLLOWING_CACHE,
                RedisConfig.USER_CACHE,
                RedisConfig.POST_CACHE,
                RedisConfig.USER_POSTS_CACHE,
                RedisConfig.POST_COMMENTS_CACHE,
                RedisConfig.FEED_CACHE)) {
            patterns.add(cacheName + "::*");
        }

        for (String pattern : patterns) {
            Set<String> keys = redisTemplate.keys(pattern);
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }
}
