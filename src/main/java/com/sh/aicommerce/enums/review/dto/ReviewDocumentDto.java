package com.sh.aicommerce.enums.review.dto;


import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.Review;
import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import com.sh.aicommerce.enums.review.ReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ReviewDocumentDto {
    Long reviewId;
    Long productVariantId;
    Long productOptionId;
    String purchasedSize;
    String content;
    Integer heightCm;
    Integer weightKg;
    FitEvaluation fitEvaluation;
    FitPreference fitPreference;
    ReviewStatus status;
    LocalDateTime createdAt;
    LocalDateTime sourceUpdatedAt;


    public static ReviewDocumentDto create(Review review){

        ReviewDocumentDto dto = new ReviewDocumentDto();
        ProductOption option = review.getOrderItem().getProductOption();

        dto.reviewId = review.getId();
        dto.productVariantId = option.getProductVariant().getId();
        dto.productOptionId = option.getId();
        dto.purchasedSize = option.getSize();
        dto.content = review.getContent();
        dto.heightCm = review.getHeightCm();
        dto.weightKg = review.getWeightKg();
        dto.fitEvaluation = review.getFitEvaluation();
        dto.fitPreference = review.getFitPreference();
        dto.status = review.getStatus();
        dto.createdAt = review.getCreatedAt();
        dto.sourceUpdatedAt = review.getUpdatedAt();

        return dto;
    }
}
