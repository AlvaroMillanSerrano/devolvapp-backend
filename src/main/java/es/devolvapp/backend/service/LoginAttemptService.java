package es.devolvapp.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import es.devolvapp.backend.exception.ApiException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;

    public void check(String username) {
        String key = key(username);
        String count = redis.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Demasiados intentos. Inténtalo más tarde");
        }
    }

    public void failed(String username) {
        String key = key(username);
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) redis.expire(key, WINDOW);
    }

    public void succeeded(String username) {
        redis.delete(key(username));
    }

    private String key(String username) {
        return "login-attempts:" + username.trim().toLowerCase();
    }
}
