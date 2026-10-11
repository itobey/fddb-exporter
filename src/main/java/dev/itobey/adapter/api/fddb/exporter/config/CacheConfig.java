package dev.itobey.adapter.api.fddb.exporter.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Caches overall statistics for two minutes; persistence writes evict the cache.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * The cache the overall statistics live in.
     */
    public static final String STATS_CACHE = "stats";

    private static final Duration STATS_TTL = Duration.ofMinutes(2);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(STATS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(STATS_TTL));
        cacheManager.setAllowNullValues(false);
        return cacheManager;
    }
}
