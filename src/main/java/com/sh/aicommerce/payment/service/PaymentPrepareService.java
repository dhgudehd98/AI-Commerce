package com.sh.aicommerce.payment.service;


import com.sh.aicommerce.account.repository.AccountRepository;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.account.AccountException;
import com.sh.aicommerce.common.exception.card.CardException;
import com.sh.aicommerce.common.exception.member.MemberException;
import com.sh.aicommerce.common.exception.product.ProductException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentMethod;
import com.sh.aicommerce.order.dto.Receiver;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PreparePaymentResultDto;
import com.sh.aicommerce.productOption.repository.ProductOptionRepository;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentPrepareService {

    private final AuthRepository authRepository;
    private final ProductOptionRepository productOptionRepository;
    private final OrderRepository orderRepository;
    private final ProductInventoryRepository inventoryRepository;

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;


    /**
     * 결제 준비 Flow
     * @param memberId
     * @param variantId
     * @param optionId
     * @param paymentRequestDto
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

            //! 현재는 비관적 락으로 설정되어 있지만 나중에는 Redis 분산락으로 변경
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

            return new PreparePaymentResultDto(
                    order.getId(),

                    payment.getId(),
                    payment.getAmount(),
                    payment.getPaymentMethod(),
                    paymentRequestDto.getGeneralPayment(),
                    payment.getCardCompany(),
                    paymentRequestDto.getSavedCardId(),
                    paymentRequestDto.getSavedAccountId()
                    );
        } catch (Exception e) {
            log.error("[결제 준비] : 에러 발생 에러메세지 : {}", e.getMessage());
            throw e;
        }

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

    private Card validateCard(Long memberId, Long cardId) {
        Card card = cardRepository.findByIdAndMemberId(cardId, memberId).orElseThrow(() -> new CardException("등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

        // 여기서 추가적으로 카드적으로 검증할거 있는지 체크 ? 뭐 만료날짜 같은거나
        return card;
    }

    private Account validateAccount(Long memberId, Long accountId) {
        Account account = accountRepository.findByIdAndMemberId(accountId, memberId).orElseThrow(() -> new AccountException("등록되어 있는 카드가 존재하지 않습니다. 카드 먼저 등록해주세요."));

        return account;
    }

}
