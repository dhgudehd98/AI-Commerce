package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.entity.ProductIndexFailLog;
import com.sh.aicommerce.entity.ReviewOutboxFailLog;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutboxFailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxPendingService {
    private static final String STREAM_NAME = "review:embedding:stream";
    private static final String GROUP_NAME = "reviewEvent-group";

    @Value("${redis.stream.review.consumer.group}")
    private String CONSUMER_NAME;
    private static final Duration MIN_IDLE_TIME = Duration.ofSeconds(30);

    private final ReviewOutboxFailLogRepository failLogRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ReviewOutBoxEventRepository eventRepository;
    private final ReviewOutboxIndexConsumer reviewOutboxIndexConsumer;



//    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void processPendingReviewOutbox() {
        PendingMessages pendingMessages = stringRedisTemplate
                .opsForStream()
                .pending(STREAM_NAME, GROUP_NAME, Range.unbounded(), 100L);

        if(pendingMessages == null || pendingMessages.isEmpty()) return;

        for (PendingMessage message : pendingMessages) {
            if(message.getElapsedTimeSinceLastDelivery().compareTo(MIN_IDLE_TIME) < 0) continue;

            if (message.getTotalDeliveryCount() >= 3) {
                moveToDeadLetterStream(message);
                continue;
            }

            // Pending 메시지 처리 재시도 횟수가 3회 이하인 경우 재시도 -> XCLAIM 으로
            List<MapRecord<String, String, String>> claimMessages = (List<MapRecord<String, String, String>>) (List<?>) stringRedisTemplate.opsForStream()
                    .claim(STREAM_NAME, GROUP_NAME, CONSUMER_NAME, MIN_IDLE_TIME, message.getId());

            for (MapRecord<String, String, String> claimMessage : claimMessages) {
                log.info("[상품 Pending Message 처리] : messageId : {}, 상품 ID :{}, Action : {}", claimMessage.getId(), claimMessage.getValue().get("productId"), claimMessage.getValue().get("action"));
                reviewOutboxIndexConsumer.handleReview(claimMessage);
            }
        }
    }

    private void moveToDeadLetterStream(PendingMessage pendingMessage) {
        String messageId = pendingMessage.getId().getValue();

        // 원본 메세지 조회
        List<MapRecord<String,String, String>> records = (List<MapRecord<String,String, String>>) (List<?>)stringRedisTemplate.opsForStream().range(
                STREAM_NAME,
                Range.closed(pendingMessage.getIdAsString(),pendingMessage.getIdAsString())
        );

        //PendignList에는 해당 데이터값이 존재하지만 원본 데이터가 존재하지 않는 경우 , 무한으로 PendingList 조회
        if (records == null || records.isEmpty()) {
            log.error("[Pending 원본 메시지 조회 실패] messageId :{}", messageId);

            // 색인 실패 로그 저장
            boolean isAck = saveReviewOutboxIndexFailLog(new ReviewOutboxFailLog(messageId, "Pending 메세지 원본 데이터 조회 실패"));
            // 해당 문제를 방지하기 위해서 ACK 설정
            if(isAck) ackPendingMessage(messageId);
        }
    }

    private boolean saveReviewOutboxIndexFailLog(ReviewOutboxFailLog reviewOutboxFailLog) {
        try{
            failLogRepository.save(reviewOutboxFailLog);
            return true;
        } catch (DataIntegrityViolationException dataException) {
            log.error("[동일한 색인 아이디 존재] messageId :{}", reviewOutboxFailLog.getMessageId());
            return true;
        } catch (Exception e) {
            log.error("[색인 실패 로그 저장 에러] messageId : {} , message : {}", reviewOutboxFailLog.getMessageId(), e.getMessage());
            return false;
        }
    }

    private void ackPendingMessage(String messageId) {
        try {
            stringRedisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);
        } catch (Exception e) {
            log.error("[Pending 메시지 ACK 실패] messageId : {}", messageId);
        }
    }
}
