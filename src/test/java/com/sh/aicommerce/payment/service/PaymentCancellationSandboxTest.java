package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.toss.*;
import com.sh.aicommerce.order.dto.OrderCancelRequestDto;
import com.sh.aicommerce.order.service.OrderService;
import com.sh.aicommerce.orderCancellation.service.*;
import com.sh.aicommerce.payment.dto.request.*;
import com.sh.aicommerce.payment.dto.response.PreparePaymentResultDto;
import com.sh.aicommerce.paymentCancellation.service.*;
import com.sh.aicommerce.paymentCancellationRecovery.service.PaymentCancellationRecoveryService;
import com.sh.aicommerce.toss.dto.response.TossPaymentCancelResponseDto;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Opt-in only: creates real test-PG approvals/cancellations and retains evidence/data.
 * Temporarily disable the hardcoded post-cancel timeout before execution and restore it afterwards.
 */
@EnabledIfEnvironmentVariable(named = "PAYMENT_CANCEL_SANDBOX", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.show-sql=false",
        "logging.level.root=OFF", "logging.level.org.hibernate.type.descriptor.sql=OFF",
        "logging.level.org.elasticsearch.client=OFF"})
@ContextConfiguration(initializers = PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentCancellationSandboxTest {
    @Autowired PaymentService payments;
    @Autowired OrderService orders;
    @Autowired OrderCancellationTransactionService preparation;
    @Autowired PaymentCancellationService cancellation;
    @Autowired PaymentCancellationRecoveryService recovery;
    @Autowired OrderCancellationInventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
    @SpyBean TossPaymentClient client;
    @SpyBean PaymentCancellationTxService cancelTx;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static final Path EVIDENCE = Path.of("docs/payment-cancellation-evidence.jsonl");

    @BeforeAll static void requireNormalCancelClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/sh/aicommerce/toss/service/TossPaymentClient.java"));
        assertFalse(java.util.regex.Pattern.compile("(?m)^\\s*if \\(response\\.getBody\\(\\) != null\\) \\{").matcher(source).find(),
                "Disable the hardcoded post-cancel timeout before opting into sandbox scenarios; restore it afterwards.");
    }

    public static class TestEnvironment implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        public void initialize(ConfigurableApplicationContext context) {
            try {
                Map<String,Object> values = new HashMap<>();
                for (String line : Files.readAllLines(Path.of(".env"))) {
                    line = line.trim(); if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                    String[] kv = line.replaceFirst("^export ", "").split("=", 2);
                    String value = kv[1].trim();
                    if (value.length() > 1 && (value.startsWith("\"") && value.endsWith("\"")
                            || value.startsWith("'") && value.endsWith("'"))) value = value.substring(1,value.length()-1);
                    values.put(kv[0].trim(),value);
                }
                if (!String.valueOf(values.get("TOSS_PAYMENT_BILLING_SECRET_KEY")).startsWith("test_"))
                    throw new IllegalStateException("Sandbox Billing key required");
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("sandbox-env", values));
            } catch (Exception e) { throw new IllegalStateException("Cannot load sandbox settings", e); }
        }
    }

    @TestConfiguration
    static class ManualExecution {
        @Bean static BeanFactoryPostProcessor removeScheduledExecution() {
            return factory -> {
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) factory;
                String name = "org.springframework.context.annotation.internalScheduledAnnotationProcessor";
                if (registry.containsBeanDefinition(name)) registry.removeBeanDefinition(name);
            };
        }
    }

    record Target(String scenario, String orderNumber, long orderId, long paymentId, long cancelId, long pcId) {}

    private Target target(String scenario, boolean returnRequired) throws Exception {
        String number = scenario+"-"+UUID.randomUUID().toString().replace("-", "");
        PaymentRequestDto request = mapper.readValue("""
                {"variantId":1,"optionId":1,"usedPoint":0,"paymentMethod":"SAVED_CARD","savedCardId":1,
                 "receiver":{"name":"테스트","email":"test@example.com","phone":"01000000000",
                 "zipCode":"00000","address":"테스트","addressDetail":"테스트","receiverMethod":"DOOR"}}
                """, PaymentRequestDto.class);
        org.springframework.test.util.ReflectionTestUtils.setField(request, "orderNumber", number);
        PreparePaymentResultDto prepared = payments.getPaymentLockByOrderNumber(1L,1L,1L,request);
        CardBillingPaymentRequestDto approval = mapper.readValue(mapper.writeValueAsString(Map.of(
                "orderNumber",number,"paymentId",prepared.getPaymentId(),
                "paymentAttemptId",prepared.getAttemptId(),"savedCardId",1)),CardBillingPaymentRequestDto.class);
        payments.adjustPaymentResult(1L, approval);
        assertEquals("PAID", jdbc.queryForObject("select status from payment where payment_id=?",String.class,prepared.getPaymentId()));
        if (returnRequired) jdbc.update("update outbound set out_bound_status='PICKING' where order_id=?",prepared.getOrderId());
        orders.cancel(1L,prepared.getOrderId(),new OrderCancelRequestDto("sandbox "+scenario));
        long cancelId = jdbc.queryForObject("select order_cancel_id from order_cancellation where order_id=?",Long.class,prepared.getOrderId());
        preparation.readyToPaymentCancellation(cancelId);
        long pcId = jdbc.queryForObject("select id from payment_cancellation where order_cancellation_id=?",Long.class,cancelId);
        Target target = new Target(scenario,number,prepared.getOrderId(),prepared.getPaymentId(),cancelId,pcId);
        evidence(target,"PREPARED");
        clearInvocations(client);
        return target;
    }

    private String pc(Target t) { return jdbc.queryForObject("select status from payment_cancellation where id=?",String.class,t.pcId); }
    private int countLedger(Target t) { return jdbc.queryForObject("select count(*) from payment_ledger where payment_id=? and type='CANCELLATION'",Integer.class,t.paymentId); }
    private int reserved() {return jdbc.queryForObject("select reserved_quantity from product_inventory where product_inventory_id=1",Integer.class);}
    private void evidence(Target t,String stage) throws Exception {
        Map<String,Object> e = new LinkedHashMap<>();e.put("scenario",t.scenario);e.put("stage",stage);e.put("time",java.time.OffsetDateTime.now().toString());e.put("target",t);
        e.put("state",jdbc.queryForMap("select o.status order_status,p.status payment_status,c.cancel_status,c.item_return_status,c.inventory_status,pc.status pg_cancel_status,pc.fail_code,pc.fail_message,pc.transaction_key from orders o join payment p on p.order_id=o.order_id join order_cancellation c on c.order_id=o.order_id join payment_cancellation pc on pc.order_cancellation_id=c.order_cancel_id where o.order_id=?",t.orderId));
        e.put("inventory",jdbc.queryForMap("select on_hand_quantity,reserved_quantity from product_inventory where product_inventory_id=1"));
        e.put("ledger",jdbc.queryForList("select id,type,amount,currency,transaction_key,occurred_at from payment_ledger where payment_id=? order by id",t.paymentId));
        e.put("movements",jdbc.queryForList("select stock_movement_status,quantity from stock_movement where reference_type='ORDER_CANCELLATION' and reference_id=?",t.cancelId));
        Files.writeString(EVIDENCE,mapper.writeValueAsString(e)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private void success(Target t) throws Exception {
        evidence(t,"PG_RESULT");assertEquals("SUCCESS",pc(t));assertEquals(1,countLedger(t));
        assertEquals("CANCELED",jdbc.queryForObject("select status from payment where payment_id=?",String.class,t.paymentId));
        verify(client,times(1)).cancel(any());
    }
    private void followUp(Target t) throws Exception {
        inventory.applyInventory(3L,t.cancelId);evidence(t,"FOLLOW_UP");
        assertEquals("COMPLETED",jdbc.queryForObject("select inventory_status from order_cancellation where order_cancel_id=?",String.class,t.cancelId));
        assertEquals("CANCELED",jdbc.queryForObject("select status from orders where order_id=?",String.class,t.orderId));
    }

    @Test @Order(1) void CAN01_normal() throws Exception {
        Target t=target("CAN01",false);int before=reserved();
        cancellation.cancelPayment(t.pcId);success(t);assertEquals(before,reserved());
        followUp(t);assertEquals(before-1,reserved());
    }
    @Test @Order(2) void CAN02_returnRequired() throws Exception {
        Target t=target("CAN02",true);cancellation.cancelPayment(t.pcId);success(t);int before=reserved();
        assertThrows(RuntimeException.class,()->inventory.applyInventory(3L,t.cancelId));assertEquals(before,reserved());
        inventory.confirmReturn(3L,t.cancelId);assertEquals(before,reserved());followUp(t);assertEquals(before-1,reserved());
    }
    @Test @Order(3) void CAN03_concurrentAndRepeated() throws Exception {
        Target t=target("CAN03",false);String key=jdbc.queryForObject("select idempotency_key from payment_cancellation where id=?",String.class,t.pcId);
        CyclicBarrier barrier=new CyclicBarrier(2);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(()->{try{barrier.await();cancellation.process(t.pcId);}catch(Exception e){throw new CompletionException(e);}}));
            for(Future<?> f:futures)f.get(90,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        cancellation.process(t.pcId);success(t);int before=reserved();followUp(t);inventory.applyInventory(3L,t.cancelId);
        assertEquals(before-1,reserved());assertEquals(key,jdbc.queryForObject("select idempotency_key from payment_cancellation where id=?",String.class,t.pcId));
        orders.cancel(1L,t.orderId,new OrderCancelRequestDto("duplicate"));
        assertEquals(1,jdbc.queryForObject("select count(*) from order_cancellation where order_id=?",Integer.class,t.orderId));
        evidence(t,"REPLAYED");
    }
    @Test @Order(2) void CAN02_deductedStockRestoration() throws Exception {
        Target t=target("CAN02-RESTORE",true);
        int beforeReserved=reserved();
        int beforeOnHand=jdbc.queryForObject("select on_hand_quantity from product_inventory where product_inventory_id=1",Integer.class);
        // Only this test's newly created order: simulate a committed outbound deduction and its audit row.
        transaction.executeWithoutResult(status->{
            jdbc.update("update product_inventory set on_hand_quantity=on_hand_quantity-1,reserved_quantity=reserved_quantity-1 where product_inventory_id=1");
            jdbc.update("insert into stock_movement(product_inventory_id,stock_movement_status,reference_type,reference_id,quantity,before_quantity,after_quantity,created_at) values(1,'OUTBOUND','ORDER',?,1,?,?,CURRENT_TIMESTAMP)",t.orderId,beforeOnHand,beforeOnHand-1);
        });
        evidence(t,"ISOLATED_DEDUCTION_FIXTURE");
        cancellation.cancelPayment(t.pcId);success(t);
        assertThrows(RuntimeException.class,()->inventory.applyInventory(3L,t.cancelId));
        inventory.confirmReturn(3L,t.cancelId);followUp(t);inventory.applyInventory(3L,t.cancelId);
        assertEquals(beforeOnHand,jdbc.queryForObject("select on_hand_quantity from product_inventory where product_inventory_id=1",Integer.class));
        assertEquals(beforeReserved-1,reserved());
        assertEquals(1,jdbc.queryForObject("select count(*) from stock_movement where reference_type='ORDER_CANCELLATION' and reference_id=? and stock_movement_status='INBOUND'",Integer.class,t.cancelId));
        evidence(t,"RESTORED_ONCE");
    }
    @Test @Order(4) void CAN04_timeoutAfterRealCancellation() throws Exception {
        Target t=target("CAN04",false);
        doAnswer(call->{call.callRealMethod();throw new TossRestClientException("sandbox post-cancel timeout",new java.net.SocketTimeoutException("sandbox"));}).when(client).cancel(any());
        cancellation.cancelPayment(t.pcId);evidence(t,"UNKNOWN");assertEquals("UNKNOWN",pc(t));assertEquals(0,countLedger(t));
        recovery.recover(t.pcId);success(t);followUp(t);
    }
    @Test @Order(5) void CAN05_internalApplyFailure() throws Exception {
        Target t=target("CAN05",false);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("sandbox apply failure")).when(cancelTx).cancel(any(TossPaymentCancelResponseDto.class),any());
        cancellation.cancelPayment(t.pcId);evidence(t,"APPLY_FAILED");assertEquals("UNKNOWN",pc(t));assertEquals(0,countLedger(t));
        recovery.recover(t.pcId);success(t);followUp(t);
    }
    @Test @Order(6) void CAN06_recoveryFindsDone() throws Exception {
        Target t=target("CAN06",false);String key=jdbc.queryForObject("select idempotency_key from payment_cancellation where id=?",String.class,t.pcId);
        jdbc.update("update payment_cancellation set status='UNKNOWN' where id=?",t.pcId);
        recovery.recover(t.pcId);success(t);assertEquals(key,jdbc.queryForObject("select idempotency_key from payment_cancellation where id=?",String.class,t.pcId));followUp(t);
    }
    @Test @Order(7) void CAN07_retryExhaustion() throws Exception {
        Target t=target("CAN07",false);jdbc.update("update payment_cancellation set status='UNKNOWN' where id=?",t.pcId);
        doThrow(new TossRestClientException("sandbox query timeout")).when(client).findPaymentHistory(t.orderNumber);
        for(int i=0;i<4;i++)recovery.recover(t.pcId);
        evidence(t,"RETRIES_EXHAUSTED");
        verify(client,times(3)).findPaymentHistory(t.orderNumber);verify(client,never()).cancel(any());
        assertEquals("MANUAL_REVIEW_REQUIRED",jdbc.queryForObject("select cancel_status from order_cancellation where order_cancel_id=?",String.class,t.cancelId));
        assertEquals(3,jdbc.queryForObject("select retry_count from payment_cancellation_recovery where payment_cancellation_id=?",Integer.class,t.pcId));
        assertEquals(0,countLedger(t));
    }
    @Test @Order(8) void CAN08_explicitRejection() throws Exception {
        Target t=target("CAN08",false);int before=reserved();
        doThrow(new TossCancelException("INVALID_REQUEST","sandbox explicit rejection",null)).when(client).cancel(any());
        cancellation.cancelPayment(t.pcId);evidence(t,"REJECTED");
        assertEquals("REJECTED",pc(t));assertEquals(0,countLedger(t));assertEquals(before,reserved());
        assertEquals("INVALID_REQUEST",jdbc.queryForObject("select fail_code from payment_cancellation where id=?",String.class,t.pcId));
    }
}
