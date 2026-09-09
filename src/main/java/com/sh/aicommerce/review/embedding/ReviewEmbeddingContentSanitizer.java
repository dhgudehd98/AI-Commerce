package com.sh.aicommerce.review.embedding;

import com.sh.aicommerce.common.exception.outbox.review.ReviewEmbeddingException;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEmbeddingFailureCode;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class ReviewEmbeddingContentSanitizer {

    private static final Pattern HEIGHT_PATTERN = Pattern.compile(
            "(?iu)(?:키\\s*[:：]?\\s*)?\\d{2,3}(?:\\.\\d+)?\\s*(?:cm|센티(?:미터)?)"
                    + "|키\\s*[:：]?\\s*\\d{2,3}(?:\\.\\d+)?"
    );

    private static final Pattern WEIGHT_PATTERN = Pattern.compile(
            "(?iu)(?:몸무게\\s*[:：]?\\s*)?\\d{2,3}(?:\\.\\d+)?\\s*(?:kg|킬로(?:그램)?)"
                    + "|몸무게\\s*[:：]?\\s*\\d{2,3}(?:\\.\\d+)?"
    );

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL_CHARACTER_PATTERN = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern MULTIPLE_WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern LEADING_SEPARATOR_PATTERN = Pattern.compile("^[,;/|\\s]+");
    private static final Pattern TRAILING_SEPARATOR_PATTERN = Pattern.compile("[,;/|\\s]+$");

    public String sanitize(String content) {
        if (content == null || content.isBlank()) {
            throw sanitizationException(ReviewEmbeddingFailureCode.INVALID_EMBEDDING_CONTENT);
        }

        String sanitized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        sanitized = HtmlUtils.htmlUnescape(sanitized);
        sanitized = HTML_TAG_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = CONTROL_CHARACTER_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = HEIGHT_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = WEIGHT_PATTERN.matcher(sanitized).replaceAll(" ");
        sanitized = MULTIPLE_WHITESPACE_PATTERN.matcher(sanitized).replaceAll(" ").trim();
        sanitized = LEADING_SEPARATOR_PATTERN.matcher(sanitized).replaceFirst("");
        sanitized = TRAILING_SEPARATOR_PATTERN.matcher(sanitized).replaceFirst("");

        if (sanitized.isBlank()) {
            throw sanitizationException(ReviewEmbeddingFailureCode.INVALID_EMBEDDING_CONTENT);
        }

        if (HEIGHT_PATTERN.matcher(sanitized).find() || WEIGHT_PATTERN.matcher(sanitized).find()) {
            throw sanitizationException(ReviewEmbeddingFailureCode.BODY_INFO_SANITIZATION_FAILED);
        }

        return sanitized;
    }

    private ReviewEmbeddingException sanitizationException(ReviewEmbeddingFailureCode failureCode) {
        return new ReviewEmbeddingException(
                failureCode,
                new IllegalArgumentException(failureCode.name())
        );
    }
}
