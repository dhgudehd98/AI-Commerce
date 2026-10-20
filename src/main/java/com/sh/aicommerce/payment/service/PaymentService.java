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
        try {
            return withPaymentLock("PREPARE", request.getOrderNumber(), () ->
                    transactionTemplate.execute(status ->
                            preparePay(memberId, variantId, optionId, request)
                    )
            );
        }catch (RuntimeException e) {
            log.error(
                    "[PAYMENT] stage=PREPARE event=FAILED orderNumber={}",
                    request.getOrderNumber(),
                    e
            );
            throw e;
        }
    }

    // 2. 락 획득 → 전달받은 작업 실행 → 락 해제
    private <T> T withPaymentLock(
            String stage,
            String orderNumber,
            Supplier<T> action
    ) {
        log.info("[PAYMENT] stage={} event=LOCK_REQUEST orderNumber={}", stage, orderNumber);
        String lockKey = PAYMENT_LOCK_KEY + orderNumber;
        String lockValue = UUID.randomUUID().toString();

        // 3. Redis에 키가 없을 때만 저장: 성공 true, 실패 false
        Boolean acquired = paymentLockTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, PAYMENT_LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.warn("[PAYMENT] stage={} event=LOCK_BUSY orderNumber={}", stage, orderNumber);
            throw new PaymentException("[결제/LOCK] 현재 처리 중인 결제 요청입니다.");
        }

        try {
            // TransactionTemplate 실행
            log.info("[PAYMENT] stage={} event=LOCK_ACQUIRED orderNumber={}", stage, orderNumber);
            T result = action.get();
            // 호출 대상의 트랜잭션 커밋이 완료된 이후에만 기록한다.
            log.info("[PAYMENT] stage={} event=COMMITTED orderNumber={}", stage, orderNumber);
            return result;
        } catch (RuntimeException e) {
            // 예외 전문에는 PG 응답/개인정보가 포함될 수 있으므로 기록하지 않는다.
            String reason = e instanceof PaymentException || e instanceof OrderException
                    || e instanceof PaymentAttemptException || e instanceof ProductException
                    || e instanceof MemberException || e instanceof CardException
                    || e instanceof AccountException || e instanceof OrderItemException
                    ? e.getMessage() : "INTERNAL_ERROR";
            log.error("[PAYMENT] stage={} event=FAILED orderNumber={} errorType={} outcome=CHECK_REQUIRED reason={}",
                    stage, orderNumber, e.getClass().getSimpleName(), reason);
            throw e;
        } finally {
            // 커밋 또는 롤백이 끝난 뒤 락 해제
            unLock(lockKey, lockValue);
        }
    }

    private void unLock(String lockKey, String lockValue) {

        // lock 삭제 명령어 -> 삭제되면 return 1
        String script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """;

        Long released = paymentLockTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                Collections.singletonList(lockKey),
                lockValue
        );
        log.info("[PAYMENT] stage=LOCK event=RELEASE_RESULT lockKey={} released={}",
                lockKey, Long.valueOf(1L).equals(released));
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
        log.info("[PAYMENT] stage=PREPARE_REUSE event=START orderNumber={} orderId={} memberId={}", order.getOrderNumber(), order.getId(), member.getId());

        // 주문 데이터 검증
        if (!order.getMember().getId().equals(member.getId()))
            throw new OrderException("[결제/PREPARE_REUSE] 본인 주문 정보가 아닙니다.");

        // 기존 주문 상품 검증
        OrderItem orderItem = order.getOrderItems()
                .stream()
                .findFirst()
                .orElseThrow(() -> new OrderItemException("[결제/PREPARE_REUSE] 주문 상품 정보가 존재하지 않습니다."));

        if (!orderItem.getProductOption().getId().equals(optionId))
            throw new OrderException("[결제/PREPARE_REUSE] 기존 주문과 요청 상품 옵션이 일치하지 않습니다.");

        if (!orderItem.getProductOption().getProductVariant().getId().equals(variantId))
            throw new OrderException("[결제/PREPARE_REUSE] 기존 주문과 요청 상품이 일치하지 않습니다.");

        // 결제 내역 검증
        Payment payment = paymentRepository.findPaymentByOrderId(order.getId())
                .orElseThrow(() -> new PaymentException("[결제/PREPARE_REUSE] 주문번호와 일치하는 결제 내역이 존재하지 않습니다."));

        if (payment.getPaymentMethod() != paymentRequestDto.getPaymentMethod())
            throw new PaymentException("[결제/PREPARE_REUSE] 기존에 존재하는 결제 방식과 현재 요청한 결제 방식이 다릅니다. 현재 요청 결제 방식 : " + paymentRequestDto.getPaymentMethod());

        // 결제 수단 검증 - 카드
        if (PaymentMethod.SAVED_CARD == paymentRequestDto.getPaymentMethod()) {
            // 기존에 결제 내역에 존재하는 CardId에 대한 값과 선택한 CardId에 대한값이 일치해야함.
            if (!payment.getSavedCardId().equals(paymentRequestDto.getSavedCardId()))
                throw new PaymentException("[결제/PREPARE_REUSE] 기존 주문과 결제 카드가 일치하지 않습니다.");

        } else if (PaymentMethod.SAVED_ACCOUNT == paymentRequestDto.getPaymentMethod()) {
            // 기존에 결제 내역에 존재하는 AccountId에 대한 값과 선택한 AccountId에 대한 값이 일치해야 함.
            if (!payment.getSavedAccountId().equals(paymentRequestDto.getSavedAccountId())) {
                throw new PaymentException("[결제/PREPARE_REUSE] 기준 주문과 결제 계좌가 일치하지 않습니다.");
            }
        }

        // 기존 결제 금액 정합성 검증
        if (!payment.getAmount().equals(order.getTotalPrice())) {
            throw new PaymentException("[결제/PREPARE_REUSE] 주문 금액과 결제 금액이 일치하지 않습니다.");
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
                "[PAYMENT] stage=PREPARE_REUSE event=REUSED orderNumber={}",
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
        log.info("[PAYMENT] stage=PREPARE_CREATE event=START orderNumber={} memberId={} variantId={} optionId={} method={}", paymentRequestDto.getOrderNumber(), member.getId(), variant.getId(), option.getId(), paymentRequestDto.getPaymentMethod());

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
                .orElseThrow(() -> new ProductException("[결제/PREPARE_CREATE] 현재 재고가 존재하지 않는 상품입니다."));
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


        log.info("[PAYMENT] stage=PREPARE_CREATE event=CREATED_PENDING_COMMIT orderNumber={} orderId={} paymentId={} attemptId={} amount={}", order.getOrderNumber(), order.getId(), payment.getId(), attempt.getId(), payment.getAmount());

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

        try {
            log.info("[PAYMENT] stage=DECIDE event=START orderNumber={} paymentId={} attemptId={}",
                    cardBillingPaymentRequestDto.getOrderNumber(), cardBillingPaymentRequestDto.getPaymentId(), cardBillingPaymentRequestDto.getPaymentAttemptId());
            PaymentDecision decision = withPaymentLock("APPROVAL_VALIDATE", cardBillingPaymentRequestDto.getOrderNumber(),
                    () -> paymentTxService.adjustPaymentAction(memberId, cardBillingPaymentRequestDto));
//

            // PaymentStatus에 대한 값이 READY || PROCESSING 이면 PG 재요청
            if (decision.action() == PaymentAction.PG_REQUEST) {
                return new PaymentCardResultDto(payCard(decision.cardRequestDto()), cardBillingPaymentRequestDto.getPaymentId());
            }

            if (decision.action() == PaymentAction.COMPLETED)
                return new PaymentCardResultDto(decision.completeDto());

            return new PaymentCardResultDto();
        } catch (RuntimeException e) {
            log.error("[결제 요청 decision 에러 발생 : ", e);
            throw e;
        }


    }

    // 실제 Toss API 요청
    public PgPaymentSuccessResponseDto payCard(PaymentBillingCardRequestDto paymentBillingCardRequestDto) {

        try {
            //PG사 외부 API 자동 결제 요청
            final TossPaymentSuccessResponseDto response =
                    client.cardBilling(paymentBillingCardRequestDto);

            // PG사 외부 API 자동 결제 요청 후, 응답 데이터 저장 전 락 획득 후 데이터 저장 시작
            return withPaymentLock("APPROVAL_APPLY", paymentBillingCardRequestDto.getOrderNumber(), () -> paymentTxService.validatePGResponseData(paymentBillingCardRequestDto, response));

        } catch (TossPaymentException e) {
            log.warn("[PAYMENT] stage=PG_REQUEST event=REJECTED orderNumber={} paymentId={} attemptId={} pgCode={}",
                    paymentBillingCardRequestDto.getOrderNumber(), paymentBillingCardRequestDto.getPaymentId(),
                    paymentBillingCardRequestDto.getAttemptId(), e.getErrorCode());

            try {
                // Toss에서 명백하게 에러에 대한 코드를 return 했기 떄문에 Payment, PaymentAttempt, Order에 대한 값들을 실패 값으로 설정
                withPaymentLock(
                        "FAILED",
                        paymentBillingCardRequestDto.getOrderNumber(),
                        () -> {
                            transactionTemplate.executeWithoutResult(status ->
                                    paymentTxService.applyPaymentRejected(
                                            paymentBillingCardRequestDto, e
                                    )
                            );

                            return null;
                        }
                );
            } catch (RuntimeException saveException) {
                // 실패 저장 오류가 원래 PG 거절 예외를 덮어쓰지 않도록 보존
                e.addSuppressed(saveException);
                log.error("[PAYMENT] stage=REJECTION_APPLY event=FAILED orderNumber={} errorType={}", paymentBillingCardRequestDto.getOrderNumber(), saveException.getClass().getSimpleName());
            }

            throw e;
        } catch (TossRestClientException e) {
            log.warn("[PAYMENT] stage=PG_REQUEST event=RESULT_UNKNOWN orderNumber={} paymentId={} attemptId={}",
                    paymentBillingCardRequestDto.getOrderNumber(), paymentBillingCardRequestDto.getPaymentId(),
                    paymentBillingCardRequestDto.getAttemptId());

            try{
                withPaymentLock(
                        "FAILED",
                        paymentBillingCardRequestDto.getOrderNumber(),
                        () -> {
                            transactionTemplate.executeWithoutResult(status ->
                                    paymentTxService.applyPaymentRejected(
                                            paymentBillingCardRequestDto, e
                                    )
                            );

                            return null;
                        }
                );
            } catch(RuntimeException saveException){
                e.addSuppressed(saveException);
                log.error("[PAYMENT] stage=REJECTION_APPLY event=FAILED orderNumber={} errorType={}", paymentBillingCardRequestDto.getOrderNumber(), saveException.getClass().getSimpleName());
            }
            throw e;

        } catch (JsonProcessingException e) {
            log.error("[PAYMENT] stage=PG_REQUEST event=RESULT_UNKNOWN orderNumber={} paymentId={} attemptId={} errorType={}", paymentBillingCardRequestDto.getOrderNumber(), paymentBillingCardRequestDto.getPaymentId(), paymentBillingCardRequestDto.getAttemptId(), e.getClass().getSimpleName());
            throw new PaymentException("[결제/APPROVAL] 결제 결과 확인이 필요합니다.");
        }
    }

    public TossPaymentSuccessResponseDto payByTossWidget(String orderNumber, String paymentKey, Integer amount) throws JsonProcessingException {
        log.info("[PAYMENT] stage=WIDGET_APPROVAL event=START orderNumber={} amount={}", orderNumber, amount);
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
            log.error("[PAYMENT] stage=WIDGET_APPROVAL event=RESULT_UNKNOWN orderNumber={} amount={} errorType={}", orderNumber, amount, e.getClass().getSimpleName());
            throw e;
        }

        // PG 승인 후 DB 반영 오류를 결제 거절로 처리하지 않는다.
        paymentTransactionService.applyPaymentByTossWidget(response, request);
        return response;
    }

    private void handlePaymentRejected(String orderNumber, TossPaymentException e) {
        log.warn("[PAYMENT] stage=WIDGET_APPROVAL event=REJECTED orderNumber={} pgCode={}",
                orderNumber, e.getErrorCode());
        try {
            // 명확한 승인 거절일 때만 실패 저장 및 예약 재고 해제
            paymentTransactionService.payFailTossPayment(orderNumber, e.getMessage());
        } catch (RuntimeException saveException) {
            // 실패 저장 오류가 원래 PG 거절 예외를 덮어쓰지 않도록 보존
            e.addSuppressed(saveException);
            log.error("[PAYMENT] stage=REJECTION_APPLY event=FAILED orderNumber={} errorType={}", orderNumber, saveException.getClass().getSimpleName());
        }
    }

    private Member validateMember(Long memberId) {
        return authRepository.findById(memberId)
                .orElseThrow(() -> new MemberException("[결제/MEMBER_VALIDATE] 존재하지 않는 회원입니다. 로그인을 다시 시도해주세요."));
    }
    private ProductOption validateProduct(Long variantId, Long optionId) {
        ProductOption option = productOptionRepository.findWithProductVariantByVariantIdAndOptionId(variantId, optionId).orElseThrow(() -> new ProductException("[결제/PRODUCT_VALIDATE] 잘못된 상품 정보입니다. 상품정보를 다시 확인해주세요."));

        int availableQuantity = option.getInventories().stream()
                .mapToInt(inventory -> inventory.getAvailableQuantity())
                .sum();

        if(availableQuantity <= 0) throw new ProductException("[결제/PRODUCT_VALIDATE] 상품 재고가 존재하지 않습니다.");

        return option;
    }
    // 등록된 카드

    private Card validateCard(Long memberId, Long cardId) {
        Card card = cardRepository.findByIdAndMemberId(cardId, memberId).orElseThrow(() -> new CardException("[결제/CARD_VALIDATE] 등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

        // 여기서 추가적으로 카드적으로 검증할거 있는지 체크 ? 뭐 만료날짜 같은거나
        return card;
    }

    private Account validateAccount(Long memberId, Long accountId) {
        Account account = accountRepository.findByIdAndMemberId(accountId, memberId).orElseThrow(() -> new AccountException("[결제/ACCOUNT_VALIDATE] 등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

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
                .orElseThrow(() -> new PaymentException("[결제/PAYMENT_READ] PaymentKey에 일치하는 Payment 내역이 존재하지 않습니다."));
    }

    public Payment findPaymentByOrderIdWithLock(Long orderId) {
        return paymentRepository.findPaymentByOrderIdWithLock(orderId)
                .orElseThrow(() -> new PaymentException("[결제/PAYMENT_READ] 주문 번호에 해당하는 지불 내역이 존재하지 않습니다. 주문 번호 : " + orderId));
    }

    public Payment findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentException("[결제/PAYMENT_READ] 주문 번호에 일치하는 결제 내역이 존재하지 않습니다. 주문 번호 : " + orderId));
    }
}
