package com.sh.aicommerce.entity;

import com.sh.aicommerce.common.exception.review.ReviewException;
import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import com.sh.aicommerce.enums.review.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_order_item", columnNames = "order_item_id")
        },
        indexes = {
                @Index(
                        name = "idx_review_product_status_created",
                        columnList = "product_id,status,created_at"
                ),
                @Index(
                        name = "idx_review_member_status_created",
                        columnList = "member_id,status,created_at"
                )
        }
)
public class Review {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_CONTENT_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer rating; // 평점

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "weight_kg")
    private Integer weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_evaluation", nullable = false, length = 30)
    private FitEvaluation fitEvaluation;

    @Enumerated(EnumType.STRING)
    @Column(name = "fit_preference", length = 30)
    private FitPreference fitPreference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;

    @Column(name = "edit_used", nullable = false)
    private boolean editUsed;

    @Column(name = "rewrite_used", nullable = false)
    private boolean rewriteUsed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static Review create(
            OrderItem orderItem,
            Integer rating,
            String content,
            Integer heightCm,
            Integer weightKg,
            FitEvaluation fitEvaluation,
            FitPreference fitPreference
    ) {
        validateOrderItem(orderItem);
        validateReviewValues(rating, content, fitEvaluation);

        Review review = new Review();
        review.orderItem = orderItem;
        review.member = orderItem.getOrder().getMember();
        review.product = orderItem.getProductOption().getProduct();
        review.applyReviewValues(
                rating,
                content,
                heightCm,
                weightKg,
                fitEvaluation,
                fitPreference
        );
        review.status = ReviewStatus.ACTIVE;
        review.editUsed = false;
        review.rewriteUsed = false;
        review.createdAt = LocalDateTime.now();
        review.updatedAt = review.createdAt;

        return review;
    }

    public void update(
            Integer rating,
            String content,
            Integer heightCm,
            Integer weightKg,
            FitEvaluation fitEvaluation,
            FitPreference fitPreference
    ) {
        if (status != ReviewStatus.ACTIVE) {
            throw new ReviewException("활성 상태의 리뷰만 수정할 수 있습니다.");
        }
        if (editUsed) {
            throw new ReviewException("리뷰는 한 번만 수정할 수 있습니다.");
        }
        validateReviewValues(rating, content, fitEvaluation);

        applyReviewValues(
                rating,
                content,
                heightCm,
                weightKg,
                fitEvaluation,
                fitPreference
        );
        this.editUsed = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void delete() {
        if (status != ReviewStatus.ACTIVE) {
            throw new ReviewException("활성 상태의 리뷰만 삭제할 수 있습니다.");
        }

        this.status = ReviewStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void rewrite(
            Integer rating,
            String content,
            Integer heightCm,
            Integer weightKg,
            FitEvaluation fitEvaluation,
            FitPreference fitPreference
    ) {
        if (status != ReviewStatus.DELETED) {
            throw new ReviewException("삭제된 리뷰만 재작성할 수 있습니다.");
        }
        if (rewriteUsed) {
            throw new ReviewException("삭제한 리뷰는 한 번만 재작성할 수 있습니다.");
        }
        validateReviewValues(rating, content, fitEvaluation);

        applyReviewValues(
                rating,
                content,
                heightCm,
                weightKg,
                fitEvaluation,
                fitPreference
        );
        this.status = ReviewStatus.ACTIVE;
        this.editUsed = false;
        this.rewriteUsed = true;
        this.updatedAt = LocalDateTime.now();
    }

    public void exclude() {
        if (status == ReviewStatus.EXCLUDED) {
            return;
        }

        this.status = ReviewStatus.EXCLUDED;
        this.updatedAt = LocalDateTime.now();
    }

    private void applyReviewValues(
            Integer rating,
            String content,
            Integer heightCm,
            Integer weightKg,
            FitEvaluation fitEvaluation,
            FitPreference fitPreference
    ) {
        this.rating = rating;
        this.content = content.trim();
        this.heightCm = heightCm;
        this.weightKg = weightKg;
        this.fitEvaluation = fitEvaluation;
        this.fitPreference = fitPreference;
    }

    private static void validateOrderItem(OrderItem orderItem) {
        if (orderItem == null
                || orderItem.getOrder() == null
                || orderItem.getOrder().getMember() == null
                || orderItem.getProductOption() == null
                || orderItem.getProductOption().getProduct() == null) {
            throw new ReviewException("구매 정보가 유효하지 않습니다.");
        }
    }

    private static void validateReviewValues(
            Integer rating,
            String content,
            FitEvaluation fitEvaluation
    ) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new ReviewException("별점은 1점부터 5점까지 입력할 수 있습니다.");
        }
        if (content == null || content.isBlank()) {
            throw new ReviewException("리뷰 내용을 입력해 주세요.");
        }
        if (content.trim().length() > MAX_CONTENT_LENGTH) {
            throw new ReviewException("리뷰 내용은 2000자를 초과할 수 없습니다.");
        }
        if (fitEvaluation == null) {
            throw new ReviewException("착용감을 선택해 주세요.");
        }
    }
}
