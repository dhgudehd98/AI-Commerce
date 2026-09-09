package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.common.exception.outbox.review.ReviewEmbeddingException;
import com.sh.aicommerce.entity.Review;
import com.sh.aicommerce.enums.review.ReviewStatus;
import com.sh.aicommerce.enums.review.dto.ReviewDocumentDto;
import com.sh.aicommerce.enums.review.es.ReviewDocument;
import com.sh.aicommerce.enums.review.redis.repository.ReviewDocumentRepository;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import com.sh.aicommerce.review.embedding.ReviewEmbeddingContentSanitizer;
import com.sh.aicommerce.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEmbeddingProcessor {

    private final ReviewDocumentRepository reviewDocumentRepository;
    private final ReviewService reviewService;
    private final EmbeddingModel embeddingModel;
    private final ReviewEmbeddingContentSanitizer contentSanitizer;


    public void processEmbedding(Long reviewId) {

        log.info("[리뷰 임베딩 요청] : 요청 ReviewId : {}", reviewId);

        // 외부 API 호출때매 Trasnsaction 안에서 실행하지 않도록 분리
        ReviewDocumentDto initSourceReview = getReviewIndexSource(reviewId);


        if (initSourceReview == null) {
            deleteDocument(reviewId);
            return;
        }

        // 활성화된 리뷰가 아니면 ES에서도 제거
        if (initSourceReview.getStatus() != ReviewStatus.ACTIVE) {
            deleteDocument(reviewId);
            return;
        }

        Optional<ReviewDocument> existingDocument = reviewDocumentRepository.findById(initSourceReview.getReviewId());

        // 동일한 Review를 중복으로 Embedding 하는 과정을 방지하기 위해서 해당 로직 추가
        if (existingDocument.isPresent() &&
                Objects.equals(
                        existingDocument.get().getSourceUpdatedAt(),
                        initSourceReview.getSourceUpdatedAt()
                )) {
            log.info(
                    "[Review Embedding 생략] 최신 ES 문서가 이미 존재 reviewId: {}",
                    reviewId
            );

            return;
        }

        // 키와 몸무게 표현을 제거한 검색용 리뷰 내용만 임베딩
        String sanitizedContent = contentSanitizer.sanitize(initSourceReview.getContent());
        float[] contentEmbedding = createReviewEmbedding(
                initSourceReview.getReviewId(),
                sanitizedContent
        );

        // 임베딩을 한 후에, Review에 대한 값 다시 조회하기 해당 Review에 대한 값이 변경되었으면 ES에 저장을 하면 안되기 떄문에
        /**
         * 예시
         * - 임베딩을 하기전 : init
         *      Review에 대한 값 : "아주 훌륭한 옷입니다 L사이즈 추천합니다."
         * - 임베딩을 하는 도중 Review에 대한 값 변경 : lastest
         *      Review에 대한 값 : "XL 사이즈 추천합니다."
         *   -> 최근 이벤트에 대한 값이 저장이 되어야하 기 때문에 해당 리뷰의 값이 다르면 기존 이벤트는 ES에 저장하지 않고 종료
         */
        ReviewDocumentDto latestSource = getReviewIndexSource(reviewId);

        if (latestSource == null) {
            deleteDocument(reviewId);
            return;
        }

        if(latestSource.getStatus() != ReviewStatus.ACTIVE){
            deleteDocument(reviewId);
            return;
        }

        // updatedAt에 대한 값이 다르다면 최근 이벤트가 저장이 되도록 하여 임베딩 과정 종료
        if (!Objects.equals(
                initSourceReview.getSourceUpdatedAt(),
                latestSource.getSourceUpdatedAt()
        )) {
            log.info(
                    "[Review Embedding 저장 중단] 처리 중 원본 변경 reviewId: {}, before: {}, after: {}",
            reviewId,
            initSourceReview.getSourceUpdatedAt(),
            latestSource.getSourceUpdatedAt()
            );
            return;
        }

        // Review ES에 저장
        ReviewDocument document = ReviewDocument.createDocument(
                latestSource,
                sanitizedContent,
                contentEmbedding
        );
        saveDocument(document);
    }

    private void saveDocument(ReviewDocument document) {
        try {
            log.info("[Review ES 적재 요청] reviewId : {}", document.getReviewId());
            reviewDocumentRepository.save(document);
            log.info("[Review ES 적재 완료] reviewId : {}", document.getReviewId());

        } catch (Exception e) {
            log.info("[Review ES 적재 실패] reviewId : {}", document.getReviewId());
            throw new ReviewEmbeddingException(
                    ReviewEmbeddingFailureCode.ELASTICSEARCH_SAVE_FAILED,
                    e
            );
        }
    }

    private float[] createReviewEmbedding(Long reviewId, String content) {
        try {
            log.info("[Review Content Embedding 요청] 요청 ReviewId : {}", reviewId);
            return embeddingModel.embed(content);
        } catch (Exception e) {
            log.info("[Review Content Embedding 실패] 실패 ReviewId : {}", reviewId);
            throw new ReviewEmbeddingException(
                    ReviewEmbeddingFailureCode.EMBEDDING_API_FAILED,
                    e
            );
        }
    }

    private ReviewDocumentDto getReviewIndexSource(Long reviewId) {
        try {
            log.info("[Embedding Review 요청] Embedding 할 reviewId : {}", reviewId);
            return reviewService.getReviewIndexSource(reviewId).orElse(null);
        } catch (Exception e) {
            log.info("[Embedding Review 반환 실패] : reviewId : {}", reviewId);
            throw new ReviewEmbeddingException(
                    ReviewEmbeddingFailureCode.REVIEW_SOURCE_READ_FAILED,
                    e
            );
        }
    }

    private void deleteDocument(Long reviewId) {
        try {
            log.info("[리뷰 ES 문서 삭제 요청] reviewId : {}", reviewId);
            reviewDocumentRepository.deleteById(reviewId);
            log.info("[리뷰 ES 문서 삭제 완료] reviewId : {}", reviewId);
        } catch (Exception e) {
            log.info("[리뷰 ES 문서 삭제 실패] reviewId : {}", reviewId);
            throw new ReviewEmbeddingException(
                    ReviewEmbeddingFailureCode.ELASTICSEARCH_DELETE_FAILED,
                    e
            );
        }
    }

}
