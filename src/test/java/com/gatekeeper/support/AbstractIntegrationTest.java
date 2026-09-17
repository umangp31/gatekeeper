package com.gatekeeper.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for integration tests: real Postgres and real Redis via Testcontainers,
 * reused across the suite. See doc/scope.md §10.
 */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine")).withReuse(true);

    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.ssl.enabled", () -> false);
    }

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetDatabase() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' "
                        + "AND tablename <> 'flyway_schema_history'",
                String.class);
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
        }
    }
}
