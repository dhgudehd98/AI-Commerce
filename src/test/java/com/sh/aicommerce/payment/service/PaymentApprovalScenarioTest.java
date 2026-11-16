package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.auth.repository.AuthRepository;
import com.sh.aicommerce.card.repository.CardRepository;
import com.sh.aicommerce.common.exception.order.OrderException;
import com.sh.aicommerce.common.exception.payment.PaymentException;
import com.sh.aicommerce.common.exception.wms.InventoryException;
import com.sh.aicommerce.common.exception.toss.TossPaymentException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.order.OrderStatus;
import com.sh.aicommerce.enums.payment.*;
import com.sh.aicommerce.order.orderRepository.OrderRepository;
import com.sh.aicommerce.orderItem.repository.OrderItemRepository;
import com.sh.aicommerce.payment.dto.request.CardBillingPaymentRequestDto;
import com.sh.aicommerce.payment.dto.request.PaymentBillingCardRequestDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentAttempt.repository.PaymentAttemptRepository;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import com.sh.aicommerce.paymentLedger.service.PaymentLedgerService;
import com.sh.aicommerce.toss.repository.TossPaymentLogRepository;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.wms.inventory.repository.ProductInventoryRepository;
import com.sh.aicommerce.wms.inventory.service.ProductInventoryService;
import com.sh.aicommerce.wms.outbound.service.OutboundService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class PaymentApprovalScenarioTest {
    private final AuthRepository members = mock(AuthRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentAttemptRepository attempts = mock(PaymentAttemptRepository.class);
    private final CardRepository cards = mock(CardRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final TossPaymentLogRepository logs = mock(TossPaymentLogRepository.class);
    private final PaymentLedgerRepository ledgers = mock(PaymentLedgerRepository.class);
    private final PaymentLedgerService ledgerService = mock(PaymentLedgerService.class);
    private final OutboundService outbound = mock(OutboundService.class);
    private final ProductInventoryRepository inventories = mock(ProductInventoryRepository.class);
    private final ProductInventoryService inventoryService = new ProductInventoryService(
            null, inventories, null, null, null, null);
    private final PaymentTxService service = new PaymentTxService(members, payments, attempts,
            cards, items, orders, logs, ledgers, ledgerService, outbound, inventoryService);
    private final Payment payment = new Payment();
    private final Orders order = new Orders();
    private final Member member = new Member();
    private final Card card = new Card();
    private PaymentAttempt attempt;

    private void fixture() {
        set(member, "id", 1L); set(member, "customerKey", "test-customer");
        set(order, "id", 10L); set(order, "orderNumber", "PAY-UNIT");
        set(order, "member", member); set(order, "status", OrderStatus.PENDING_PAYMENT);
        set(order, "totalPrice", 10000);
        set(payment, "id", 20L); set(payment, "order", order);
        set(payment, "status", PaymentStatus.READY); set(payment, "amount", 10000);
        set(payment, "paymentMethod", PaymentMethod.SAVED_CARD); set(payment, "savedCardId", 1L);
        set(card, "id", 1L); set(card, "member", member); set(card, "active", true);
        set(card, "billingKey", "test-billing-key");
        attempt = PaymentAttempt.createPaymentAttempt(payment, "PAY-UNIT");
        set(attempt, "id", 30L);
        when(members.findById(1L)).thenReturn(Optional.of(member));
        when(payments.findPaymentByIdWithOrderForUpdate(20L, "PAY-UNIT")).thenReturn(Optional.of(payment));
        when(orders.findOrderByOrderNumberForUpdate("PAY-UNIT")).thenReturn(Optional.of(order));
        when(items.findByOrderNumber("PAY-UNIT")).thenReturn(List.of("test-product"));
        when(cards.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.of(card));
        when(attempts.findPaymentAttemptByAttemptIdForUpdate(30L)).thenReturn(Optional.of(attempt));
    }

    private CardBillingPaymentRequestDto request() {
        CardBillingPaymentRequestDto request = new CardBillingPaymentRequestDto();
        set(request, "orderNumber", "PAY-UNIT"); set(request, "paymentId", 20L);
        set(request, "paymentAttemptId", 30L); set(request, "savedCardId", 1L);
        return request;
    }

    @Test
    void 타_회원의_주문은_승인_준비_전에_차단한다() {
        fixture(); Member other = new Member(); set(other, "id", 2L); set(order, "member", other);
        assertThrows(OrderException.class, () -> service.validatePayment(1L, request()));
        assertEquals(PaymentStatus.READY, payment.getStatus());
        verifyNoInteractions(outbound, ledgerService, logs);
    }

    @Test
    void 타_회원의_카드는_승인_준비_전에_차단한다() {
        fixture(); when(cards.findByIdAndMemberId(1L, 1L)).thenReturn(Optional.empty());
        assertThrows(PaymentException.class, () -> service.validatePayment(1L, request()));
        assertEquals(PaymentAttemptStatus.READY, attempt.getStatus());
        verifyNoInteractions(outbound, ledgerService, logs);
    }

    @Test
    void 주문과_결제_금액이_다르면_승인_준비_전에_차단한다() {
        fixture(); set(payment, "amount", 9999);
        assertThrows(PaymentException.class, () -> service.validatePayment(1L, request()));
        assertEquals(PaymentStatus.READY, payment.getStatus());
        verifyNoInteractions(outbound, ledgerService, logs);
    }

    @Test
    void 재고가_부족하면_예약이_누적되거나_음수가_되지_않는다() {
        ProductInventory inventory = ProductInventory.create(null, null, 1, 1);
        inventory.reserve();
        assertThrows(InventoryException.class, inventory::reserve);
        assertEquals(1, inventory.getOnHandQuantity());
        assertEquals(1, inventory.getReservedQuantity());
        assertEquals(0, inventory.getAvailableQuantity());
    }

    @Test
    void 명시적_PG_거절은_예약을_한번만_해제하고_원장과_출고를_생성하지_않는다() {
        fixture(); TossPaymentException rejection = rejectionFromMockPg();
        ProductInventory inventory = attachInventory();
        PaymentBillingCardRequestDto request = new PaymentBillingCardRequestDto(20L, 10L, "PAY-UNIT", 30L);
        service.applyPaymentRejected(request, rejection);
        service.applyPaymentRejected(request, rejection);
        assertEquals(PaymentStatus.PAYMENT_REJECTED, payment.getStatus());
        assertEquals(OrderStatus.PAYMENT_REJECTED, order.getStatus());
        assertEquals(PaymentAttemptStatus.PAYMENT_REJECTED, attempt.getStatus());
        assertEquals(0, inventory.getReservedQuantity());
        assertEquals(2, inventory.getOnHandQuantity());
        verify(inventories, times(1)).findProductInventoryByOptionIdForUpdate(40L);
        verifyNoInteractions(outbound, ledgerService, logs);
    }

    @Test
    void 명시적_PG_거절_코드를_승인_시도에_보존한다() {
        fixture(); TossPaymentException rejection = rejectionFromMockPg(); attachInventory();
        service.applyPaymentRejected(new PaymentBillingCardRequestDto(20L, 10L, "PAY-UNIT", 30L), rejection);
        assertEquals("REJECT_CARD_PAYMENT", attempt.getFailureCode());
    }

    private ProductInventory attachInventory() {
        ProductOption option = new ProductOption(); set(option, "id", 40L);
        OrderItem item = new OrderItem(); set(item, "productOption", option); set(item, "quantity", 1);
        set(order, "orderItems", List.of(item));
        ProductInventory inventory = ProductInventory.create(option, null, 2, 1); inventory.reserve();
        when(inventories.findProductInventoryByOptionIdForUpdate(40L)).thenReturn(Optional.of(inventory));
        return inventory;
    }

    private TossPaymentException rejectionFromMockPg() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(template);
        TossPaymentClient client = new TossPaymentClient(template, new RestTemplate(), new ObjectMapper());
        set(client, "tossBillingSecretKey", "test-unit-key");
        PaymentBillingCardRequestDto request = mock(PaymentBillingCardRequestDto.class);
        when(request.getCardBillingKey()).thenReturn("test-billing-key");
        when(request.getAmount()).thenReturn(10000); when(request.getOrderNumber()).thenReturn("PAY-UNIT");
        when(request.getIdempotencyKey()).thenReturn("test-unit-idempotency");
        server.expect(requestTo("https://api.tosspayments.com/v1/billing/test-billing-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"test rejection\"}"));
        TossPaymentException rejection = assertThrows(TossPaymentException.class, () -> client.cardBilling(request));
        assertEquals("REJECT_CARD_PAYMENT", rejection.getErrorCode()); server.verify();
        return rejection;
    }

    private static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
