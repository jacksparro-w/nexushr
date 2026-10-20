package com.nexushr.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiRateLimitFilter extends OncePerRequestFilter {
  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_BUCKETS = 10_000;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final ObjectMapper json;

  public ApiRateLimitFilter(ObjectMapper json) { this.json = json; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    int limit = limitFor(request.getMethod(), path);
    if (limit == 0) { chain.doFilter(request, response); return; }
    long now = System.currentTimeMillis();
    String key = request.getRemoteAddr() + "|" + path;
    if (buckets.size() > MAX_BUCKETS) buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart > WINDOW_MILLIS);
    Bucket bucket = buckets.compute(key, (ignored, previous) -> previous == null || now - previous.windowStart >= WINDOW_MILLIS
        ? new Bucket(now, new AtomicInteger(0)) : new Bucket(previous.windowStart, previous.count));
    if (bucket.count.incrementAndGet() > limit) {
      long retryAfter = Math.max(1, (bucket.windowStart + WINDOW_MILLIS - now + 999) / 1000);
      response.setStatus(429);
      response.setHeader("Retry-After", Long.toString(retryAfter));
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      json.writeValue(response.getOutputStream(), new RateLimitError(Instant.now(), 429,
          "Too Many Requests", "Too many requests. Retry after the indicated delay.", path, java.util.Map.of()));
      return;
    }
    response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
    chain.doFilter(request, response);
  }

  private int limitFor(String method, String path) {
    if (path.equals("/api/auth/login")) return 10;
    if (path.equals("/api/auth/refresh")) return 30;
    if (path.startsWith("/api/intelligence/")) return 20;
    if (method.equals("POST") && path.equals("/api/auth/accounts")) return 20;
    return 0;
  }

  private record Bucket(long windowStart, AtomicInteger count) {}
  private record RateLimitError(Instant timestamp, int status, String error, String message,
      String path, java.util.Map<String, String> fieldErrors) {}
}
