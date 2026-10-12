package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sh.aicommerce.account.repository.AccountRepository;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.account.AccountException;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentMethod;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.dto.response.PreparePaymentResultDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import com.sh.aicommerce.toss.dto.request.TossPaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.TossPaymentSuccessResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final AuthRepository authRepository;
    private final ProductOptionRepository productOptionRepository;
    private final OrderRepository orderRepository;
    private final ProductInventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final PaymentTxService paymentTxService;

    private final TossPaymentClient client;

    /**
     * 결제 FLow
     * 1. 실제 결제를 하기 전에 호출 -> 사용자가 선택한 결제 정보에 따라서 결제 준비 설정
     * 2.
     * @param memberId
     * @param variantId
     * @param optionId
     * @param paymentRequestDto
     * @return
     */
    @Transactional
    public PreparePaymentResultDto preparePay(Long memberId, Long variantId, Long optionId, PaymentRequestDto paymentRequestDto) {
        try {
            log.info("[상품 결제 요청] 상품 ID : {}", variantId);
            log.info(paymentRequestDto.toString());

            // 멤버 및 상품(재고) 검증
            Member member = authRepository.findById(memberId).orElseThrow(() -> new MemberException("존재하지 않는 회원입니다. 로그인을 다시 시도해주세요."));

            // 상품 요청 값 검증
            ProductOption option = validateProduct(variantId, optionId);
            ProductVariant variant = option.getProductVariant();

            Integer productTotalPrice = variant.getPrice() + option.getAdditionalPrice();
            Integer deliveryPrice = setDeliveryPrice(productTotalPrice);

            // 최종 결제 금액 = 상품 Variant 가격 + 상품 옵션 추가 금액 + 배송비 - 사용 포인트 - 쿠폰이 있다면 쿠폰 금액
            Integer totalPaymentPrice = productTotalPrice + deliveryPrice - paymentRequestDto.getUsedPoint();

            //! Coupon에 대한 부분 구현하고 Coupon에 대한 가격 제외
//        Coupon coupon = new Coupon();
//        if (paymentRequestDto.getCouponId() != null) {
//            coupon = validateCoupon(paymentRequestDto.getCouponId());
//
//            totalPaymentPrice -= coupon.getPrice();
//        }


            /**
             * 결제 방법 -> 계좌 결제 || 카드 결제
             * - 계좌 결제 -> 등록되어 있는 계좌 ID와 요청한 memberId에 대한 값이 일치하는지 확인
             * - 카드 결제 -> 등록되어 있는 카드 ID와 요청한 memberId에 대한 값이 일치하는지 확인
             *
             * 현재 Card Entity, Account Entity에 대한 값은 구현되어 있지 않고 일단은 구현되어 있다고 가정한 후 검증 시도
             */
            Card card = new Card();
            Account account = new Account();

            // 결제 방법 -> 등록된 카드 결제 인 경우
            if (paymentRequestDto.getPaymentMethod().equals(PaymentMethod.SAVED_CARD)) {
                card = validateCard(memberId, paymentRequestDto.getSavedCardId());
            }

            if (paymentRequestDto.getPaymentMethod().equals(PaymentMethod.SAVED_ACCOUNT)) {
                account = validateAccount(memberId, paymentRequestDto.getSavedAccountId());
            }


            /**
             * Inventory에 실제로 재고가 존재하지는지 확인하기
             * 재고가 존재하면 -> 수량 변경
             * reservedQuantity += 1
             */
            
            ProductInventory productInventory = inventoryRepository.findByProductOptionIdForUpdate(option.getId()).orElseThrow(() -> new ProductException("현재 재고가 존재하지 않는 상품입니다."));

            // 실제로 재고가 존재하면 수량 변경 설정
            productInventory.reserve();

            // OrderItem 생성
            OrderItem item = OrderItem.createOrderItem(option);
            // Order 생성
            Orders order = Orders.createOrder(member, option.getId(), totalPaymentPrice);
            order.addOrderItem(item);

            // Delivery 생성
            Delivery delivery = Delivery.createDelivery(paymentRequestDto.getReceiver());
            order.setDelivery(delivery);

            // Payment 생성
            Payment payment = Payment.createPayment(paymentRequestDto, totalPaymentPrice);
            order.setPayment(payment);

            orderRepository.saveAndFlush(order);
            log.info("[주문 생성] 주문번호 : {}", order.getId());

            log.info("[결제 준비 완료] 주문 번호 : {}, 결제번호 : {}", order.getId(), payment.getId());

            return new PreparePaymentResultDto(
                    order.getId(),
                    order.getOrderNumber(),
                    variant.getVariantName(),
                    payment.getId(),
                    payment.getAmount(),
                    payment.getPaymentMethod(),
                    payment.getCardCompany(),
                    paymentRequestDto.getSavedCardId(),
                    paymentRequestDto.getSavedAccountId(),
                    card != null ? card.getBillingKey() : null,
                    member.getCustomerKey()
            );
        } catch (Exception e) {
            log.error("[결제 준비] : 에러 발생 에러메세지 : {}", e.getMessage());
            throw e;
        }

    }

    public TossPaymentSuccessResponseDto payByTossWidget(String orderNumber, String paymentKey, Integer amount) throws JsonProcessingException {
        log.info("[토스 페이먼츠 결제 승인 요청] : 주문번호(orderId) : {}", orderNumber);
        TossPaymentRequestDto request = new TossPaymentRequestDto(paymentKey, amount, orderNumber);
        // PG 요청 전 검증은 승인 거절 처리 범위에서 제외한다.
        if (paymentTxService.isPaidTossWidgetPayment(orderNumber, paymentKey, amount)) {
            Payment payment = paymentTxService.findPaidTossWidgetPayment(orderNumber, paymentKey, amount);
            return new TossPaymentSuccessResponseDto(payment);
        }
        paymentTxService.validatePaymentByTossWidget(request);

        TossPaymentSuccessResponseDto response;
        try {
            response = client.confirm(request);
        } catch (TossPaymentException e) {
            handlePaymentRejected(orderNumber, e);
            throw e;
        } catch (TossRestClientException e) {
            // 실제 승인됐을 수 있으므로 실패 확정 및 예약 해제를 하지 않는다.
            log.error("[위젯 결제 결과 확인 필요] 주문번호: {}, 결제금액: {}", orderNumber, amount, e);
            throw e;
        }

        // PG 승인 후 DB 반영 오류를 결제 거절로 처리하지 않는다.
        paymentTxService.applyPaymentByTossWidget(response, request);
        return response;
    }

    public TossPaymentSuccessResponseDto payByTossBillingCard(Long memberId, String orderNumber) throws JsonProcessingException {

        // 해당 주문번호가 이미 결제되어 있는 정보인지 확인
        if(paymentTxService.isPaidBillingCardPayment(orderNumber)) {
            Payment payment = paymentTxService.findPaidBillingCardPayment(orderNumber);

            return new TossPaymentSuccessResponseDto(payment);
        }

        log.info("[토스페이먼츠 자동 결제(Billing) 요청] 주문번호 : {}", orderNumber);
        Member member = paymentTxService.validateMember(memberId);
        Payment payment = paymentTxService.validatePaymentByBillingCard(orderNumber, memberId);
        Orders order = payment.getOrder();

        Card card = paymentTxService.validateCard(payment.getSavedCardId(), memberId);

        if(!order.getMember().getId().equals(member.getId())) throw new OrderException("주문 하려는 사용자의 정보가 일치하지 않습니다.");

        String orderName = "아이앱 스튜디오 후드 라이트 그레이";
        TossPaymentSuccessResponseDto response;
        try {
            response = client.cardBilling(card.getBillingKey(), member.getCustomerKey(), payment.getAmount(), orderNumber, orderName, member.getEmail(), member.getMemberName(), 0);
        } catch (TossPaymentException e) {
            handlePaymentRejected(orderNumber, e);
            throw e;
        } catch (TossRestClientException e) {
            // 결과 확인 전에는 예약 재고를 유지한다.
            log.error("[카드 자동결제 결과 확인 필요] 주문번호: {}, PaymentId: {}, 결제금액: {}",
                    orderNumber, payment.getId(), payment.getAmount(), e);
            throw e;
        }

        // 결제 완료시 Payment 및 Order 내역 업데이트
        paymentTxService.applyPaymentByTossBillingCard(memberId, orderNumber, response);
        log.info("[토스 페이먼츠 카드 결제 완료] 결제 번호 : " + orderNumber);

        return response;
    }


    private void handlePaymentRejected(String orderNumber, TossPaymentException e) {
        log.warn("[결제 승인 거절] 주문번호: {}, 오류코드: {}, 사유: {}",
                orderNumber, e.getErrorCode(), e.getMessage());
        try {
            // 명확한 승인 거절일 때만 실패 저장 및 예약 재고 해제
            paymentTxService.payFailTossPayment(orderNumber);
        } catch (RuntimeException saveException) {
            // 실패 저장 오류가 원래 PG 거절 예외를 덮어쓰지 않도록 보존
            e.addSuppressed(saveException);
            log.error("[결제 실패 반영 오류] 주문번호: {}", orderNumber, saveException);
        }
    }



//    private Coupon validateCoupon(Long couponId) {
//        return couponRepository.findById(couponId);
//    }

    private ProductOption validateProduct(Long variantId, Long optionId) {
        ProductOption option = productOptionRepository.findWithProductVariantByVariantIdAndOptionId(variantId, optionId).orElseThrow(() -> new ProductException("잘못된 상품 정보입니다. 상품정보를 다시 확인해주세요."));

        int availableQuantity = option.getInventories().stream()
                .mapToInt(inventory -> inventory.getAvailableQuantity())
                .sum();

        if(availableQuantity <= 0) throw new ProductException("상품 재고가 존재하지 않습니다.");

        return option;
    }

    // 등록된 카드
    private Card validateCard(Long memberId, Long cardId) {
        Card card = cardRepository.findByIdAndMemberId(cardId, memberId).orElseThrow(() -> new CardException("등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

        // 여기서 추가적으로 카드적으로 검증할거 있는지 체크 ? 뭐 만료날짜 같은거나
        return card;
    }

    private Account validateAccount(Long memberId, Long accountId) {
        Account account = accountRepository.findByIdAndMemberId(accountId, memberId).orElseThrow(() -> new AccountException("등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

        return account;
    }

    private int setDeliveryPrice(Integer productPrice) {
        if (productPrice >= 300000) {
            return 0;
        } else if (productPrice >= 200000) {
            return 3000;
        } else {
            return 5000;
        }
    }

    @Transactional
    public Payment validatePaymentByPaymentKey(String paymentKey) {
        return paymentRepository.findPaymentByPaymentKey(paymentKey)
                .orElseThrow(() -> new PaymentException("PaymentKey에 일치하는 Payment 내역이 존재하지 않습니다."));
    }

    public Payment findPaymentByOrderIdWithLock(Long orderId) {
        return paymentRepository.findPaymentByOrderIdWithLock(orderId)
                .orElseThrow(() -> new PaymentException("주문 번호에 해당하는 지불 내역이 존재하지 않습니다. 주문 번호 : " + orderId));
    }

    public Payment findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException("주문 번호에 일치하는 결제 내역이 존재하지 않습니다. 주문 번호 : " + orderId));
    }
}