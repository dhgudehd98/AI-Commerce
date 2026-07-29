package com.sh.aicommerce.review;

import com.sh.aicommerce.common.exception.review.ReviewException;
import com.sh.aicommerce.entity.Member;
import com.sh.aicommerce.entity.OrderItem;
import com.sh.aicommerce.entity.Orders;
import com.sh.aicommerce.entity.Product;
import com.sh.aicommerce.entity.ProductOption;
import com.sh.aicommerce.entity.Review;
import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import com.sh.aicommerce.enums.review.ReviewStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewTest {

    private OrderItem orderItem;

    @BeforeEach
    void setUp() {
        Member member = mock(Member.class);
        Orders order = mock(Orders.class);
        Product product = mock(Product.class);
        ProductOption productOption = mock(ProductOption.class);
        orderItem = mock(OrderItem.class);

        when(order.getMember()).thenReturn(member);
        when(productOption.getProduct()).thenReturn(product);
        when(orderItem.getOrder()).thenReturn(order);
        when(orderItem.getProductOption()).thenReturn(productOption);
    }

    @Test
    void 리뷰를_작성하면_초기_상태가_설정된다() {
        Review review = createReview();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.ACTIVE);
        assertThat(review.isEditUsed()).isFalse();
        assertThat(review.isRewriteUsed()).isFalse();
        assertThat(review.getCreatedAt()).isNotNull();
        assertThat(review.getUpdatedAt()).isEqualTo(review.getCreatedAt());
    }

    @Test
    void 리뷰는_한_번만_수정할_수_있다() {
        Review review = createReview();

        review.update(
                4,
                "수정된 리뷰",
                177,
                77,
                FitEvaluation.TRUE_TO_SIZE,
                FitPreference.RELAXED
        );

        assertThat(review.isEditUsed()).isTrue();
        assertThatThrownBy(() -> review.update(
                3,
                "다시 수정",
                null,
                null,
                FitEvaluation.SMALL,
                null
        ))
                .isInstanceOf(ReviewException.class)
                .hasMessage("리뷰는 한 번만 수정할 수 있습니다.");
    }

    @Test
    void 삭제한_리뷰는_한_번만_재작성할_수_있다() {
        Review review = createReview();
        review.delete();
        review.rewrite(
                5,
                "재작성 리뷰",
                178,
                78,
                FitEvaluation.TRUE_TO_SIZE,
                FitPreference.REGULAR
        );
        review.delete();

        assertThatThrownBy(() -> review.rewrite(
                4,
                "두 번째 재작성",
                null,
                null,
                FitEvaluation.LARGE,
                null
        ))
                .isInstanceOf(ReviewException.class)
                .hasMessage("삭제한 리뷰는 한 번만 재작성할 수 있습니다.");
    }

    @Test
    void 재작성한_리뷰는_수정_기회를_한_번_받는다() {
        Review review = createReview();
        review.update(
                4,
                "최초 리뷰 수정",
                null,
                null,
                FitEvaluation.TRUE_TO_SIZE,
                null
        );
        review.delete();
        review.rewrite(
                5,
                "재작성 리뷰",
                null,
                null,
                FitEvaluation.TRUE_TO_SIZE,
                FitPreference.REGULAR
        );

        assertThat(review.isEditUsed()).isFalse();
        assertThat(review.isRewriteUsed()).isTrue();

        review.update(
                5,
                "재작성 리뷰 수정",
                null,
                null,
                FitEvaluation.TRUE_TO_SIZE,
                FitPreference.RELAXED
        );

        assertThat(review.isEditUsed()).isTrue();
    }

    @Test
    void 환불된_리뷰는_제외_상태가_된다() {
        Review review = createReview();

        review.exclude();

        assertThat(review.getStatus()).isEqualTo(ReviewStatus.EXCLUDED);
        assertThatThrownBy(() -> review.update(
                3,
                "수정 불가",
                null,
                null,
                FitEvaluation.SMALL,
                null
        ))
                .isInstanceOf(ReviewException.class)
                .hasMessage("활성 상태의 리뷰만 수정할 수 있습니다.");
    }

    @Test
    void 필수값이_없거나_별점_범위가_잘못되면_작성할_수_없다() {
        assertThatThrownBy(() -> Review.create(
                orderItem,
                0,
                "리뷰",
                null,
                null,
                FitEvaluation.TRUE_TO_SIZE,
                null
        )).isInstanceOf(ReviewException.class);

        assertThatThrownBy(() -> Review.create(
                orderItem,
                5,
                " ",
                null,
                null,
                FitEvaluation.TRUE_TO_SIZE,
                null
        )).isInstanceOf(ReviewException.class);

        assertThatThrownBy(() -> Review.create(
                orderItem,
                5,
                "리뷰",
                null,
                null,
                null,
                null
        )).isInstanceOf(ReviewException.class);
    }

    private Review createReview() {
        return Review.create(
                orderItem,
                5,
                "사이즈가 잘 맞아요.",
                177,
                77,
                FitEvaluation.TRUE_TO_SIZE,
                FitPreference.REGULAR
        );
    }
}
