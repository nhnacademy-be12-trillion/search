package com.nhnacademy.search.service;

import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Service
public class AiSearchCacheService {

    private static final Duration TTL = Duration.ofMinutes(3);
    private static final Duration REFRESH_AHEAD = Duration.ofSeconds(30);
    private static final Duration REFRESH_LOCK_TTL = Duration.ofSeconds(90);

    private final RedisTemplate<String, BookSearchResponse> redis; // 캐시 값 저장용
    private final StringRedisTemplate stringRedis;                 // 락/TTL 조회용
    private final Executor executor;

    public AiSearchCacheService(
            @Qualifier("aiSearchRedisTemplate")
            RedisTemplate<String, BookSearchResponse> redis,

            StringRedisTemplate stringRedis,

            @Qualifier("aiCacheRefreshExecutor")
            Executor executor
    ) {
        this.redis = redis;
        this.stringRedis = stringRedis;
        this.executor = executor;
    }

    public BookSearchResponse getOrCompute(BookSearchRequest req, java.util.function.Supplier<BookSearchResponse> loader) {
        String key = buildKey(req);

        BookSearchResponse cached = redis.opsForValue().get(key);
        if (cached != null) {
            log.debug("[AI-CACHE] HIT key={}", key);
            refreshIfNeededAsync(key, loader);
            return cached;
        }

        log.debug("[AI-CACHE] MISS key={}", key);
        BookSearchResponse fresh = loader.get();
        redis.opsForValue().set(key, fresh, TTL);
        return fresh;
    }

    private void refreshIfNeededAsync(String key, java.util.function.Supplier<BookSearchResponse> loader) {
        Long ttlSec = stringRedis.getExpire(key, TimeUnit.SECONDS);
        if (ttlSec == null || ttlSec < 0) return;
        if (ttlSec > REFRESH_AHEAD.toSeconds()) return;

        String lockKey = key + ":refresh_lock";
        Boolean locked = stringRedis.opsForValue().setIfAbsent(lockKey, "1", REFRESH_LOCK_TTL);
        if (locked == null || !locked) return;

        executor.execute(() -> {
            try {
                BookSearchResponse fresh = loader.get();
                redis.opsForValue().set(key, fresh, TTL);
                log.debug("[AI-CACHE] refreshed key={}, ttl={}s", key, TTL.toSeconds());
            } catch (Exception e) {
                log.warn("[AI-CACHE] refresh failed key={}", key, e);
            } finally {
                stringRedis.delete(lockKey);
            }
        });
    }

    private String buildKey(BookSearchRequest req) {
        String q = (req.query() == null) ? "" : req.query().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String sort = (req.sort() == null) ? "RELEVANCE" : req.sort().name();
        int page = Math.max(req.page(), 0);
        int size = Math.max(req.size(), 1);

        String version = "v1"; // 파이프라인/프롬프트 바꾸면 올려서 캐시 전체 무효화
        String qHash = Integer.toHexString(q.hashCode());

        return "team1:ai:" + version + ":" + qHash + ":" + sort + ":" + page + ":" + size;
    }
}

