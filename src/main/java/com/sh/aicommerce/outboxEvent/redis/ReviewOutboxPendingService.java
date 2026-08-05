package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.entity.ReviewOutboxFailLog;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEventType;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewOutboxPendingService {
    private static final String FAILURE_KEY_PREFIX = "review:embedding:failure:";
    private static final String STREAM_NAME = "review:embedding:stream";
    private static final String GROUP_NAME = "reviewEvent-group";

    @Value("${redis.stream.review.consumer-name}")
    private String consumerName;
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
                    .claim(STREAM_NAME, GROUP_NAME, consumerName, MIN_IDLE_TIME, message.getId());

            for (MapRecord<String, String, String> claimMessage : claimMessages) {
                log.info("[Review Pending Data Index 재처리 요청] Review MessageId : {}, reviewId : {}", claimMessage.getId().getValue(), claimMessage.getValue().get("reviewId"));
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

            ReviewEmbeddingFailureCode failureCode = ReviewEmbeddingFailureCode.STREAM_MESSAGE_NOTFOUND;
            // 원본 데이터가 없는 경우 색인 실패 로그 저장
            ReviewOutboxFailLog failLog = ReviewOutboxFailLog.createMissingMessage(messageId, failureCode);
            boolean saved = saveReviewOutboxIndexFailLog(failLog);
            // 해당 문제를 방지하기 위해서 ACK 설정
            if(saved && ackPendingMessage(messageId)) deleteFailureCode(messageId);

            return;
        }

        // 원본 데이터가 존재하고, 재시도 횟수가 3회 초과인 상태면 강제 ACK 처리
        MapRecord<String, String, String> message = records.get(0);

        String eventId = message.getValue().get("eventId");
        Long reviewId = Long.parseLong(message.getValue().get("reviewId"));
        ReviewEventType type = ReviewEventType.valueOf(message.getValue().get("type"));
        ReviewEmbeddingFailureCode failCode = getFailureCodeByMessageId(messageId);


        log.info("[Review 색인 과정 실패] 실패 사유 : 재시도 횟수초과 reivewId : {}", reviewId);

        // 색인 실패(재시도 횟수 초과)시 FailLog DB에 저장
        ReviewOutboxFailLog reviewOutboxFailLog = ReviewOutboxFailLog.create(eventId, reviewId, messageId, type, failCode);
        boolean saved = saveReviewOutboxIndexFailLog(reviewOutboxFailLog);

        if(saved && ackPendingMessage(messageId)) deleteFailureCode(messageId);
    }

    @Transactional
    public boolean saveReviewOutboxIndexFailLog(ReviewOutboxFailLog reviewOutboxFailLog) {
        try{
            log.info("[Pending Message 재시도 횟수 초과] FailLog 저장 messageId : {}", reviewOutboxFailLog.getMessageId());
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

    private boolean ackPendingMessage(String messageId) {
        try {
            log.info("[Pending Message 재시도 횟수 초과 강제 ACK] MessageId : {}", messageId);
            stringRedisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            return true;
        } catch (Exception e) {
            log.error("[Pending 메시지 ACK 실패] messageId : {}", messageId);
            return false;
        }
    }

    private ReviewEmbeddingFailureCode getFailureCodeByMessageId(String messageId) {
        String failureCodeValue =
                stringRedisTemplate.opsForValue().get(
                        FAILURE_KEY_PREFIX + messageId
                );

        if(failureCodeValue == null) return ReviewEmbeddingFailureCode.UNKNOWN_ERROR;

        try{
            return ReviewEmbeddingFailureCode.valueOf(failureCodeValue);
        }catch (IllegalArgumentException exception) {
            return ReviewEmbeddingFailureCode.UNKNOWN_ERROR;
        }
    }

    private void deleteFailureCode(String messageId) {
        stringRedisTemplate.delete(
                FAILURE_KEY_PREFIX + messageId
        );
    }
}
