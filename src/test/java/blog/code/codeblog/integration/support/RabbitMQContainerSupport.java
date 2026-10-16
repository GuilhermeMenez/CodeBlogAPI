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

/**
 * Sobe um RabbitMQ efêmero (e o Redis, herdado de {@link RedisContainerSupport}, usado pelos @CacheEvict
 * acionados no callback do upload). Sem Docker, os testes são ignorados.
 */
public abstract class RabbitMQContainerSupport extends RedisContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQContainerSupport.class);

    private static final int AMQP_PORT = 5672;

    private static final String SKIP_MESSAGE =
            "Nenhum RabbitMQ disponível para os testes de integração. Instale/inicie um Docker.";

    private static final Endpoint RABBITMQ = resolveRabbitMQ();

    private record Endpoint(String host, int port) {}

    private static Endpoint resolveRabbitMQ() {
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
