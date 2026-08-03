package com.sh.aicommerce.review.service;

import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.review.ReviewException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.review.FitEvaluation;
import com.sh.aicommerce.enums.review.FitPreference;
import com.sh.aicommerce.enums.review.dto.ReviewDocumentDto;
import com.sh.aicommerce.enums.review.reviewEvent.ReviewEventType;
import com.sh.aicommerce.orderItem.service.OrderItemService;
import com.sh.aicommerce.outboxEvent.review.repository.ReviewOutBoxEventRepository;
import com.sh.aicommerce.review.dto.ReviewRequestDto;
import com.sh.aicommerce.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewService {

    private final AuthRepository authRepository;
    private final OrderItemService itemService;
    private final ReviewRepository reviewRepository;
    private final ReviewOutBoxEventRepository eventRepository;

    @Transactional
    public Map<String,String> createReview(Long memberId, Long orderItemId, ReviewRequestDto request) {
        log.info("[리뷰 생성 요청] : orderItemId : {}", orderItemId);

        // 주문 상품에 대해서 여러개의 리뷰 작성했는지 중복 검사
        if(reviewRepository.existsByOrderItemId(orderItemId))
            throw new ReviewException("주문한 상품에 대해서 상품 리뷰는 하나만 등록하실 수 있습니다.");

        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("등록되지 않은 회원입니다. 다시 로그인해주세요."));
        OrderItem orderItem = itemService.validateOrderItemPaidOrder(memberId, orderItemId);

        // Review Entity 생성
        Review review = Review.create(
                orderItem,
                request.getRating(),
                request.getContent(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getFitEvaluation(),
                request.getFitPreference()
                );

        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new ReviewException("해당 주문 상품에는 이미 리뷰가 작성되어 있습니다.");
        }

        //색인 과정을 비동기 처리 하기 위한 outBox 패턴 적용
        ReviewOutboxEvent event = ReviewOutboxEvent.createEvent(review, orderItem.getProductOption().getId(), ReviewEventType.CREATED);
        eventRepository.save(event);


        return Map.of(
                "result", "Y",
                "message", "리뷰가 성공적으로 등록되었습니다.");
    }

    @Transactional
    public Map<String, String> updateReview(Long memberId, Long orderItemId, ReviewRequestDto request) {
        log.info("[리뷰 수정 요청] orderItemId : {}", orderItemId);

        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("등록되지 않은 회원입니다. 다시 로그인해주세요."));
        OrderItem orderItem = itemService.validateOrderItemPaidOrder(memberId, orderItemId);

        // 리뷰 중복 수정 가능 여부 파악
        Review review = reviewRepository.reviewUpdateForUpdate(orderItemId).orElseThrow(() -> new ReviewException("이미 작성된 리뷰는 한번만 수정할 수 있습니다."));

        review.update(
                request.getRating(),
                request.getContent(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getFitEvaluation(),
                request.getFitPreference()
                );

        log.info("[리뷰 수정 요청 완료] orderItemId : {}", orderItemId);

        ReviewOutboxEvent event = ReviewOutboxEvent.createEvent(review, orderItem.getProductOption().getId(), ReviewEventType.UPDATED);
        eventRepository.save(event);

        return Map.of(
                "result", "Y",
                "message", "리뷰가 성공적으로 수정되었습니다..");
    }

    @Transactional
    public Map<String,String> deleteReview(Long memberId, Long orderItemId) {
        log.info("[리뷰 삭제 요청] orderItemId : {}", orderItemId);

        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("등록되지 않은 회원입니다. 다시 로그인해주세요."));
        OrderItem orderItem = itemService.validateOrderItemPaidOrder(memberId, orderItemId);

        // 리뷰 중복 수정 가능 여부 파악
        Review review = reviewRepository.reviewDeleteForUpdate(orderItemId).orElseThrow(() -> new ReviewException("삭제 가능한 리뷰가 존재하지 않습니다."));

        ReviewOutboxEvent event = ReviewOutboxEvent.createEvent(review, orderItem.getProductOption().getId(), ReviewEventType.DELETED);
        eventRepository.save(event);
        review.delete();

        log.info("[리뷰 삭제 요청 완료] reviewId : {}, orderItemId : {}", review.getId(), orderItemId);

        return Map.of(
                "result", "Y",
                "message", "리뷰가 성공적으로 삭제되었습니다.");
    }

    @Transactional
    public Map<String,String> reWriteReview(Long memberId, Long orderItemId, ReviewRequestDto request) {
        log.info("[리뷰 재작성 요청] orderItemId : {}", orderItemId);
        Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("등록되지 않은 회원입니다. 다시 로그인해주세요."));
        OrderItem orderItem = itemService.validateOrderItemPaidOrder(memberId, orderItemId);

        // 리뷰 중복 수정 가능 여부 파악
        Review review = reviewRepository.reviewReWriteForUpdate(orderItemId).orElseThrow(() -> new ReviewException("재작성 가능한 리뷰가 존재하지 않습니다."));

        review.rewrite(
                request.getRating(),
                request.getContent(),
                request.getHeightCm(),
                request.getWeightKg(),
                request.getFitEvaluation(),
                request.getFitPreference()
        );

        log.info("[리뷰 재작성 요청 완료] orderItemId : {}", orderItemId);

        ReviewOutboxEvent event = ReviewOutboxEvent.createEvent(review, orderItem.getProductOption().getId(), ReviewEventType.REWRITTEN);
        eventRepository.save(event);

        return Map.of(
                "result", "Y",
                "message", "리뷰가 성공적으로 작성되었습니다..");
    }

    @Transactional(readOnly = true)
    public Optional<ReviewDocumentDto> getReviewIndexSource(Long reviewId) {
        return reviewRepository.findByIdWithProductOption(reviewId)
                .map(review -> ReviewDocumentDto.create(review));
    }
}
