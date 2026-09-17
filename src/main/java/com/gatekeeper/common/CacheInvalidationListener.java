package com.gatekeeper.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Subscribes every instance to CacheInvalidationPublisher.CHANNEL and evicts the published key
 * locally — see doc/scope.md §8.4.
 */
@Configuration
public class CacheInvalidationListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public CacheInvalidationListener(RedisTemplate<String, Object> redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void handleMessage(String key) {
        redisTemplate.delete(key);
    }

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "handleMessage");
        adapter.setSerializer(stringRedisTemplate.getStringSerializer());
        adapter.afterPropertiesSet();
        container.addMessageListener(adapter, new ChannelTopic(CacheInvalidationPublisher.CHANNEL));
        return container;
    }
}
