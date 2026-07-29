package com.sh.aicommerce.review.controller;

import com.sh.aicommerce.review.dto.ReviewRequestDto;
import com.sh.aicommerce.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/review")
public class ReviewController {
    private final ReviewService reviewService;

    @PostMapping("{orderItemId}")
    public ResponseEntity<?> createReview(
            @PathVariable("orderItemId") Long orderItemId,
            @RequestBody ReviewRequestDto request
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(reviewService.createReview(memberId, orderItemId, request));
    }

    @PostMapping("update/{orderItemId}")
    public ResponseEntity<?> updateReview(
            @PathVariable("orderItemId") Long orderItemId,
            @RequestBody ReviewRequestDto request
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(reviewService.updateReview(memberId, orderItemId, request));
    }

    @DeleteMapping("delete/{orderItemId}")
    public ResponseEntity<?> deleteReview(
            @PathVariable("orderItemId") Long orderItemId
    ) {
        Long memberId = 1L;
        return ResponseEntity.ok(reviewService.deleteReview(memberId, orderItemId));
    }
}