package com.sh.aicommerce.enums.review.es;

import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import com.sh.aicommerce.enums.review.dto.ReviewDocumentDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "review-search", createIndex = false)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Slf4j
public class ReviewDocument {
    @Id
    private Long reviewId;

    @Field(type = FieldType.Keyword)
    private Long productVariantId;

    @Field(type = FieldType.Keyword)
    private Long productOptionId;

    @Field(type = FieldType.Keyword)
    private String purchasedSize;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Dense_Vector)
    private float[] contentEmbedding;

    @Field(type = FieldType.Integer)
    private Integer heightCm;

    @Field(type = FieldType.Integer)
    private Integer weightKg;

    @Field(type = FieldType.Keyword)
    private FitEvaluation fitEvaluation;

    @Field(type = FieldType.Keyword)
    private FitPreference fitPreference;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date)
    private LocalDateTime sourceUpdatedAt;

    public static ReviewDocument createDocument(
            ReviewDocumentDto dto,
            String sanitizedContent,
            float[] reviewEmbedding
    ) {
        ReviewDocument document = new ReviewDocument();
        document.reviewId = dto.getReviewId();
        document.productVariantId = dto.getProductVariantId();
        document.productOptionId = dto.getProductOptionId();
        document.purchasedSize = dto.getPurchasedSize();
        document.content = sanitizedContent;
        document.contentEmbedding = reviewEmbedding;
        document.heightCm = dto.getHeightCm();
        document.weightKg = dto.getWeightKg();
        document.fitEvaluation = dto.getFitEvaluation();
        document.fitPreference = dto.getFitPreference();
        document.createdAt = dto.getCreatedAt();
        document.sourceUpdatedAt = dto.getSourceUpdatedAt();

        return document;
    }
}
