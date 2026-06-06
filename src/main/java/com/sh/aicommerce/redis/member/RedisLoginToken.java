package com.sh.aicommerce.redis.member;


import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisLoginToken {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String refreshTokenKey = "refreshToken";

    public void setRefreshToken(String refreshToken, Long memberId) {
        stringRedisTemplate.opsForValue().set(
                refreshTokenKey + ":" + memberId,
                refreshToken,
                7, TimeUnit.DAYS
        );
    }

    public Optional<String> getRefreshToken(Long memberId) {
        return Optional.ofNullable(stringRedisTemplate.opsForValue().get(refreshTokenKey + ":" +  memberId));
    }

    public void deleteRefreshToken(Long memberId) {
        stringRedisTemplate.delete(refreshTokenKey + ":" + memberId);
    }
}