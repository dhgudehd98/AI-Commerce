package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sh.aicommerce.account.repository.AccountRepository;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.account.AccountException;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.orderItem.OrderItemException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.paymentAttempt.PaymentAttemptException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.*;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.payment.dto.request.CardBillingPaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.dto.response.PaymentCardResultDto;
import com.sh.aicommerce.payment.dto.response.PgPaymentSuccessResponseDto;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

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

    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate paymentLockTemplate;
    private static final String PAYMENT_LOCK_KEY = "lock:payment:orderNumber:";
    private static final Duration PAYMENT_LOCK_TTL = Duration.ofSeconds(60); // 락 유지시간 60초


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
     * - 상품 기본 금액
     * - 상품 옵션별 추가 금액
     * - 상품 가격에 따른 배송비
     * - 쿠폰 금액
     * - 포인트 사용 금액
     * -> 총 결제 금액 = 상품 금액(기본금액 + 추가 금액) + 배송비 - (쿠폰 금액 + 포인트 사용금액)
     * <p>
     * 3. 사용자가 결제 선택 수단에 따른 결제 방식 선택
     * a. TossPayments 사용
     * b. saved_account
     * c. saved_card
     * => TossPayment 제외 saved에 대한 정보는 등록되어 있는 카드나 등록되어 있는 계좌를 사용하여 간편 결제
     */


    public PreparePaymentResultDto getPaymentLockByOrderNumber(
            Long memberId,
            Long variantId,
            Long optionId,
            PaymentRequestDto request
    ) {
        return withPaymentLock(request.getOrderNumber(), () ->
                transactionTemplate.execute(status ->
                        preparePay(memberId, variantId, optionId, request)
                )
        );
    }

    // 2. 락 획득 → 전달받은 작업 실행 → 락 해제
    private <T> T withPaymentLock(
            String orderNumber,
            Supplier<T> action
    ) {
        log.info("[결제 요청전] 주문번호를 통해 분산 락 획득 요청 주문번호 : {}", orderNumber);
        String lockKey = PAYMENT_LOCK_KEY + orderNumber;
        String lockValue = UUID.randomUUID().toString();

        // 3. Redis에 키가 없을 때만 저장: 성공 true, 실패 false
        Boolean acquired = paymentLockTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, PAYMENT_LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            throw new PaymentException("현재 처리 중인 결제 요청입니다.");
        }

        try {
            // TransactionTemplate 실행
            log.info("[결제 요청 전 ] 주문번호를 통해 분산 락 획득  주문번호 : {}", orderNumber);
            return action.get();
        } finally {
            // 커밋 또는 롤백이 끝난 뒤 락 해제
            unLock(lockKey, lockValue);
        }
    }

    // 4. 자신이 획득한 락만 삭제


    private void unLock(String lockKey, String lockValue) {

        // lock 삭제 명령어 -> 삭제되면 return 1
        String script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """;

        paymentLockTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
    }

    public PreparePaymentResultDto preparePay(Long memberId, Long variantId, Long optionId, PaymentRequestDto paymentRequestDto) {

        Member member = validateMember(memberId);
        Optional<Orders> existsOrder = orderRepository.findOrderByOrderNumber(paymentRequestDto.getOrderNumber());

        if (existsOrder.isPresent()) {
            Orders order = existsOrder.get();

            // 기존에 주문이 있는 경우, 결제 요청 멱등성 처리를 하기 위해 데이터 검증
            return existsOrderValidate(member, variantId, optionId, order, paymentRequestDto);
        }

        // 새로 결제를 요청하는 경우에만 ProductOption , Variant에 대한 값 검증
        ProductOption option = validateProduct(variantId, optionId);
        ProductVariant variant = option.getProductVariant();
        return createPaymentAndOrder(member, variant, option, paymentRequestDto);
    }

    /**
     * 기존에 존재하는 주문 데이터가 있을 떄 검증해야되는 요소
     * - Orders, OrderItem, Payment, PaymentAttempt, Delivery
     * <p>
     * Orders
     * - 존재하는 주문에 대한 내역과 현재 로그인해서 결제 신청한 사람이 동일한 사람인지 확인
     * - 주문 상태에 대한 값은 어떻게 확인을 해야하지 ?
     * OrderItem
     * -
     * Payment
     * - 존재하는 결제 내역에 결제 요청 금액과 Client 쪽에서 전달받은 합산 금액이 동일한지 확인
     * - 존재하는 결제 내역에 결제 방식과 Client 쪽에서 전달받은 결제 방식이 동일한지 확인
     * -> 여기서 만약에 두번째 요청에서 사용자가 결제 방식을 변경하게 되면 어떻게 처리해야하는가 ?
     * - 카드로 결제 했다는 가정하에 -> 카드가 로그인한 사람의 것인지 확인
     * <p>
     * PaymentAttempt
     * - Payment에 대한 값이 존재하는지 확인하고, 승인 요청 상태에 대한 값을 확인을 해야하나 ?
     */
    private PreparePaymentResultDto existsOrderValidate(Member member, Long variantId, Long optionId, Orders order, PaymentRequestDto paymentRequestDto) {
        log.info("[결제 요청] 기존에 존재하는 주문 정보가 있는 경우에 데이터 검증 시작");

        // 주문 데이터 검증
        if (!order.getMember().getId().equals(member.getId()))
            throw new OrderException("본인 주문 정보가 아닙니다.");

        // 기존 주문 상품 검증
        OrderItem orderItem = order.getOrderItems()
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderItemException("주문 상품 정보가 존재하지 않습니다."));

        if (!orderItem.getProductOption().getId().equals(optionId))
            throw new OrderException("기존 주문과 요청 상품 옵션이 일치하지 않습니다.");

        if (!orderItem.getProductOption().getProductVariant().getId().equals(variantId))
            throw new OrderException("기존 주문과 요청 상품이 일치하지 않습니다.");

        // 결제 내역 검증
        Payment payment = paymentRepository.findPaymentByOrderId(order.getId())
                .orElseThrow(() -> new PaymentException("주문번호와 일치하는 결제 내역이 존재하지 않습니다."));

        if (payment.getPaymentMethod() != paymentRequestDto.getPaymentMethod())
            throw new PaymentException("기존에 존재하는 결제 방식과 현재 요청한 결제 방식이 다릅니다. 현재 요청 결제 방식 : " + paymentRequestDto.getPaymentMethod());

        // 결제 수단 검증 - 카드
        if (PaymentMethod.SAVED_CARD == paymentRequestDto.getPaymentMethod()) {
            // 기존에 결제 내역에 존재하는 CardId에 대한 값과 선택한 CardId에 대한값이 일치해야함.
            if (!payment.getSavedCardId().equals(paymentRequestDto.getSavedCardId()))
                throw new PaymentException("기존 주문과 결제 카드가 일치하지 않습니다.");

        } else if (PaymentMethod.SAVED_ACCOUNT == paymentRequestDto.getPaymentMethod()) {
            // 기존에 결제 내역에 존재하는 AccountId에 대한 값과 선택한 AccountId에 대한 값이 일치해야 함.
            if (!payment.getSavedAccountId().equals(paymentRequestDto.getSavedAccountId())) {
                throw new PaymentException("기준 주문과 결제 계좌가 일치하지 않습니다.");
            }
        }

        // 기존 결제 금액 정합성 검증
        if (!payment.getAmount().equals(order.getTotalPrice())) {
            throw new PaymentException("주문 금액과 결제 금액이 일치하지 않습니다.");
        }

        // 7. 기존 PaymentAttempt 조회
        PaymentAttempt attempt = attemptRepository
                .findFirstByPaymentIdOrderByIdDesc(payment.getId())
                .orElseThrow(() ->
                        new PaymentAttemptException(
                                "결제 승인 시도 정보가 존재하지 않습니다."
                        )
                );

        // 9. 기존 결제 준비 결과 반환
        log.info(
                "[결제 준비 멱등성] 기존 결제 준비 결과 반환 주문번호 : {}",
                order.getOrderNumber()
        );

        return new PreparePaymentResultDto(
                order.getId(),
                order.getOrderNumber(),
                orderItem.getProductOption()
                        .getProductVariant()
                        .getVariantName(),
                payment.getId(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getCardCompany(),
                payment.getSavedCardId(),
                payment.getSavedAccountId(),
                order.getMember().getCustomerKey(),
                attempt.getId()
        );

    }

    private PreparePaymentResultDto createPaymentAndOrder(Member member, ProductVariant variant, ProductOption option, PaymentRequestDto paymentRequestDto) {
        log.info("[결제 요청] 회원 번호 : {}, 상품번호 : {}, 옵션번호 : {}, 결제 방법 : {}", member.getId(), variant.getId(), option.getId(), paymentRequestDto.getPaymentMethod());

        // 금액 검증 = 상품 기본 금액 + 옵션별 추가 금액 + 배송비 - (쿠폰 사용 금액 , 사용 포인트) -> 현재 쿠폰, 사용 포인트에 대한 값은 구현되어 있지 않음
        Integer productTotalPrice = variant.getPrice() + option.getAdditionalPrice();
        Integer totalPaymentPrice = productTotalPrice + setDeliveryPrice(productTotalPrice);

        // 사용자 결제 수단 선택
        PaymentMethod paymentMethod = paymentRequestDto.getPaymentMethod();
        Card card = new Card();
        Account account = new Account();

        if (paymentMethod.equals(PaymentMethod.SAVED_CARD))
            card = validateCard(member.getId(), paymentRequestDto.getSavedCardId());
        if (paymentMethod.equals(PaymentMethod.SAVED_ACCOUNT))
            account = validateAccount(member.getId(), paymentRequestDto.getSavedAccountId());


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
                member.getCustomerKey(),
                attempt.getId()
        );
    }


    public PaymentCardResultDto adjustPaymentResult(Long memberId, CardBillingPaymentRequestDto cardBillingPaymentRequestDto) {

        log.info("[결제 요청 전 결제 내역 상태 검증] 주문번호 : {}, 결제 번호 : {}", cardBillingPaymentRequestDto, cardBillingPaymentRequestDto.getPaymentId());
        PaymentDecision decision = withPaymentLock(cardBillingPaymentRequestDto.getOrderNumber(),
                () -> paymentTxService.adjustPaymentAction(memberId, cardBillingPaymentRequestDto));
//

        // PaymentStatus에 대한 값이 READY || PROCESSING 이면 PG 재요청
        if (decision.action() == PaymentAction.PG_REQUEST) {
            return new PaymentCardResultDto(payCard(decision.cardRequestDto()),cardBillingPaymentRequestDto.getPaymentId());
        }

        if(decision.action() == PaymentAction.COMPLETED)
            return new PaymentCardResultDto(decision.completeDto());

        return new PaymentCardResultDto();

    }

    // 실제 Toss API 요청
    public PgPaymentSuccessResponseDto payCard(PaymentBillingCardRequestDto paymentBillingCardRequestDto) {

        try {
            //PG사 외부 API 자동 결제 요청
            final TossPaymentSuccessResponseDto response =
                    client.cardBilling(paymentBillingCardRequestDto);

            // PG사 외부 API 자동 결제 요청 후, 응답 데이터 저장 전 락 획득 후 데이터 저장 시작
            return withPaymentLock(paymentBillingCardRequestDto.getOrderNumber(), () -> paymentTxService.validatePGResponseData(paymentBillingCardRequestDto, response));

        } catch (TossPaymentException | TossRestClientException e) {
            throw e;
        } catch (JsonProcessingException e) {
            log.error("[결제 응답 처리 실패] 주문번호: {}", paymentBillingCardRequestDto.getOrderNumber(), e);
            throw new PaymentException("결제 결과 확인이 필요합니다.");
        }
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