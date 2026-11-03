package blog.code.codeblog.integration.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;


public abstract class ElasticsearchContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchContainerSupport.class);

    private static final String IMAGE = "docker.elastic.co/elasticsearch/elasticsearch:9.4.5";

    private static final String URIS_PROPERTY = "elasticsearch.test.uris";
    private static final String URIS_ENV = "ELASTICSEARCH_TEST_URIS";

    private static final String SKIP_MESSAGE =
            "No Elasticsearch available for integration tests. Install/start Docker, or point "
                    + "to a disposable Elasticsearch with -D" + URIS_PROPERTY + "=http://<host>:9200.";

    private static final String ELASTICSEARCH_URIS = resolveElasticsearch();

    private static String resolveElasticsearch() {
        String externalUris = setting(URIS_PROPERTY, URIS_ENV);
        if (externalUris != null) {
            log.info("Using external Elasticsearch at {}", externalUris);
            return externalUris;
        }

        if (!DockerClientFactory.instance().isDockerAvailable()) {
            log.warn(SKIP_MESSAGE);
            return null;
        }

        ElasticsearchContainer container = new ElasticsearchContainer(DockerImageName.parse(IMAGE))
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .withStartupTimeout(Duration.ofMinutes(3));
        container.start();
        return "http://" + container.getHttpHostAddress();
    }

    private static String setting(String systemProperty, String environmentVariable) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return (value == null || value.isBlank()) ? null : value;
    }

    @BeforeAll
    static void requireElasticsearch() {
        Assumptions.assumeTrue(ELASTICSEARCH_URIS != null, SKIP_MESSAGE);
    }

    @DynamicPropertySource
    static void elasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> {
            Assertions.assertNotNull(ELASTICSEARCH_URIS);
            return ELASTICSEARCH_URIS;
        });
    }
}
