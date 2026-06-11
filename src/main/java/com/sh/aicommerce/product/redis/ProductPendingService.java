package com.sh.aicommerce.product.redis;

import com.sh.aicommerce.entity.ProductIndexFailLog;
import com.sh.aicommerce.product.repository.ProductIndexFailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductPendingService {

    private final ProductIndexFailLogRepository productIndexFailLogRepository;
    private final ProductIndexConsumer productIndexConsumer;
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_NAME = "product:index:stream";
    private static final String GROUP_NAME = "product-group";
    private static final String CONSUMER_NAME = "product-index-consumer-1";

    private static final int MAX_DELIVERY_COUNT = 3;
    private static final Duration MIN_IDLE_TIME = Duration.ofSeconds(30);

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void processPendingProduct() {

        PendingMessages pendingMessages = redisTemplate.opsForStream()
                .pending(STREAM_NAME, GROUP_NAME, Range.unbounded(), 100L);

        if(pendingMessages == null || pendingMessages.isEmpty()) return;

        for (PendingMessage message : pendingMessages) {

            // PendingMessage에서 재처리하고 있는 데이터를 중복처리하는 것을 방지하기 위해서 IDLE_TIME 체크
            if(message.getElapsedTimeSinceLastDelivery().compareTo(MIN_IDLE_TIME) < 0) continue;

            // Pending 메시지 처리 재시도 횟수가 3회 초과되는 경우 DLQ로 이동해서 직접 등록
            if (message.getTotalDeliveryCount() >= MAX_DELIVERY_COUNT) {
                moveToDeadLetterStream(message);
                continue;
            }

            // Pending 메시지 처리 재시도 횟수가 3회 이하인 경우 재시도 -> XCLAIM 으로
            List<MapRecord<String, String, String>> claimMessages = (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                    .claim(STREAM_NAME, GROUP_NAME, CONSUMER_NAME, MIN_IDLE_TIME, message.getId());

            for (MapRecord<String, String, String> claimMessage : claimMessages) {
                log.info("[상품 Pending Message 처리] : messageId : {}, 상품 ID :{}, Action : {}", claimMessage.getId(), claimMessage.getValue().get("productId"), claimMessage.getValue().get("action"));
                productIndexConsumer.handleProduct(claimMessage);
            }


        }
    }

    // 재시도 횟수 초과한 Pending Message들 DLQ에 저장(실패 로그 저장)
    private void moveToDeadLetterStream(PendingMessage pendingMessage) {
        String messageId = pendingMessage.getId().getValue();

        List<MapRecord<String,String, String>> records = (List<MapRecord<String,String, String>>) (List<?>)redisTemplate.opsForStream().range(
                STREAM_NAME,
                Range.closed(pendingMessage.getIdAsString(),pendingMessage.getIdAsString())
        );

        if (records == null || records.isEmpty()) {
            log.error("[Pending 원본 메시지 조회 실패] messageId :{}", messageId);
            return;
        }

        MapRecord<String, String, String> message = records.get(0);

        Long productId = Long.parseLong(message.getValue().get("productId"));
        String action = message.getValue().get("action");

        log.info("[ES 상품 색인 과정 실패] : PendingMessageId : {} , 상품 ID : {}, Action : {}, 실패사유 : {}", messageId, productId, action, "재시도 횟수 초과");

        productIndexFailLogRepository.save(new ProductIndexFailLog(productId, messageId, "재시도 횟수 초과 ", action));

        redisTemplate.opsForStream()
                .acknowledge(STREAM_NAME, GROUP_NAME, messageId);
    }
}