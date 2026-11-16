package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.common.exception.payment.PaymentResultUnknownException;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.event.payment.compensation.PaymentCompensationEventListener;
import com.sh.aicommerce.payment.dto.request.*;
import com.sh.aicommerce.payment.dto.response.PreparePaymentResultDto;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import com.sh.aicommerce.paymentCompensation.service.*;
import com.sh.aicommerce.paymentCompensation.repository.PaymentCompensationRepository;
import com.sh.aicommerce.paymentCompensation.record.PaymentCompensationScanRecord;
import com.sh.aicommerce.orderCancellation.service.OrderCancellationInventoryService;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.toss.dto.cancel.request.TossCancelRequestDto;
import com.sh.aicommerce.toss.dto.response.*;
import com.sh.aicommerce.enums.paymentCompensation.PaymentCompensationStatus;
import com.sh.aicommerce.enums.paymentLedger.PaymentLedgerType;
import com.sh.aicommerce.paymentLedger.service.PaymentLedgerService;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Explicit opt-in only. Temporarily disable the hardcoded cancel timeout before execution. */
@EnabledIfEnvironmentVariable(named="PAYMENT_COMPENSATION_SANDBOX", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false","logging.level.root=OFF",
        "logging.level.org.hibernate.type.descriptor.sql=OFF","logging.level.org.elasticsearch.client=OFF"})
@ContextConfiguration(initializers=PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentCompensationSandboxTest {
    @Autowired PaymentService payments;
    @SpyBean PaymentTxService approvalTx;
    @Autowired PaymentCompensationTxService tx;
    @SpyBean PaymentCompensationService compensation;
    @SpyBean PaymentCompensationEventListener listener;
    @SpyBean TossPaymentClient client;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderCancellationInventoryService inventory;
    @Autowired PaymentCompensationRepository tasks;
    @Autowired PaymentRepository paymentRepository;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired PaymentLedgerRepository ledgers;
    @Autowired PaymentLedgerService ledgerService;
    @Autowired org.springframework.transaction.support.TransactionTemplate transaction;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private static final Path EVIDENCE=Path.of("docs/payment-compensation-evidence.jsonl");
    record Target(String scenario,String orderNumber,long orderId,long paymentId,long attemptId,
                  PaymentBillingCardRequestDto pgRequest,CardBillingPaymentRequestDto approvalRequest) {}

    @BeforeAll static void guard() throws Exception {PaymentCancellationSandboxTest.requireNormalCancelClient();}
    private PaymentCompensationEventListener listenerTarget() {
        return org.springframework.test.util.AopTestUtils.getUltimateTargetObject(listener);
    }
    @BeforeEach void manualEvents(){doNothing().when(listenerTarget()).handlePaymentCompensation(any());}

    private Target prepared(String scenario) throws Exception {
        String number=scenario+"-"+UUID.randomUUID().toString().replace("-","");
        PaymentRequestDto request=mapper.readValue("""
                {"variantId":1,"optionId":1,"usedPoint":0,"paymentMethod":"SAVED_CARD","savedCardId":1,
                 "receiver":{"name":"테스트","email":"test@example.com","phone":"01000000000",
                 "zipCode":"00000","address":"테스트","addressDetail":"테스트","receiverMethod":"DOOR"}}
                """,PaymentRequestDto.class);
        ReflectionTestUtils.setField(request,"orderNumber",number);
        PreparePaymentResultDto p=payments.getPaymentLockByOrderNumber(1L,1L,1L,request);
        CardBillingPaymentRequestDto r=mapper.readValue(mapper.writeValueAsString(Map.of("orderNumber",number,"paymentId",p.getPaymentId(),"paymentAttemptId",p.getAttemptId(),"savedCardId",1)),CardBillingPaymentRequestDto.class);
        return new Target(scenario,number,p.getOrderId(),p.getPaymentId(),p.getAttemptId(),null,r);
    }
    private Target processing(String scenario,boolean approve) throws Exception {
        Target t=prepared(scenario);PaymentBillingCardRequestDto pg=approvalTx.adjustPaymentAction(1L,t.approvalRequest).cardRequestDto();
        t=new Target(t.scenario,t.orderNumber,t.orderId,t.paymentId,t.attemptId,pg,t.approvalRequest);
        if(approve) client.cardBilling(pg);
        return t;
    }
    private long task(Target t){return jdbc.queryForObject("select id from payment_compensation where payment_attempt_id=?",Long.class,t.attemptId);}
    private String state(Target t){return jdbc.queryForObject("select status from payment_compensation where payment_attempt_id=?",String.class,t.attemptId);}
    private int ledgerCount(Target t){return jdbc.queryForObject("select count(*) from payment_ledger where payment_id=?",Integer.class,t.paymentId);}
    private int outboundCount(Target t){return jdbc.queryForObject("select count(*) from outbound where order_id=?",Integer.class,t.orderId);}
    private int reserved(){return jdbc.queryForObject("select reserved_quantity from product_inventory where product_inventory_id=1",Integer.class);}
    private void evidence(Target t,String stage) throws Exception {
        Map<String,Object> e=new LinkedHashMap<>();e.put("scenario",t.scenario);e.put("stage",stage);e.put("time",OffsetDateTime.now().toString());
        e.put("orderNumber",t.orderNumber);e.put("orderId",t.orderId);e.put("paymentId",t.paymentId);e.put("attemptId",t.attemptId);
        e.put("state",jdbc.queryForMap("select o.status order_status,p.status payment_status,p.compensation_required,a.status attempt_status,a.failure_code,c.id compensation_id,c.status compensation_status,c.result,c.retry_count,c.last_error_code,c.payment_cancellation_id,c.checked_at from orders o join payment p on p.order_id=o.order_id join payment_attempts a on a.payment_id=p.payment_id left join payment_compensation c on c.payment_attempt_id=a.id where o.order_id=?",t.orderId));
        e.put("ledger",jdbc.queryForList("select id,type,amount,currency,transaction_key from payment_ledger where payment_id=? order by id",t.paymentId));
        e.put("outboundCount",outboundCount(t));e.put("inventory",jdbc.queryForMap("select on_hand_quantity,reserved_quantity from product_inventory where product_inventory_id=1"));
        Files.writeString(EVIDENCE,mapper.writeValueAsString(e)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private void await(Target t,String status) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        while(System.nanoTime()<end){if(status.equals(state(t)))return;Thread.sleep(50);}
        evidence(t,"WAIT_TIMEOUT");assertEquals(status,state(t));
    }
    private void followUp(Target t) throws Exception {
        assertEquals("FOLLOW_UP_PENDING",state(t));assertEquals(0,outboundCount(t));
        assertNotEquals("PAID",jdbc.queryForObject("select status from orders where order_id=?",String.class,t.orderId));
        long cid=jdbc.queryForObject("select order_cancel_id from order_cancellation where order_id=?",Long.class,t.orderId);
        inventory.applyInventory(3L,cid);assertEquals("COMPLETED",state(t));evidence(t,"FOLLOW_UP_COMPLETED");
    }
    private void unapproved(Target t,String result) throws Exception {
        evidence(t,"UNAPPROVED_COMPLETED");assertEquals("COMPLETED",state(t));assertEquals(0,ledgerCount(t));assertEquals(0,outboundCount(t));
        assertEquals("CANCELED",jdbc.queryForObject("select status from payment where payment_id=?",String.class,t.paymentId));
        assertEquals("CANCELED",jdbc.queryForObject("select status from payment_attempts where id=?",String.class,t.attemptId));
        assertEquals(result,jdbc.queryForObject("select result from payment_compensation where payment_attempt_id=?",String.class,t.attemptId));
        verify(client,never()).cancel(any());
    }

    @Test @Order(1) void COM01_actualApprovalThenTimeoutAsyncCompensation() throws Exception {
        Target t=prepared("COM01");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        List<String> observed=new CopyOnWriteArrayList<>();
        doCallRealMethod().when(listenerTarget()).handlePaymentCompensation(any());
        doAnswer(call->{observed.add(Thread.currentThread().getName()+" tx="+TransactionSynchronizationManager.isActualTransactionActive());entered.countDown();
            try{if(!release.await(20,TimeUnit.SECONDS))throw new IllegalStateException("test gate timeout");return call.callRealMethod();}finally{finished.countDown();}}).when(compensation).compensationProcess(anyLong());
        doAnswer(call->{call.callRealMethod();throw new TossRestClientException("sandbox approval response timeout");}).when(client).cardBilling(any());
        try {
            assertThrows(PaymentResultUnknownException.class,()->payments.adjustPaymentResult(1L,t.approvalRequest));
            assertTrue(entered.await(10,TimeUnit.SECONDS));assertEquals("PENDING",state(t));
            assertEquals("COMPENSATION_PENDING",jdbc.queryForObject("select status from payment where payment_id=?",String.class,t.paymentId));
            evidence(t,"USER_RETURNED_BEFORE_ASYNC_QUERY");
        } finally {release.countDown();}
        assertTrue(finished.await(30,TimeUnit.SECONDS));await(t,"FOLLOW_UP_PENDING");evidence(t,"PG_CANCELED_FOLLOW_UP_PENDING");
        assertTrue(observed.stream().allMatch(x->x.endsWith("tx=false")));assertEquals(2,ledgerCount(t));verify(client,times(1)).cancel(any());followUp(t);
        Files.writeString(EVIDENCE,mapper.writeValueAsString(Map.of("scenario","COM01","asyncExecution",observed))+"\n",StandardOpenOption.APPEND);
    }
    @Test @Order(2) void COM02_actualApprovalThenApplyFailure() throws Exception {
        Target t=prepared("COM02");doCallRealMethod().when(listenerTarget()).handlePaymentCompensation(any());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("sandbox approval apply failure")).when(approvalTx).validatePGResponseData(any(),any());
        assertThrows(PaymentResultUnknownException.class,()->payments.adjustPaymentResult(1L,t.approvalRequest));
        await(t,"FOLLOW_UP_PENDING");evidence(t,"COMPENSATED");assertEquals(2,ledgerCount(t));verify(client,times(1)).cancel(any());followUp(t);
    }
    @Test @Order(3) void COM03_actualPgNotFound() throws Exception {
        Target t=processing("COM03",false);tx.preparePaymentCompensation(t.pgRequest);int before=reserved();
        compensation.compensationProcess(task(t));unapproved(t,"PAYMENT_NOT_FOUND");assertEquals(before-1,reserved());
        verify(client,times(1)).findPaymentHistory(t.orderNumber);compensation.compensationProcess(task(t));assertEquals(before-1,reserved());
    }
    @Test @Order(4) void COM04_abortedAndExpired() throws Exception {
        for(String status:List.of("ABORTED","EXPIRED")) {
            Target t=processing("COM04-"+status,false);tx.preparePaymentCompensation(t.pgRequest);int before=reserved();
            TossPaymentRequestHistoryResponseDto response=mapper.readValue(mapper.writeValueAsString(Map.of("status",status,"orderId",t.orderNumber,"totalAmount",350000,"currency","KRW")),TossPaymentRequestHistoryResponseDto.class);
            doReturn(response).when(client).findPaymentHistory(t.orderNumber);compensation.compensationProcess(task(t));
            unapproved(t,status.equals("ABORTED")?"APPROVAL_ABORTED":"PAYMENT_EXPIRED");assertEquals(before-1,reserved());
        }
    }
    @Test @Order(5) void COM05_pendingRetryBoundaryAndNoRegression() throws Exception {
        Target t=processing("COM05",false);tx.preparePaymentCompensation(t.pgRequest);
        TossPaymentRequestHistoryResponseDto response=mapper.readValue("{\"status\":\"IN_PROGRESS\"}",TossPaymentRequestHistoryResponseDto.class);
        doReturn(response).when(client).findPaymentHistory(t.orderNumber);compensation.compensationProcess(task(t));assertEquals("PENDING",state(t));
        jdbc.update("update payment_compensation set updated_at=DATE_SUB(NOW(), INTERVAL 9 MINUTE) where id=?",task(t));
        compensation.retryPendingCompensations();verify(client,times(1)).findPaymentHistory(t.orderNumber);
        jdbc.update("update payment_compensation set updated_at=DATE_SUB(NOW(), INTERVAL 11 MINUTE) where id=?",task(t));
        doThrow(new TossRestClientException("sandbox lookup timeout")).when(client).findPaymentHistory(t.orderNumber);
        compensation.retryPendingCompensations();verify(client,times(2)).findPaymentHistory(t.orderNumber);assertEquals("PENDING",state(t));
        assertEquals(1,jdbc.queryForObject("select retry_count from payment_compensation where id=?",Integer.class,task(t)));
        PaymentCompensation detached=tasks.findById(task(t)).orElseThrow();ReflectionTestUtils.setField(detached,"status",PaymentCompensationStatus.CANCELLATION_PENDING);
        detached.defer("TEST",false);assertEquals(PaymentCompensationStatus.CANCELLATION_PENDING,detached.getStatus());
        assertEquals(0,ledgerCount(t));verify(client,never()).cancel(any());evidence(t,"PENDING_RETRY_CHECKED");
    }
    @Test @Order(6) void COM06_pgCancelAndFinalCompletionSeparated() throws Exception {
        Target t=processing("COM06",true);tx.preparePaymentCompensation(t.pgRequest);int before=reserved();
        compensation.compensationProcess(task(t));assertEquals("FOLLOW_UP_PENDING",state(t));assertEquals(before,reserved());
        assertEquals(2,ledgerCount(t));evidence(t,"PG_ONLY_COMPLETED");followUp(t);assertEquals(before-1,reserved());
    }
    @Test @Order(7) void COM07_alreadyCanceledLedgerReuseAndConflict() throws Exception {
        Target t=processing("COM07",true);long id=tx.preparePaymentCompensation(t.pgRequest);
        tx.findPaymentAndAttempt(id);TossPaymentRequestHistoryResponseDto approved=client.findPaymentHistory(t.orderNumber);
        Long pc=tx.prepareCancellation(id,approved);
        assertNotNull(pc);assertEquals(1,ledgerCount(t));
        String key=jdbc.queryForObject("select idempotency_key from payment_cancellation where id=?",String.class,pc);
        client.cancel(new TossCancelRequestDto(approved.getPaymentKey(),t.orderNumber,key,"sandbox COM07"));
        // Isolated stale worker fixture: retry its query after PG success/internal response loss.
        jdbc.update("update payment_compensation set status='PENDING' where id=?",id);
        compensation.compensationProcess(id);assertEquals("FOLLOW_UP_PENDING",state(t));assertEquals(2,ledgerCount(t));
        jdbc.update("update payment_compensation set status='PROCESSING' where id=?",id);
        tx.prepareCancellation(id,client.findPaymentHistory(t.orderNumber));assertEquals(2,ledgerCount(t));
        verify(client,times(1)).cancel(any());
        PaymentLedger existing=ledgers.findByPaymentIdAndType(t.paymentId,PaymentLedgerType.APPROVAL).orElseThrow();
        Payment p=mock(Payment.class);when(p.getId()).thenReturn(t.paymentId);when(p.getAmount()).thenReturn(350000);when(p.getPaymentKey()).thenReturn(approved.getPaymentKey());
        PaymentLedger conflict=PaymentLedger.createApproval(p,350000,"KRW",approved.getPaymentKey(),"conflicting-test-transaction",approved.getApprovedAt());
        assertThrows(com.sh.aicommerce.common.exception.paymentLedger.PaymentLedgerException.class,
                ()->transaction.executeWithoutResult(status->ledgerService.saveIfAbsent(conflict)));
        assertEquals(2,ledgerCount(t));
        evidence(t,"CANCELED_REUSED_CONFLICT_REJECTED");followUp(t);
    }
    @Test @Order(8) void COM08_staleProcessingCollection() throws Exception {
        Target old=processing("COM08-OLD",false),fresh=processing("COM08-FRESH",false),existing=processing("COM08-EXISTING",false);
        tx.preparePaymentCompensation(existing.pgRequest);
        jdbc.update("update payment set updated_at=DATE_SUB(NOW(),INTERVAL 31 MINUTE) where payment_id=?",old.paymentId);
        jdbc.update("update payment set updated_at=DATE_SUB(NOW(),INTERVAL 29 MINUTE) where payment_id=?",fresh.paymentId);
        Set<Long> own=Set.of(old.paymentId,fresh.paymentId,existing.paymentId);
        PaymentRepository scoped=mock(PaymentRepository.class);
        when(scoped.findMissingProcessingCompensations(any(),any())).thenAnswer(call->paymentRepository.findMissingProcessingCompensations(call.getArgument(0),call.getArgument(1)).stream().filter(r->own.contains(r.paymentId())).toList());
        PaymentCompensationScanService scan=new PaymentCompensationScanService(scoped,tx,redis);
        ReflectionTestUtils.setField(scan,"staleAfter",Duration.ofMinutes(30));ReflectionTestUtils.setField(scan,"batchSize",100);
        scan.collectMissingCompensations();scan.collectMissingCompensations();
        assertEquals("PENDING",state(old));assertEquals(0,jdbc.queryForObject("select count(*) from payment_compensation where payment_attempt_id=?",Integer.class,fresh.attemptId));
        for(Target t:List.of(old,existing))assertEquals(1,jdbc.queryForObject("select count(*) from payment_compensation where payment_attempt_id=?",Integer.class,t.attemptId));
        verify(client,never()).findPaymentHistory(any());verify(client,never()).cardBilling(any());verify(client,never()).cancel(any());
        evidence(old,"STALE_COLLECTED_ONCE");evidence(fresh,"FRESH_SKIPPED");evidence(existing,"EXISTING_REUSED");
    }
    @Test @Order(7) void COM07_verifyExistingLedgerConflictInTransaction() throws Exception {
        // Read-only recheck of this run's retained fixture; no new PG request or ledger edits.
        Map<String,Object> row=jdbc.queryForMap("select o.order_id,o.order_number,p.payment_id,a.id attempt_id from orders o join payment p on p.order_id=o.order_id join payment_attempts a on a.payment_id=p.payment_id where o.order_id=28");
        String number=(String)row.get("order_number");assertTrue(number.startsWith("COM07-"));
        Target t=new Target("COM07",number,28,((Number)row.get("payment_id")).longValue(),((Number)row.get("attempt_id")).longValue(),null,null);
        PaymentLedger existing=ledgers.findByPaymentIdAndType(t.paymentId,PaymentLedgerType.APPROVAL).orElseThrow();
        Payment payment=mock(Payment.class);when(payment.getId()).thenReturn(t.paymentId);when(payment.getAmount()).thenReturn(existing.getAmount());when(payment.getPaymentKey()).thenReturn(existing.getPaymentKey());
        PaymentLedger conflict=PaymentLedger.createApproval(payment,existing.getAmount(),existing.getCurrency(),existing.getPaymentKey(),"conflicting-test-transaction",existing.getOccurredAt());
        assertThrows(com.sh.aicommerce.common.exception.paymentLedger.PaymentLedgerException.class,
                ()->transaction.executeWithoutResult(status->ledgerService.saveIfAbsent(conflict)));
        assertEquals(2,ledgerCount(t));evidence(t,"LEDGER_CONFLICT_VERIFIED_IN_TRANSACTION");
        verify(client,never()).cancel(any());
    }
}
