package com.sh.aicommerce.review.embedding;

import com.sh.aicommerce.common.exception.outbox.review.ReviewEmbeddingException;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewEmbeddingContentSanitizerTest {

    private final ReviewEmbeddingContentSanitizer sanitizer =
            new ReviewEmbeddingContentSanitizer();

    @Test
    void 리뷰_본문의_키와_몸무게를_제거한다() {
        String content = "키 178cm, 몸무게 75kg이고 L 사이즈가 잘 맞아요.";

        String sanitized = sanitizer.sanitize(content);

        assertThat(sanitized)
                .doesNotContain("178", "75", "cm", "kg", "키", "몸무게")
                .contains("L 사이즈가 잘 맞아요.");
    }

    @Test
    void 센티와_킬로_표현도_제거한다() {
        String content = "178센티 / 75킬로, 적당한 오버핏입니다.";

        String sanitized = sanitizer.sanitize(content);

        assertThat(sanitized).isEqualTo("적당한 오버핏입니다.");
    }

    @Test
    void 신체정보를_제거한_결과가_비어있으면_임베딩하지_않는다() {
        assertThatThrownBy(() -> sanitizer.sanitize("키 178cm, 몸무게 75kg"))
                .isInstanceOf(ReviewEmbeddingException.class)
                .extracting("failureCode")
                .isEqualTo(ReviewEmbeddingFailureCode.INVALID_EMBEDDING_CONTENT);
    }

    @Test
    void HTML과_제어문자를_정리한다() {
        String sanitized = sanitizer.sanitize("<p>소매가\n잘 맞아요.</p>");

        assertThat(sanitized).isEqualTo("소매가 잘 맞아요.");
    }
}
