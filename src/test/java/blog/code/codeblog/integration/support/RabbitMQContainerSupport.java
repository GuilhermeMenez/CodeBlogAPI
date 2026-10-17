package blog.code.codeblog.integration.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;


public abstract class RabbitMQContainerSupport extends RedisContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQContainerSupport.class);

    private static final int AMQP_PORT = 5672;

    private static final String HOST_PROPERTY = "rabbitmq.test.host";
    private static final String PORT_PROPERTY = "rabbitmq.test.port";
    private static final String HOST_ENV = "RABBITMQ_TEST_HOST";
    private static final String PORT_ENV = "RABBITMQ_TEST_PORT";

    private static final String SKIP_MESSAGE =
            "No RabbitMQ available for integration tests. Install/start Docker, or point "
                    + "to a disposable RabbitMQ with -D" + HOST_PROPERTY + "=<host> [-D" + PORT_PROPERTY + "=5672].";

    private static final Endpoint RABBITMQ = resolveRabbitMQ();

    private record Endpoint(String host, int port) {}

    private static Endpoint resolveRabbitMQ() {
        String externalHost = setting(HOST_PROPERTY, HOST_ENV);
        if (externalHost != null) {
            String externalPort = setting(PORT_PROPERTY, PORT_ENV);
            int port = externalPort != null ? Integer.parseInt(externalPort) : AMQP_PORT;
            log.info("Using external RabbitMQ at {}:{}", externalHost, port);
            return new Endpoint(externalHost, port);
        }

        if (!DockerClientFactory.instance().isDockerAvailable()) {
            log.warn(SKIP_MESSAGE);
            return null;
        }

        GenericContainer<?> container =
                new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-alpine"))
                        .withExposedPorts(AMQP_PORT)
                        .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1)
                                .withStartupTimeout(Duration.ofMinutes(2)));
        container.start();
        return new Endpoint(container.getHost(), container.getMappedPort(AMQP_PORT));
    }

    private static String setting(String systemProperty, String environmentVariable) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return (value == null || value.isBlank()) ? null : value;
    }

    @BeforeAll
    static void requireRabbitMQ() {
        Assumptions.assumeTrue(RABBITMQ != null, SKIP_MESSAGE);
    }

    @DynamicPropertySource
    static void rabbitMQProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> {
            Assertions.assertNotNull(RABBITMQ);
            return RABBITMQ.host();
        });
        registry.add("spring.rabbitmq.port", () -> {
            Assertions.assertNotNull(RABBITMQ);
            return RABBITMQ.port();
        });
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }
}
