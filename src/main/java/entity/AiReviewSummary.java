package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_review_summary_product", columnNames = "product_id")
        }
)
@Getter
@NoArgsConstructor
public class AiReviewSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_review_summary_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 2000)
    private String positiveSummary; // 긍정적인 상품 요약

    @Column(length = 2000)
    private String negativeSummary; // 부정적인 상품 요약

    @Column(length = 2000)
    private String sizeSummary; // 크기에 대한 상품 요약

    @Column(length = 2000)
    private String qualitySummary; // 퀄리티에 대한 상품 요약

    @Column(length = 2000)
    private String deliverySummary; // 배송에 대한 상품 요약

    @Column(nullable = false)
    private Integer reviewCount; // 리뷰 수

    @Column(nullable = false)
    private LocalDateTime summarizedAt;
}
