package com.sh.aicommerce.outboxEvent.redis;

import com.sh.aicommerce.entity.Review;
import com.sh.aicommerce.enums.review.ReviewStatus;
import com.sh.aicommerce.enums.review.dto.ReviewDocumentDto;
import com.sh.aicommerce.enums.review.es.ReviewDocument;
import com.sh.aicommerce.enums.review.redis.repository.ReviewDocumentRepository;
import com.sh.aicommerce.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEmbeddingProcessor {

    private final ReviewDocumentRepository reviewDocumentRepository;
    private final ReviewService reviewService;
    private final EmbeddingModel embeddingModel;


    public void processEmbedding(Long reviewId) {
        log.info("[리뷰 임베딩 요청] : 요청 ReviewId : {}", reviewId);

        // 외부 API 호출때매 Trasnsaction 안에서 실행하지 않도록 분리
        Optional<ReviewDocumentDto> optionalReviewDto = reviewService.getReviewIndexSource(reviewId);

        if (optionalReviewDto.isEmpty()) {
            // DB에 해당 Review에 대한 데이터가 존재하지 않으면 ES에도 해당 ReviewId로 존재하는 document 삭제
            reviewDocumentRepository.deleteById(reviewId);
            return;
        }

        ReviewDocumentDto reviewDocumentDto = optionalReviewDto.get();

        if (!reviewDocumentDto.getStatus().equals(ReviewStatus.ACTIVE)) {
            reviewDocumentRepository.deleteById(reviewId);
            return;
        }

        float[] contentEmbedding = embeddingModel.embed(reviewDocumentDto.getContent());
        ReviewDocument document = ReviewDocument.createDocument(reviewDocumentDto, contentEmbedding);

        reviewDocumentRepository.save(document);
    }

}
