package com.sh.aicommerce.payment.service;

import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.*;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import com.sh.aicommerce.payment.dto.request.CardBillingPaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.dto.response.PaymentCompleteDto;
import com.sh.aicommerce.payment.dto.response.PgPaymentSuccessResponseDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentTxService {
    private final AuthRepository authRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CardRepository cardRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final TossPaymentLogRepository tossPaymentLogRepository;

    private final OutboundService outboundService;
    private final ProductInventoryService inventoryService;


    @Transactional
    public PaymentDecision adjustPaymentAction(Long memberId, CardBillingPaymentRequestDto cardBillingPaymentRequestDto) {

        Payment payment = paymentRepository.findById(cardBillingPaymentRequestDto.getPaymentId())
                .orElseThrow(() -> new PaymentException("[결제/DECIDE] 주문 번호에 존재하는 결제 내역이 존재하지 않습니다."));
        PaymentStatus status = payment.getStatus();

        log.info("[PAYMENT] stage=DECIDE event=STATE_READ orderNumber={} paymentId={} attemptId={} paymentStatus={}", cardBillingPaymentRequestDto.getOrderNumber(), payment.getId(), cardBillingPaymentRequestDto.getPaymentAttemptId(), status);
        // 상태 값이 준비단계 || 현재 진행중인 상태라면 -> PG 결제 재요청
        if (status == PaymentStatus.READY || status == PaymentStatus.PROCESSING) {
            return validatePayment(memberId, cardBillingPaymentRequestDto);
        }
        // 상태 값이 지불완료 단계이면 결제 완료된 화면만 출력
        else if (status == PaymentStatus.PAID)
            return findPaymentSuccessResult(memberId, cardBillingPaymentRequestDto);
            // 상태 값이 CANCELED면 현재 취소가 됐거나 취소가 완료된 결제 내역이라고 메세지 출력
        else throw new PaymentException("[결제/DECIDE] 현재 결제 요청을 처리 할 수 있는 상태가 아닙니다.");
    }

    @Transactional
    public PaymentDecision findPaymentSuccessResult(Long memberId, CardBillingPaymentRequestDto cardBillingPaymentRequestDto) {

        // 연관된 Entity들 검증
        Member member = authRepository.findById(memberId)
                .orElseThrow(() ->
                        new MemberException("[결제/COMPLETED_READ] 회원 정보가 존재하지 않습니다."));
        Payment payment = paymentRepository.findPaymentByIdWithOrder(cardBillingPaymentRequestDto.getPaymentId())
                .orElseThrow(() -> new PaymentException("[결제/COMPLETED_READ] 주문 번호에 존재하는 결제 내역이 존재하지 않습니다."));
        Orders order = payment.getOrder();
        PaymentAttempt attempt = paymentAttemptRepository.findPaymentAttemptByAttemptId(cardBillingPaymentRequestDto.getPaymentAttemptId())
                .orElseThrow(() -> new PaymentAttemptException("[결제/COMPLETED_READ] 현재 승인 요청 번호에 일치하는 승인 요청에 대한 데이터가 존재하지 않습니다."));

        // 본인 주문인지 검증
        if(!order.getMember().getId().equals(member.getId()))
            throw new PaymentException("[결제/COMPLETED_READ] 본인의 주문건만 결제 요청을 진행할 수 있습니다.");

        // 주문 내역에 존재하는 주문번호와 클라이언트 측에서 전달받은  주문번호가 동일하지 않음
        if(!order.getOrderNumber().equals(cardBillingPaymentRequestDto.getOrderNumber()))
            throw new OrderException("[결제/COMPLETED_READ] 현재 주문 번호가 동일하지 않습니다. 관리자에게 문의해주세요.");

        // 승인 시도 요청 내역과 결제 내역에 대한 값이 동일한지 확인
        if(!attempt.getPayment().getId().equals(payment.getId()))
            throw new PaymentException("[결제/COMPLETED_READ] 결제 승인 요청 내역에 있는 결제 번호와 현재 결제번호가 존재하지 않습니다.");

        PaymentStatus paymentStatus = payment.getStatus();
        OrderStatus orderStatus = order.getStatus();
        PaymentAttemptStatus attemptStatus = attempt.getStatus();

        // 결제, 주문, 결제 승인 요청 상태 값이 이미 완료되어 있는 내역이 아니면 return
        if(paymentStatus != PaymentStatus.PAID ||
                orderStatus != OrderStatus.PAID ||
                    attemptStatus != PaymentAttemptStatus.SUCCESS)
            throw new PaymentException("[결제/COMPLETED_READ] 현재 결제 완료의 상태가 아닙니다.");

        log.info("[PAYMENT] stage=COMPLETED_READ event=REUSED orderNumber={} paymentId={} attemptId={}", order.getOrderNumber(), payment.getId(), attempt.getId());
        PaymentCompleteDto completeDto = new PaymentCompleteDto(
                payment, order, attempt
        );

        return new PaymentDecision(
                null,
                null,
                PaymentAction.COMPLETED,
                completeDto
        );
    }
    /**
     * PG 결제 요청전 데이터 검증
     * - Payment, Order 존재 여부 확인
     * - 주문 소유자 확인(해당 주문이 로그인한 회원의 주문인지 확인)
     */
    @Transactional
    public PaymentDecision validatePayment(
            Long memberId,
            CardBillingPaymentRequestDto requestDto
    ) {
        Member member = authRepository.findById(memberId)
                .orElseThrow(() ->
                        new MemberException("[결제/APPROVAL_VALIDATE] 회원 정보가 존재하지 않습니다."));

        // 결제 존재 + 주문 존재 + 주문번호 연결 검사
        Payment payment = paymentRepository.findPaymentByIdWithOrderForUpdate(
                requestDto.getPaymentId(),
                requestDto.getOrderNumber()
        ).orElseThrow(() ->
                new PaymentException("[결제/APPROVAL_VALIDATE] 주문과 일치하는 결제 정보가 없습니다."));

        Orders order = orderRepository.findOrderByOrderNumberForUpdate(requestDto.getOrderNumber())
                .orElseThrow(() -> new OrderException("[결제/APPROVAL_VALIDATE] 주문 번호에 해당하는 주문 내역이 존재하지 않습니다."));

        if(!order.getId().equals(payment.getOrder().getId()))
            throw new OrderException("[결제/APPROVAL_VALIDATE] 결제 내역에 저장된 주문 정보와 주문번호로 저장된 주문내역에 대한 정보가 다릅니다.");

        String orderName = orderItemRepository.findByOrderNumber(requestDto.getOrderNumber())
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderException("[결제/APPROVAL_VALIDATE] 주문 상품명이 존재하지 않습니다."));

        if (!memberId.equals(order.getMember().getId())) {
            throw new OrderException("[결제/APPROVAL_VALIDATE] 본인의 주문만 결제할 수 있습니다.");
        }

        // 현재 주문 상태가 결제 대기 , 결제 진행중 -> 상태 일때 검증 통과
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT
                && order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            throw new OrderException("[결제/APPROVAL_VALIDATE] 결제 가능한 주문 상태가 아닙니다.");
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING
                && payment.getStatus() != PaymentStatus.READY) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 현재 결제가 가능한 결제 상태가 아닙니다.");
        }

        if (payment.getPaymentMethod() != PaymentMethod.SAVED_CARD) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 저장된 카드 결제 내역이 아닙니다.");
        }

        if (payment.getAmount() == null
                || payment.getAmount() <= 0
                || !payment.getAmount().equals(order.getTotalPrice())) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 주문과 결제 금액이 유효하지 않습니다.");
        }

        if (payment.getSavedCardId() == null) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 결제할 카드 정보가 없습니다.");
        }

        Long savedCardId = payment.getSavedCardId();

        // 결제 내역에 저장된 카드 값 + 결제 요청한 카드 ID에 대한 값이 동일한지 확인
        if(savedCardId == null || !savedCardId.equals(requestDto.getSavedCardId()))
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 결제 내역에 저장된 카드 ID와 결제 요청한 카드 ID에 대한 값이 일치하지 않습니다.");

        Card card = cardRepository.findByIdAndMemberId(
                savedCardId, memberId
        ).orElseThrow(() ->
                new PaymentException("[결제/APPROVAL_VALIDATE] 본인의 결제 카드가 존재하지 않습니다."));

        if (!card.isActive() || card.getDeletedAt() != null) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 사용할 수 없는 카드입니다.");
        }

        if (!StringUtils.hasText(card.getBillingKey())
                || !StringUtils.hasText(member.getCustomerKey())) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 자동 결제 인증 정보가 없습니다.");
        }

        // 승인 요청 내역 생성 확인 후 상태 값을 진행중으로 변경
        PaymentAttempt attempt = paymentAttemptRepository.findPaymentAttemptByAttemptIdForUpdate(requestDto.getPaymentAttemptId())
                .orElseThrow(() -> new PaymentAttemptException("[결제/APPROVAL_VALIDATE] 결제 승인 요청 내역이 존재하지 않습니다."));

        if (attempt.getPayment() == null
                || !payment.getId().equals(attempt.getPayment().getId())) {
            throw new PaymentAttemptException("[결제/APPROVAL_VALIDATE] 결제와 승인 시도가 일치하지 않습니다.");
        }

        if (!payment.getAmount().equals(attempt.getRequestAmount())) {
            throw new PaymentAttemptException("[결제/APPROVAL_VALIDATE] 결제 금액과 승인 요청 금액이 일치하지 않습니다.");
        }

        if(attempt.getStatus() != PaymentAttemptStatus.READY
                && attempt.getStatus() != PaymentAttemptStatus.PROCESSING)
            throw new PaymentAttemptException("[결제/APPROVAL_VALIDATE] 현재 결제 승인을 진행할 수 없는 상태입니다. 상태 값을 확인해주세요.");

        // 아래에 유효기간·주문별 예약 검증이 추가되어야 함

        // 결제와 관련된 Entity의 상태값이 결제대기 상태인 경우에는 검증에 통과하면 PROCESSING으로 상태 값 변경 
        // 만약에 PROCESSING의 상태인 경우에는 Update하지 않음.
        boolean ready =
                payment.getStatus() == PaymentStatus.READY
                        && attempt.getStatus() == PaymentAttemptStatus.READY
                        && order.getStatus() == OrderStatus.PENDING_PAYMENT;

        boolean processing =
                payment.getStatus() == PaymentStatus.PROCESSING
                        && attempt.getStatus() == PaymentAttemptStatus.PROCESSING
                        && order.getStatus() == OrderStatus.PAYMENT_PROCESSING;

        log.info("[PAYMENT] stage=APPROVAL_VALIDATE event=STATE_CHECK orderNumber={} paymentId={} attemptId={} paymentStatus={} orderStatus={} attemptStatus={} replay={}", order.getOrderNumber(), payment.getId(), attempt.getId(), payment.getStatus(), order.getStatus(), attempt.getStatus(), processing);
        if (ready) {
            attempt.updateAttemptStatusToProcessing();
            payment.updatePaymentStatusToProcessingByBilling();
            order.updateOrderStatusToPaymentProcessing();
        } else if (!processing) {
            throw new PaymentException("[결제/APPROVAL_VALIDATE] 주문과 결제 상태가 일치하지 않습니다.");
        }

        return new PaymentDecision(
                new PaymentBillingCardRequestDto(
                        payment,
                        attempt,
                        order,
                        card,
                        member,
                        orderName
                ),
                null,
                PaymentAction.PG_REQUEST,
                null
        );
    }

    /**
     * PG 결제 요청 후 요청 데이터와 응답 데이터 검증 처리
     * - OrderId가 동일한지 확인
     * - orderName이 동일한지 확인
     * - totalAmount(총 결제 완료 금액)과 amount 결제 내역이 동일한지 확인
     *
     */
    @Transactional
    public PgPaymentSuccessResponseDto validatePGResponseData(PaymentBillingCardRequestDto billingCardRequestDto, TossPaymentSuccessResponseDto responseDto) {
        log.info("[PAYMENT] stage=APPROVAL_VERIFY event=START orderNumber={} paymentId={} attemptId={} approvedAmount={} pgStatus={}", billingCardRequestDto.getOrderNumber(), billingCardRequestDto.getPaymentId(), billingCardRequestDto.getAttemptId(), responseDto.getTotalAmount(), responseDto.getStatus());
        Payment payment = paymentRepository.findPaymentWithOrderByOrderNumber(billingCardRequestDto.getOrderNumber())
                .orElseThrow(() -> new PaymentException("[결제/APPROVAL_VERIFY] 주문 번호에 해당하는 결제 내역이 존재하지 않습니다."));
        Orders order = payment.getOrder();
        String orderName = orderItemRepository.findByOrderNumber(billingCardRequestDto.getOrderNumber())
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderException("[결제/APPROVAL_VERIFY] 주문 상품명이 존재하지 않습니다."));

        if (!order.getOrderNumber().equals(responseDto.getOrderId())){
            log.error("[PAYMENT] stage=APPROVAL_VERIFY event=ORDER_MISMATCH paymentId={} attemptId={}", payment.getId(), billingCardRequestDto.getAttemptId());
            throw new OrderException("[결제/APPROVAL_VERIFY] 잘못된 주문번호 결제 ");
        }

        if (!orderName.equals(responseDto.getOrderName())) {
            log.error("[PAYMENT] stage=APPROVAL_VERIFY event=ORDER_NAME_MISMATCH orderNumber={} paymentId={}", order.getOrderNumber(), payment.getId());
            throw new OrderException("[결제/APPROVAL_VERIFY] 일치하지 않는 상품명");

        }

        if (!payment.getAmount().equals(responseDto.getTotalAmount())) {
            log.error("[PAYMENT] stage=APPROVAL_VERIFY event=AMOUNT_MISMATCH orderNumber={} paymentId={} expectedAmount={} approvedAmount={}", order.getOrderNumber(), payment.getId(), payment.getAmount(), responseDto.getTotalAmount());
            throw new PaymentException("[결제/APPROVAL_VERIFY] 결제 요청 금액과 결제 승인 금액 불일치");
        }

        if (payment.getStatus() == PaymentStatus.PAID) {
            // 동일한 승인 결과인지 확인
            if (!Objects.equals(payment.getPaymentKey(), responseDto.getPaymentKey())
                    || !Objects.equals(payment.getAmount(), responseDto.getTotalAmount())
                    || order.getStatus() != OrderStatus.PAID) {
                throw new PaymentException("[결제/APPROVAL_VERIFY] 기존 결제 완료 정보와 일치하지 않습니다.");
            }

            PaymentAttempt attempt =
                    paymentAttemptRepository.findPaymentAttemptByAttemptIdForUpdate(
                            billingCardRequestDto.getAttemptId()
                    ).orElseThrow(() ->
                            new PaymentAttemptException("[결제/APPROVAL_VERIFY] 승인 시도가 존재하지 않습니다."));

            if (!attempt.getPayment().getId().equals(payment.getId())
                    || attempt.getStatus() != PaymentAttemptStatus.SUCCESS
                    || !Objects.equals(attempt.getPaymentKey(), responseDto.getPaymentKey())) {
                throw new PaymentAttemptException("[결제/APPROVAL_VERIFY] 기존 승인 결과와 일치하지 않습니다.");
            }

            log.info("[PAYMENT] stage=APPROVAL_APPLY event=REPLAYED orderNumber={} paymentId={} attemptId={}", order.getOrderNumber(), payment.getId(), attempt.getId());
            // 업데이트·출고 생성을 다시 하지 않고 반환
            return new PgPaymentSuccessResponseDto(payment, attempt, order);
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING
                || order.getStatus() != OrderStatus.PAYMENT_PROCESSING) {
            throw new PaymentException("[결제/APPROVAL_VERIFY] 결제 완료를 반영할 수 없는 상태입니다.");
        }

        log.info("[PAYMENT] stage=APPROVAL_VERIFY event=VALIDATED orderNumber={} paymentId={} attemptId={}", order.getOrderNumber(), payment.getId(), billingCardRequestDto.getAttemptId());
        return applyPaymentByCard(billingCardRequestDto, responseDto);
    }
    // DB에 대한 내역을 저장하기 전에 성공적으로 데이터가 완료됐는지 제대로 검증을 해봐야될듯 그리고 저장을 해야겠어

    public PgPaymentSuccessResponseDto applyPaymentByCard(PaymentBillingCardRequestDto billingCardRequestDto, TossPaymentSuccessResponseDto responseDto) {
        log.info("[PAYMENT] stage=APPROVAL_APPLY event=START orderNumber={} paymentId={} attemptId={}", billingCardRequestDto.getOrderNumber(), billingCardRequestDto.getPaymentId(), billingCardRequestDto.getAttemptId());
        // 결제 내역의 상태를 업데이트 하기 떄문에 Payment, Orders를 비관적 락으로 조회
        // 정상적으로 결제 승인이 된 경우 상태에 대한 값을 Payment, Order 둘다 PAID로 변경
        Payment payment = paymentRepository.findPaymentByOrderNumberForUpdate(billingCardRequestDto.getOrderNumber(), billingCardRequestDto.getMemberId())
                .orElseThrow(() -> new PaymentException("[결제/APPROVAL_APPLY] 주문 번호에 해당 하는 결제 정보를 찾을 수 없습니다. 관리자에게 문의해주세요."));

        Orders order = orderRepository.findOrderByOrderNumberForUpdate(billingCardRequestDto.getOrderNumber())
                .orElseThrow(() -> new OrderException("[결제/APPROVAL_APPLY] 주문 번호에 해당 하는 주문 내역이 존재하지 않습니다."));

        PaymentAttempt attempt = paymentAttemptRepository.findPaymentAttemptByAttemptIdForUpdate(billingCardRequestDto.getAttemptId())
                .orElseThrow(() -> new PaymentAttemptException("[결제/APPROVAL_APPLY] 해당 지불 내역 ID에 일치하는 결제 신청 이력이 존재하지 않습니다."));

        // 토스 응답 내역 저장
        TossPaymentLog tossPaymentLog = TossPaymentLog.confirmSuccess(payment, responseDto, 200, billingCardRequestDto.toString(), responseDto.toString());
        tossPaymentLogRepository.save(tossPaymentLog);
        // 상태값 및 내역 업데이트 처리
        payment.updatePaymentByBillingCard(responseDto);
        order.updateStatusToPaymentComplete();
        attempt.updateAttemptSuccessPay(responseDto);

        // 결제 완료 시 출고 요청 처리
        outboundService.createOutbound(order);
        log.info("[PAYMENT] stage=APPROVAL_APPLY event=APPLIED_PENDING_COMMIT orderNumber={} orderId={} paymentId={} attemptId={} paymentStatus={} orderStatus={} attemptStatus={}", order.getOrderNumber(), order.getId(), payment.getId(), attempt.getId(), payment.getStatus(), order.getStatus(), attempt.getStatus());

        // 출고 요청 처리 후, 결제가 완료되면 실제 재고 감소 처리
//        inventoryService.decreaseQuantityProduct(payment.getId());

        return new PgPaymentSuccessResponseDto(payment, attempt, order);
    }
    
}
