package com.sh.aicommerce.product.redis;

import com.sh.aicommerce.product.es.document.ProductDocument;
import com.sh.aicommerce.product.es.repository.ProductDocumentRepository;
import com.sh.aicommerce.product.es.service.ProductDocumentService;
import com.sh.aicommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductIndexConsumer implements ApplicationRunner {


    //Redis 관련
    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_NAME = "product:index:stream";
    private static final String GROUP_NAME = "product-group";

    @Value("${redis.stream.consumer.group}")
    private String CONSUMER_NAME;

    //DB 관련
    private final ProductRepository productRepository;

    // ES 관련
    private final ProductDocumentService productDocumentService;
    private final EmbeddingModel embeddingModel;
    private final ProductDocumentRepository productDocumentRepository;
    @Override
    public void run(ApplicationArguments args) throws Exception {
        initStream(); // Stream / Consumer / Consumer-group 생성
        container.receive(
                Consumer.from(GROUP_NAME, CONSUMER_NAME),
                StreamOffset.create(STREAM_NAME, ReadOffset.lastConsumed()),
                this::handleProduct
        );
    }

    // Stream 초기화 -> Stream 생성 및 Consumer / Consumer-Group 생성
    private void initStream() {
        try {
            log.info("[Product Index Stream & Consumer group Create");
            redisTemplate.opsForStream()
                    .createGroup(STREAM_NAME, ReadOffset.from("0"), GROUP_NAME);
        } catch (Exception e) {
            if (isBusyGroupException(e)) {
                log.info("[Consumer Group 이미 존재]");
                return;
            }

            log.error("[Consumer Group 생성 실패]", e);
            throw e;
        }
    }

    private boolean isBusyGroupException(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            String message = current.getMessage();

            if (message != null && message.contains("BUSYGROUP")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * Redis에 저장되어 있는 ES 색인 연동
     * - action : create / update/ delete
     * -
     * @param message
     */
    public void handleProduct(MapRecord<String, String, String> message) {
        String action = message.getValue().get("action");
        Long productId = Long.parseLong(message.getValue().get("productId"));
        String messageId = message.getId().getValue();

        switch (action) {
            case "CREATE" -> createProductVariantDocument(productId, messageId);
            case "INBOUND" -> inboundProductVariantDocument(productId, messageId);
            case "OUTBOUND" -> outboundProductVariantDocument(productId, messageId);
            case "DELETE" -> deleteProductIndex(productId, messageId);
        }

    }

    // 상품 색인 정보 생성
    private void createProductVariantDocument(Long productId, String messageId) {
        log.info("[ES 색인 상품 생성 시작] : ProductId : {}", productId);
        try {
            List<ProductDocument> documents = productDocumentService.insertProductVariantDocument(productId);
            productDocumentRepository.saveAll(documents);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

            log.info("[ES 상품 생성 완료] productId : {}", productId);
        } catch (Exception e) {
            log.error("[ES 상품 생성 실패] : {}", e.getMessage());
        }
    }

    // 상품 입고
    private void inboundProductVariantDocument(Long productId, String messageId) {
        log.info("[ES 색인 상품 입고] : ProductId : {}", productId);
        try {
            List<ProductDocument> documents = productDocumentService.inboundProductVariantDocument(productId);
            productDocumentRepository.saveAll(documents);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);
            log.info("[ES 색인 상품 입고 완료] : ProductId : {}", productId);
        } catch (Exception e) {
            log.error("[ES 색인 상품 정보 실패] : productId : {}", productId);
        }
    }

    //상품 출고
    private void outboundProductVariantDocument(Long productId, String messageId) {

    }



    // 상품 색인 정보 삭제
    private void deleteProductIndex(Long productId, String messageId) {
        log.info("[ES 색인 상품 삭제 시작] : ProductId : {}", productId);

        try {

            // 색인 데이터 삭제 -> 색인 삭제에 대한 부분은 DB에서는 이미 삭제가 되어 있기 때문에 DB 조회하지 않고 그냥
            productDocumentService.deleteProductDocument(productId);

            log.info("[ES 상품 삭제 완료] 상품번호 : {}", productId);

            redisTemplate.opsForStream()
                    .acknowledge(STREAM_NAME, GROUP_NAME, messageId);

        } catch (Exception e) {
            log.info("[ES 상품 삭제 실패] productId : {}", productId);
        }
    }
}
