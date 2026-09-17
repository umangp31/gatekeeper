package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class LocalRedisConfigIT {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    void roundTripsAValueThroughRedis() {
        String key = "gk:test:" + UUID.randomUUID();
        redisTemplate.opsForValue().set(key, "hello", Duration.ofSeconds(30));

        Object value = redisTemplate.opsForValue().get(key);

        assertThat(value).isEqualTo("hello");
        redisTemplate.delete(key);
    }
}
