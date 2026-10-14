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
import com.sh.aicommerce.payment.dto.request.CardBillingPaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.dto.response.PreparePaymentResultDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
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
    private final PaymentAttemptRepository attemptRepository;

    private final TossPaymentClient client;

    private final PaymentTransactionService paymentTransactionService;
    private final PaymentTxService paymentTxService;

    /**
     * 결제 Flow
     * 1. 실제 결제를 하기전 주문 시트지 생성(preparePay)
     * a. 주문 시트지에 필요한 사항
     * - 주문자 정보(배송지)
     * - 상품 정보(variantId, optionId)
     * - 결제 수단
     * - 정책 수단
     * -
     * 2. 금액 결정
     *  - 상품 기본 금액
     *  - 상품 옵션별 추가 금액
     *  - 상품 가격에 따른 배송비
     *  - 쿠폰 금액
     *  - 포인트 사용 금액
     *  -> 총 결제 금액 = 상품 금액(기본금액 + 추가 금액) + 배송비 - (쿠폰 금액 + 포인트 사용금액)
     *
     * 3. 사용자가 결제 선택 수단에 따른 결제 방식 선택
     * a. TossPayments 사용
     * b. saved_account
     * c. saved_card
     *  => TossPayment 제외 saved에 대한 정보는 등록되어 있는 카드나 등록되어 있는 계좌를 사용하여 간편 결제
     */

    @Transactional
    public PreparePaymentResultDto preparePay(Long memberId, Long variantId, Long optionId, PaymentRequestDto paymentRequestDto) {

        // 일단 동일 주문 생성 방지만 설정 - 중복 방지에 대한 코드 완성하고, 멱등성 로직 추가
        if(orderRepository.existsByOrderNumber(paymentRequestDto.getOrderNumber()))
            throw new OrderException("현재 진행중인 주문 정보가 존재합니다. 잠시 후에 다시 시도해주세요.");

        return createPaymentAndOrder(memberId, variantId, optionId, paymentRequestDto);
    }

    private PreparePaymentResultDto createPaymentAndOrder(Long memberId, Long variantId, Long optionId, PaymentRequestDto paymentRequestDto) {
        log.info("[결제 요청] 회원 번호 : {}, 상품번호 : {}, 옵션번호 : {}, 결제 방법 : {}", memberId, variantId, optionId, paymentRequestDto.getPaymentMethod());
        Member member = validateMember(memberId);

        // 상품 데이터 검증
        ProductOption option = validateProduct(variantId, optionId);
        ProductVariant variant = option.getProductVariant();

        // 금액 검증 = 상품 기본 금액 + 옵션별 추가 금액 + 배송비 - (쿠폰 사용 금액 , 사용 포인트) -> 현재 쿠폰, 사용 포인트에 대한 값은 구현되어 있지 않음
        Integer productTotalPrice = variant.getPrice() + option.getAdditionalPrice();
        Integer totalPaymentPrice = productTotalPrice + setDeliveryPrice(productTotalPrice);

        // 사용자 결제 수단 선택
        PaymentMethod paymentMethod = paymentRequestDto.getPaymentMethod();
        Card card = new Card();
        Account account = new Account();

        if(paymentMethod.equals(PaymentMethod.SAVED_CARD))
            card = validateCard(memberId, paymentRequestDto.getSavedCardId());
        if(paymentMethod.equals(PaymentMethod.SAVED_ACCOUNT))
            account = validateAccount(memberId, paymentRequestDto.getSavedAccountId());


        /**
         * Inventory에 실제로 재고가 존재하지는지 확인하기
         * 재고가 존재하면 -> 수량 변경
         * reservedQuantity += 1
         */
        ProductInventory productInventory = inventoryRepository.findByProductOptionIdForUpdate(option.getId())
                .orElseThrow(() -> new ProductException("현재 재고가 존재하지 않는 상품입니다."));
        productInventory.reserve();

        // OrderItem 생성
        OrderItem item = OrderItem.createOrderItem(option);
        // Order 생성
        Orders order = Orders.createOrder(member, option.getId(), totalPaymentPrice, paymentRequestDto.getOrderNumber());
        order.addOrderItem(item);

        // Delivery 생성
        Delivery delivery = Delivery.createDelivery(paymentRequestDto.getReceiver());
        order.setDelivery(delivery);

        // Payment 생성
        Payment payment = Payment.createPayment(paymentRequestDto, totalPaymentPrice);
        order.setPayment(payment);

        orderRepository.saveAndFlush(order);

        // PaymentAttempt 생성
        PaymentAttempt attempt = PaymentAttempt.createPaymentAttempt(payment);
        attemptRepository.save(attempt);


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
                member.getCustomerKey(),
                attempt.getId()
        );
    }

    public TossPaymentSuccessResponseDto payCard(Long memberId, CardBillingPaymentRequestDto cardBillingPaymentRequestDto) {

        log.info("[결제 요청] PG사 자동 카드 결제 요청 회원번호 : {}, 주문번호 : {}, 결제 번호 : {}", memberId, cardBillingPaymentRequestDto.getOrderNumber(), cardBillingPaymentRequestDto.getPaymentId());

        // PG 요청 전 데이터 검증 처리
        log.info("[결제 요청] PG사 자동 카드 결제 요청전 데이터 검증 시작 회원번호 : {}, 주문번호 : {}, 결제 번호 : {}", memberId, cardBillingPaymentRequestDto.getOrderNumber(), cardBillingPaymentRequestDto.getPaymentId());
        PaymentBillingCardRequestDto billingCardRequestDto = paymentTxService.validatePayment(memberId, cardBillingPaymentRequestDto);
        log.info("[결제 요청] PG사 자동 카드 결제 요청전 데이터 검증 완료 회원번호 : {}, 주문번호 : {}, 결제 번호 : {}", memberId, cardBillingPaymentRequestDto.getOrderNumber(), cardBillingPaymentRequestDto.getPaymentId());


        TossPaymentSuccessResponseDto responseDto = new TossPaymentSuccessResponseDto();
        // PG 자동 카드 결제 요청
        log.info("[결제 요청] PG사 자동 카드 결제 요청 시작 회원 번호 : {}, 주문 번호 : {}, 주문 상품 : {}", memberId, billingCardRequestDto.getOrderNumber(), billingCardRequestDto.getOrderName());
        try {
            responseDto = client.cardBilling(billingCardRequestDto);
            // 결제 요청이 성공적으로 진행되면 결제 내역 검증 후 저장
            paymentTxService.validatePGResponseData(billingCardRequestDto, responseDto);

        }catch (TossPaymentException e) {
//            handlePaymentRejected(orderNumber, e);
            throw e;
        } catch (TossRestClientException e) {
            // 결과 확인 전에는 예약 재고를 유지한다.
//            log.error("[카드 자동결제 결과 확인 필요] 주문번호: {}, PaymentId: {}, 결제금액: {}",
//                    orderNumber, payment.getId(), payment.getAmount(), e);
            throw e;
        }
        catch (Exception e) {
            log.error("[PG사 결제 요청 실패]  회원 번호 : {} ", e);
        }


        return responseDto;
    }

    public TossPaymentSuccessResponseDto payByTossWidget(String orderNumber, String paymentKey, Integer amount) throws JsonProcessingException {
        log.info("[토스 페이먼츠 결제 승인 요청] : 주문번호(orderId) : {}", orderNumber);
        TossPaymentRequestDto request = new TossPaymentRequestDto(paymentKey, amount, orderNumber);
        // PG 요청 전 검증은 승인 거절 처리 범위에서 제외한다.
        if (paymentTransactionService.isPaidTossWidgetPayment(orderNumber, paymentKey, amount)) {
            Payment payment = paymentTransactionService.findPaidTossWidgetPayment(orderNumber, paymentKey, amount);
            return new TossPaymentSuccessResponseDto(payment);
        }
        paymentTransactionService.validatePaymentByTossWidget(request);

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
        paymentTransactionService.applyPaymentByTossWidget(response, request);
        return response;
    }

//


    private void handlePaymentRejected(String orderNumber, TossPaymentException e) {
        log.warn("[결제 승인 거절] 주문번호: {}, 오류코드: {}, 사유: {}",
                orderNumber, e.getErrorCode(), e.getMessage());
        try {
            // 명확한 승인 거절일 때만 실패 저장 및 예약 재고 해제
            paymentTransactionService.payFailTossPayment(orderNumber);
        } catch (RuntimeException saveException) {
            // 실패 저장 오류가 원래 PG 거절 예외를 덮어쓰지 않도록 보존
            e.addSuppressed(saveException);
            log.error("[결제 실패 반영 오류] 주문번호: {}", orderNumber, saveException);
        }
    }

    private Member validateMember(Long memberId) {
        return authRepository.findById(memberId)
                .orElseThrow(() -> new MemberException("존재하지 않는 회원입니다. 로그인을 다시 시도해주세요."));
    }
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