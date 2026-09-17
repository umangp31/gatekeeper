package com.gatekeeper.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AbstractIntegrationTestSmokeTest extends AbstractIntegrationTest {

    @Test
    void containersStartAndFlywayApplies() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
    }
}
