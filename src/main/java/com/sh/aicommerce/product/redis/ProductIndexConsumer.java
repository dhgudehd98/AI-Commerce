package com.sh.aicommerce.product.redis;

import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.product.es.ProductDocument;
import com.sh.aicommerce.product.repository.ProductDocumentRepository;
import com.sh.aicommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.sql.results.graph.embeddable.EmbeddableInitializer;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.core.io.support.SpringFactoriesLoader.FailureHandler.handleMessage;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductIndexConsumer implements ApplicationRunner {


    //Redis 관련
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_NAME = "product:index:stream";
    private static final String GROUP_NAME = "product-group";
    private static final String CONSUMER_NAME = "product-index-consumer-1";

    //DB 관련
    private final ProductRepository productRepository;

    // ES 관련
    private final ProductDocumentService productDocumentService;
    private final EmbeddingModel embeddingModel;
    private final ProductDocumentRepository productDocumentRepository;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        initStream(); // Stream / Consumer / Consumer-group 생성
        processPendingProduct();

        container.receive(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed()),
                this::handleProduct
        );
    }

    //Pending List 안에 있는 Product 처리
    private void processPendingProduct() {
        /**
         * PendingMessages : Pending 되어 있는 데이터의 메타정보만 가져옴
         * - 메타정보 : messageId , consumer-name, pending 시간 , 재시도 횟수
         */
        PendingMessages pendingMessages = redisTemplate.opsForStream()
                .pending(STREAM_NAME, Consumer.from(GROUP_NAME, CONSUMER_NAME), Range.unbounded(), 100L);

        if(pendingMessages == null || pendingMessages.isEmpty()) return; // Pending 되어 있는 상품이 없으면 종료

        for (PendingMessage message : pendingMessages) {
            List<MapRecord<String, String, String>> range = (List<MapRecord<String, String, String>>) (List<?>) redisTemplate.opsForStream()
                    .range(STREAM_NAME, Range.closed(
                            message.getId().getValue(),
                            message.getId().getValue()
                    ));

            if(range == null || range.isEmpty()) continue;

            // Pending Product 재시도 횟수가 3회 넘으면 강제로 ack 날리고 -> DB ProductIndexFailLog에 해당 상품 수동처리 하거나 / 재등록 설정
            if (message.getTotalDeliveryCount() > 3) {
                log.error("[ES 색인 과정 재시도 횟수 초과] 해당 productId : {} 재시도 횟수 : {} ", message.getId(), message.getTotalDeliveryCount());

                Long productId = Long.parseLong(range.get(0).getValue().get("productId"));
                String originMessageId = message.getId().getValue(); // Redis에 저장되어 있는 messageId에 대한 값
                String action = range.get(0).getValue().get("action"); // 상품 등록 / 수정 / 삭제 식별

//                productIndexFailLogRepository.save(new ProductIndexFailLog(productId, originMessageId, action, "재시도 횟수 초과"));
                redisTemplate.opsForStream().acknowledge(STREAM_NAME, GROUP_NAME, message.getId());
                continue;
            }

            handleProduct(range.get(0));
        }
    }

    // Stream 초기화 -> Stream 생성 및 Consumer / Consumer-Group 생성
    private void initStream() {
        try {
            log.info("[Product Index Stream & Consumer group Create");
            redisTemplate.opsForStream()
                    .createGroup(STREAM_NAME, ReadOffset.from("0"), GROUP_NAME);
        } catch (Exception e) {
            log.info("[Consumer Group 이미 존재]");
        }
    }

    /**
     * Redis에 저장되어 있는 ES 색인 연동
     * - action : create / update/ delete
     * -
     * @param message
     */
    @Transactional(readOnly = true)
    public void handleProduct(MapRecord<String, String, String> message) {
        String action = message.getValue().get("action");
        Long productId = Long.parseLong(message.getValue().get("productId"));
        String messageId = message.getId().getValue();

        switch (action) {
            case "CREATE" -> createProductIndex(productId, messageId);
            case "UPDATE" -> updateProductIndex(productId, messageId);
            case "DELETE" -> deleteProductIndex(productId, messageId);
        }

    }

    // 상품 색인 정보 생성
    private void createProductIndex(Long productId, String messageId) {
        log.info("[ES 색인 상품 생성 시작] : ProductId : {}", productId);
        try {
            ProductDocument document = productDocumentService.upSertDocument(productId);
            productDocumentRepository.save(document);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            log.info("[ES 상품 생성 완료] productId : {}", productId);
        } catch (Exception e) {
            log.error("[ES 상품 생성 실패] : {}", e.getMessage());
        }
    }

    // 상품 색인 정보 업데이트
    private void updateProductIndex(Long productId, String messageId) {
        log.info("[ES 색인 상품 수정 시작] : ProductId : {}", productId);

        try {
            ProductDocument document = productDocumentService.upSertDocument(productId);
            productDocumentRepository.save(document); // ES에서 save 자체는 upsert로 이루어지기 때문에 save로 사용

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            log.info("[ES 상품 정보 수정 완료] productId : {}", productId);
        } catch (Exception e) {
            log.error("[ES 상품 수정 실패] : {}", e.getMessage());
        }
    }

    // 상품 색인 정보 삭제
    private void deleteProductIndex(Long productId, String messageId) {
        log.info("[ES 색인 상품 삭제 시작] : ProductId : {}", productId);

        try {
            ProductDocument document = productDocumentService.deleteDocument(productId);
            productDocumentRepository.delete(document);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

        } catch (Exception e) {
            log.info("[ES 상품 삭제 완료] productId : {}", productId);
        }
    }
}