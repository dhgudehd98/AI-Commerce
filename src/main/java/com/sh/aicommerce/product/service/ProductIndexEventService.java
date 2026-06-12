package com.sh.aicommerce.product.service;

import com.sh.aicommerce.product.redis.ProductIndexEventRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductIndexEventService {

    //ES
    private final RedisTemplate<String, String> redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publicProductIndexEvent(ProductIndexEventRecord record) {
        // 상품 등록시 ES 색인 과정 진행
        redisTemplate.opsForStream()
                .add("product:index:stream",
                        Map.of("productId", String.valueOf(record.productId()),
                                "action",record.action())
                );

        log.info("[상품 색인 이벤트 발행] 상품 ID : {}, action : {}", record.productId(), record.action());
    }
}