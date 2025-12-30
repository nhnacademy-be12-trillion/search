package com.nhnacademy.search.service;

import com.nhnacademy.search.dto.BookSearchRequest;
import com.nhnacademy.search.dto.BookSearchResponse;
import com.nhnacademy.search.dto.BookSortOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiSearchCacheServiceTest {

    @Mock private RedisTemplate<String, BookSearchResponse> redis;
    @Mock private StringRedisTemplate stringRedis;

    @Mock private ValueOperations<String, BookSearchResponse> redisValueOps;
    @Mock private ValueOperations<String, String> stringValueOps;

    private Executor directExecutor;
    private AiSearchCacheService service;

    @BeforeEach
    public void setUp() {
        when(redis.opsForValue()).thenReturn(redisValueOps);

        // async 즉시 실행
        directExecutor = Runnable::run;

        service = new AiSearchCacheService(redis, stringRedis, directExecutor);
    }

    @Test
    public void getOrCompute_cacheMiss_storesFreshWithTtl() {
        BookSearchRequest req = new BookSearchRequest("  HeLLo   World ", null, -2, 0);
        String key = expectedKey(req);

        when(redisValueOps.get(key)).thenReturn(null);

        BookSearchResponse fresh = new BookSearchResponse(List.of(), 123L, 0, 10);
        Supplier<BookSearchResponse> loader = () -> fresh;

        BookSearchResponse out = service.getOrCompute(req, loader);

        assertSame(fresh, out);
        verify(redisValueOps).set(eq(key), same(fresh), eq(Duration.ofMinutes(3)));
    }

    @Test
    public void getOrCompute_cacheHit_returnsCached_noRefreshWhenTtlIsFar() {
        BookSearchRequest req = new BookSearchRequest("q", BookSortOption.RELEVANCE, 0, 10);
        String key = expectedKey(req);

        BookSearchResponse cached = new BookSearchResponse(List.of(), 1L, 0, 10);

        when(redisValueOps.get(key)).thenReturn(cached);
        when(stringRedis.getExpire(key, TimeUnit.SECONDS)).thenReturn(100L);

        BookSearchResponse out = service.getOrCompute(req, () -> fail("loader should not be called"));

        assertSame(cached, out);
        verify(stringRedis, never()).opsForValue();
        verify(stringRedis, never()).delete(anyString());
    }

    @Test
    public void getOrCompute_cacheHit_ttlNull_noRefresh() {
        BookSearchRequest req = new BookSearchRequest("q", null, 0, 10);
        String key = expectedKey(req);

        BookSearchResponse cached = new BookSearchResponse(List.of(), 1L, 0, 10);

        when(redisValueOps.get(key)).thenReturn(cached);
        when(stringRedis.getExpire(key, TimeUnit.SECONDS)).thenReturn(null);

        BookSearchResponse out = service.getOrCompute(req, () -> fail("loader should not be called"));

        assertSame(cached, out);
        verify(stringRedis, never()).opsForValue();
    }

    @Test
    public void getOrCompute_cacheHit_refreshNeeded_lockFails_noRefresh() {
        BookSearchRequest req = new BookSearchRequest("q", null, 0, 10);
        String key = expectedKey(req);
        String lockKey = key + ":refresh_lock";

        BookSearchResponse cached = new BookSearchResponse(List.of(), 1L, 0, 10);

        when(redisValueOps.get(key)).thenReturn(cached);
        when(stringRedis.getExpire(key, TimeUnit.SECONDS)).thenReturn(10L);

        when(stringRedis.opsForValue()).thenReturn(stringValueOps);
        when(stringValueOps.setIfAbsent(eq(lockKey), eq("1"), any(Duration.class))).thenReturn(false);

        BookSearchResponse out = service.getOrCompute(req, () -> fail("loader should not be called"));

        assertSame(cached, out);
        verify(redisValueOps, never()).set(eq(key), any(), eq(Duration.ofMinutes(3)));
        verify(stringRedis, never()).delete(lockKey);
    }

    @Test
    public void getOrCompute_cacheHit_refreshNeeded_lockSuccess_refreshesAndReleasesLock() {
        BookSearchRequest req = new BookSearchRequest("q", null, 0, 10);
        String key = expectedKey(req);
        String lockKey = key + ":refresh_lock";

        BookSearchResponse cached = new BookSearchResponse(List.of(), 1L, 0, 10);
        BookSearchResponse fresh = new BookSearchResponse(List.of(), 2L, 0, 10);

        when(redisValueOps.get(key)).thenReturn(cached);
        when(stringRedis.getExpire(key, TimeUnit.SECONDS)).thenReturn(10L);

        when(stringRedis.opsForValue()).thenReturn(stringValueOps);
        when(stringValueOps.setIfAbsent(eq(lockKey), eq("1"), any(Duration.class))).thenReturn(true);

        BookSearchResponse out = service.getOrCompute(req, () -> fresh);

        assertSame(cached, out);
        verify(redisValueOps).set(eq(key), same(fresh), eq(Duration.ofMinutes(3)));
        verify(stringRedis).delete(lockKey);
    }

    @Test
    public void getOrCompute_cacheHit_refreshNeeded_loaderThrows_stillReleasesLock() {
        BookSearchRequest req = new BookSearchRequest("q", null, 0, 10);
        String key = expectedKey(req);
        String lockKey = key + ":refresh_lock";

        BookSearchResponse cached = new BookSearchResponse(List.of(), 1L, 0, 10);

        when(redisValueOps.get(key)).thenReturn(cached);
        when(stringRedis.getExpire(key, TimeUnit.SECONDS)).thenReturn(10L);

        when(stringRedis.opsForValue()).thenReturn(stringValueOps);
        when(stringValueOps.setIfAbsent(eq(lockKey), eq("1"), any(Duration.class))).thenReturn(true);

        BookSearchResponse out = service.getOrCompute(req, () -> { throw new RuntimeException("boom"); });

        assertSame(cached, out);
        verify(stringRedis).delete(lockKey);
    }

    private String expectedKey(BookSearchRequest req) {
        String q = (req.query() == null) ? "" : req.query().trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String sort = (req.sort() == null) ? "RELEVANCE" : req.sort().name();
        int page = Math.max(req.page(), 0);
        int size = Math.max(req.size(), 1);

        String version = "v1";
        String qHash = Integer.toHexString(q.hashCode());
        return "team1:ai:" + version + ":" + qHash + ":" + sort + ":" + page + ":" + size;
    }
}
