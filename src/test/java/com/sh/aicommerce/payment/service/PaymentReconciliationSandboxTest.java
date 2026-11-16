package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.admin.batch.service.AdminBatchService;
import com.sh.aicommerce.common.exception.toss.TossRestClientException;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.toss.dto.response.CancelResponseDto;
import com.sh.aicommerce.toss.dto.transaction.*;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.transaction.service.PgTransactionCollectionRunService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in batch tests. Synthetic business fixtures never reserve stock or call the PG. */
@EnabledIfEnvironmentVariable(named="PAYMENT_RECONCILIATION_SANDBOX",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false","logging.level.root=OFF",
        "logging.level.org.hibernate.type.descriptor.sql=OFF","logging.level.org.elasticsearch.client=OFF"})
@ContextConfiguration(initializers=PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentReconciliationSandboxTest {
    @Autowired JobLauncher launcher;
    @Autowired JobRepository jobRepository;
    @Autowired @Qualifier("pgTransactionCollectionJob") Job collectionJob;
    @Autowired @Qualifier("pgTransactionReconciliationJob") Job reconciliationJob;
    @SpyBean TossPaymentClient client;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired EntityManager em;
    @Autowired AdminBatchService admin;
    @Autowired PgTransactionCollectionRunService collectionService;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final String nonce=UUID.randomUUID().toString().replace("-","");
    private static final Path EVIDENCE=Path.of("docs/payment-reconciliation-evidence.jsonl");
    private static final ZoneOffset KST=ZoneOffset.ofHours(9);
    record Fixture(long paymentId,String paymentKey,String approvalKey,String cancelKey,LocalDate date) {}
    record Batch(JobExecution collection,JobExecution reconciliation,long collectionId,long runId) {}
    private LocalDate date(int offset){
        return LocalDate.of(2090,1,1).plusDays(Integer.remainderUnsigned(nonce.hashCode(),150000)+offset);
    }
    private JobParameters parameters(LocalDate date,String name){return new JobParametersBuilder().addString("targetDate",date.toString()).addString("testRun",name+"-"+UUID.randomUUID()).toJobParameters();}
    private long cid(JobExecution e){return e.getExecutionContext().getLong("collectionRunId");}
    private long rid(JobExecution e){return e.getExecutionContext().getLong("reconciliationRunId");}
    private TransactionResponseDto dto(LocalDate date,String status,String paymentKey,String transactionKey,long amount,String currency){
        return new TransactionResponseDto("rec-test",transactionKey,paymentKey,"REC-"+nonce,"카드",null,false,null,status,date.atTime(12,0).atOffset(KST),currency,amount);
    }
    private List<TransactionResponseDto> missingData(LocalDate date,int count,String status){
        List<TransactionResponseDto> result=new ArrayList<>();
        for(int i=0;i<count;i++)result.add(dto(date,status,"REC-P-"+nonce+"-"+date+"-"+i,"REC-T-"+nonce+"-"+date+"-"+i,350000,"KRW"));
        return result;
    }
    private AtomicInteger mockPages(List<TransactionResponseDto> data){
        AtomicInteger calls=new AtomicInteger();
        doAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"PG API must run outside chunk transaction");
            calls.incrementAndGet();String cursor=call.getArgument(2);int start=0;
            if(cursor!=null){boolean found=false;for(int i=0;i<data.size();i++)if(data.get(i).getTransactionKey().equals(cursor)){start=i+1;found=true;break;}assertTrue(found,"Cursor must advance to the last transaction");}
            int limit=call.getArgument(3);return new TransactionHistoryResponseDto(data.subList(start,Math.min(start+limit,data.size())));
        }).when(client).transactionHistory(any(LocalDateTime.class),any(LocalDateTime.class),nullable(String.class),anyInt());
        return calls;
    }
    private Batch run(String scenario,LocalDate date,List<TransactionResponseDto> data) throws Exception {
        mockPages(data);JobExecution collection=launcher.run(collectionJob,parameters(date,scenario+"-collection"));
        if(collection.getStatus()!=BatchStatus.COMPLETED){capture(scenario,collection,null);assertEquals(BatchStatus.COMPLETED,collection.getStatus());}
        JobExecution reconciliation=launcher.run(reconciliationJob,parameters(date,scenario+"-reconciliation"));
        capture(scenario,collection,reconciliation);assertEquals(BatchStatus.COMPLETED,reconciliation.getStatus());
        return new Batch(collection,reconciliation,cid(collection),rid(reconciliation));
    }
    private void capture(String scenario,JobExecution collection,JobExecution reconciliation) throws Exception {
        Map<String,Object> e=new LinkedHashMap<>();e.put("scenario",scenario);e.put("time",OffsetDateTime.now().toString());
        e.put("targetDate",collection.getJobParameters().getString("targetDate"));e.put("collectionJobExecutionId",collection.getId());e.put("collectionJobStatus",collection.getStatus());
        if(collection.getExecutionContext().containsKey("collectionRunId")){
            e.put("collectionRunId",cid(collection));e.put("collection",jdbc.queryForMap("select id,job_instance_id,job_execution_id,target_date,status,collected_count,error_message from pg_transaction_collection_run where id=?",cid(collection)));
        }
        e.put("collectionSteps",steps(collection));
        if(reconciliation!=null){
            e.put("reconciliationJobExecutionId",reconciliation.getId());e.put("reconciliationJobStatus",reconciliation.getStatus());e.put("reconciliationSteps",steps(reconciliation));
            if(reconciliation.getExecutionContext().containsKey("reconciliationRunId")){
                long id=rid(reconciliation);e.put("runId",id);e.put("run",jdbc.queryForMap("select id,collection_run_id,status,pg_count,processed_count,matched_count,mismatch_count,excluded_count,internal_checked_count,internal_only_count,error_code,error_message from pg_transaction_reconciliation_run where id=?",id));
                e.put("issues",jdbc.queryForList("select i.code,count(*) issue_count from pg_transaction_reconciliation_issue i join pg_transaction_reconciliation_result r on r.id=i.result_id where r.reconciliation_run_id=? group by i.code",id));
                e.put("sampleResults",jdbc.queryForList("select id,status,transaction_type,exclusion_code,amount_diff,pg_snapshot,internal_snapshot from pg_transaction_reconciliation_result where reconciliation_run_id=? order by id limit 5",id));
            }
        }
        Files.writeString(EVIDENCE,mapper.writeValueAsString(e)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private List<Map<String,Object>> steps(JobExecution execution){return execution.getStepExecutions().stream().map(s->Map.<String,Object>of("name",s.getStepName(),"status",s.getStatus(),"readCount",s.getReadCount(),"writeCount",s.getWriteCount(),"commitCount",s.getCommitCount(),"rollbackCount",s.getRollbackCount())).toList();}
    private Set<String> codes(long run){return new HashSet<>(jdbc.queryForList("select distinct i.code from pg_transaction_reconciliation_issue i join pg_transaction_reconciliation_result r on r.id=i.result_id where r.reconciliation_run_id=?",String.class,run));}
    private long count(long run,String column){return jdbc.queryForObject("select "+column+" from pg_transaction_reconciliation_run where id=?",Long.class,run);}
    private Fixture fixture(LocalDate date,boolean approvalLedger,boolean canceled,boolean cancelLedger,boolean priorApproval) throws Exception {
        PaymentRequestDto request=mapper.readValue("{\"paymentMethod\":\"SAVED_CARD\",\"savedCardId\":1}",PaymentRequestDto.class);
        String key="REC-P-"+UUID.randomUUID().toString().replace("-","");String approval="REC-A-"+UUID.randomUUID().toString().replace("-","");String cancel="REC-C-"+UUID.randomUUID().toString().replace("-","");
        return transaction.execute(status->{
            Orders order=Orders.createOrder(em.getReference(Member.class,1L),1L,350000,"REC-FIXTURE-"+UUID.randomUUID());
            Payment payment=Payment.createPayment(request,350000);ReflectionTestUtils.setField(payment,"status",PaymentStatus.PAID);ReflectionTestUtils.setField(payment,"paymentKey",key);ReflectionTestUtils.setField(payment,"transactionKey",approval);order.setPayment(payment);em.persist(order);em.flush();
            if(approvalLedger)em.persist(PaymentLedger.createApproval(payment,350000,"KRW",key,approval,(priorApproval?date.minusDays(1):date).atTime(10,0).atOffset(KST)));
            if(canceled){
                OrderCancellation oc=new OrderCancellation(order,"synthetic REC fixture",false);oc.startOrderCancellation();em.persist(oc);em.flush();
                PaymentCancellation pc=PaymentCancellation.createPaymentCancellation(payment,oc);em.persist(pc);em.flush();
                CancelResponseDto response=new CancelResponseDto("synthetic REC fixture",date.atTime(11,0).atOffset(KST),350000,0,0,0,0,0,0,cancel,null,"DONE",null);
                pc.cancelSuccess(response);payment.updateCancelStatus();
                if(cancelLedger)em.persist(PaymentLedger.createCancellation(payment,pc,350000,"KRW",key,cancel,response.getCanceledAt()));
            }
            em.flush();return new Fixture(payment.getId(),key,approval,cancel,date);
        });
    }

    @Test @Order(1) void REC01_actualPgCollectionAndReconciliation() throws Exception {
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Seoul"));
        JobExecution c=launcher.run(collectionJob,parameters(date,"REC01-real"));
        if(c.getStatus()!=BatchStatus.COMPLETED){capture("REC01",c,null);assertEquals(BatchStatus.COMPLETED,c.getStatus());}
        JobExecution r=launcher.run(reconciliationJob,parameters(date,"REC01-real"));capture("REC01",c,r);assertEquals(BatchStatus.COMPLETED,r.getStatus());
        long controls=jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=? and payment_id in (1,2,4,5,6,7,8,9,10,11,12,21,22,27,28)",Long.class,rid(r));
        assertEquals(26,controls,"15 actual approvals and 11 actual cancellations retained from prior tests");
        assertEquals(0,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=? and payment_id in (1,2,4,5,6,7,8,9,10,11,12,21,22,27,28) and status<>'MATCHED'",Integer.class,rid(r)));
        assertEquals(2,r.getStepExecutions().size());
    }
    @Test @Order(2) void REC02_pgOnlyAndPaging() throws Exception {
        Batch b=run("REC02",date(2),missingData(date(2),1,"DONE"));
        assertTrue(codes(b.runId).contains("PAYMENT_NOT_FOUND"));assertEquals(1,count(b.runId,"mismatch_count"));
        var page=admin.mismatches(b.runId,0,1);assertEquals(1,page.getTotalElements());assertEquals(1,page.getContent().size());
    }
    @Test @Order(3) void REC03_missingApprovalAndCancellationLedgers() throws Exception {
        Fixture approval=fixture(date(3),false,false,false,false);
        Batch a=run("REC03-APPROVAL",date(3),List.of(dto(date(3),"DONE",approval.paymentKey,approval.approvalKey,350000,"KRW")));
        assertTrue(codes(a.runId).contains("APPROVAL_LEDGER_NOT_FOUND"));
        Fixture cancel=fixture(date(4),true,true,false,false);
        Batch c=run("REC03-CANCELLATION",date(4),List.of(dto(date(4),"CANCELED",cancel.paymentKey,cancel.cancelKey,350000,"KRW")));
        assertTrue(codes(c.runId).contains("CANCELLATION_LEDGER_NOT_FOUND"));
    }
    @Test @Order(4) void REC04_amountCurrencyKeyAndLinkMismatch() throws Exception {
        Fixture f=fixture(date(5),true,false,false,false);
        Batch b=run("REC04-VALUES",date(5),List.of(dto(date(5),"DONE",f.paymentKey,"REC-WRONG-"+nonce,350001,"USD")));
        assertTrue(codes(b.runId).containsAll(Set.of("PAYMENT_AMOUNT_MISMATCH","APPROVAL_LEDGER_TRANSACTION_KEY_MISMATCH","APPROVAL_LEDGER_AMOUNT_MISMATCH","APPROVAL_LEDGER_CURRENCY_MISMATCH")));
        assertTrue(jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_issue i join pg_transaction_reconciliation_result r on r.id=i.result_id where r.reconciliation_run_id=? and i.pg_value is not null and i.internal_value is not null",Integer.class,b.runId)>=4);
        Fixture a=fixture(date(6),true,false,false,false),c=fixture(date(6),true,true,true,false);
        Batch link=run("REC04-LINK",date(6),List.of(dto(date(6),"CANCELED",a.paymentKey,c.cancelKey,350000,"KRW")));
        assertTrue(codes(link.runId).containsAll(Set.of("CANCELLATION_PAYMENT_MISMATCH","CANCELLATION_LEDGER_PAYMENT_MISMATCH","CANCELLATION_LEDGER_PAYMENT_KEY_MISMATCH")));
    }
    @Test @Order(5) void REC05_multipleIssuesSingleResultPagination() throws Exception {
        Fixture f=fixture(date(7),true,false,false,false);
        Batch b=run("REC05",date(7),List.of(dto(date(7),"DONE",f.paymentKey,f.approvalKey,350001,"USD")));
        assertEquals(1,count(b.runId,"mismatch_count"));assertEquals(1,count(b.runId,"processed_count"));
        assertEquals(1,admin.mismatches(b.runId,0,1).getTotalElements());
        assertEquals(3,codes(b.runId).size());
    }
    @Test @Order(6) void REC06_priorApprovalNextDayCancellation() throws Exception {
        Fixture f=fixture(date(8),true,true,true,true);
        Batch b=run("REC06-CANCEL",date(8),List.of(dto(date(8),"CANCELED",f.paymentKey,f.cancelKey,350000,"KRW")));
        assertEquals(1,count(b.runId,"matched_count"));assertEquals(0,count(b.runId,"mismatch_count"));assertTrue(codes(b.runId).isEmpty());
        Batch a=run("REC06-PAST-APPROVAL",date(8).minusDays(1),List.of(dto(date(8).minusDays(1),"DONE",f.paymentKey,f.approvalKey,350000,"KRW")));
        assertEquals(1,count(a.runId,"matched_count"));assertEquals(0,count(a.runId,"mismatch_count"));
    }
    @Test @Order(7) void REC07_excludedStates() throws Exception {
        List<String> statuses=List.of("READY","IN_PROGRESS","WAITING_FOR_DEPOSIT","PARTIAL_CANCELED","ABORTED","EXPIRED","UNKNOWN_TEST");
        List<TransactionResponseDto> data=new ArrayList<>();for(int i=0;i<statuses.size();i++)data.add(dto(date(9),statuses.get(i),"REC-XP-"+nonce+i,"REC-XT-"+nonce+i,350000,"KRW"));
        Batch b=run("REC07",date(9),data);assertEquals(7,count(b.runId,"processed_count"));assertEquals(7,count(b.runId,"excluded_count"));assertEquals(0,count(b.runId,"matched_count"));assertEquals(0,count(b.runId,"mismatch_count"));
        assertEquals(7,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=? and status='EXCLUDED' and exclusion_code is not null and exclusion_reason is not null and pg_snapshot is not null",Integer.class,b.runId));
    }
    @Test @Order(8) void REC08_pagingChunkBoundariesAndZero() throws Exception {
        int i=0;for(int n:new int[]{0,1,50,51,205}){
            LocalDate date=date(20+i++);List<TransactionResponseDto> data=missingData(date,n,"IN_PROGRESS");AtomicInteger calls=mockPages(data);
            JobExecution c=launcher.run(collectionJob,parameters(date,"REC08-"+n));JobExecution r=launcher.run(reconciliationJob,parameters(date,"REC08-"+n));capture("REC08-"+n,c,r);
            assertEquals(BatchStatus.COMPLETED,c.getStatus());assertEquals(BatchStatus.COMPLETED,r.getStatus());
            assertEquals(n,jdbc.queryForObject("select count(*) from pg_transaction where collection_run_id=?",Integer.class,cid(c)));assertEquals(n,count(rid(r),"processed_count"));
            assertEquals(n==0?1:(n+99)/100+1,calls.get());assertEquals(2,r.getStepExecutions().size());
            assertEquals(n,c.getStepExecutions().iterator().next().getWriteCount());
        }
    }
    @Test @Order(9) void REC09_collectionFailureAndIncompleteLatestBlock() throws Exception {
        LocalDate failedDate=date(30);List<TransactionResponseDto> data=missingData(failedDate,100,"IN_PROGRESS");AtomicInteger calls=new AtomicInteger();
        doAnswer(call->{if(calls.incrementAndGet()==1)return new TransactionHistoryResponseDto(data);throw new TossRestClientException("sandbox second-page failure");}).when(client).transactionHistory(any(LocalDateTime.class),any(LocalDateTime.class),nullable(String.class),anyInt());
        JobExecution c=launcher.run(collectionJob,parameters(failedDate,"REC09-failed"));JobExecution r=launcher.run(reconciliationJob,parameters(failedDate,"REC09-blocked"));capture("REC09-FAILED",c,r);
        assertEquals(BatchStatus.FAILED,c.getStatus());assertEquals(BatchStatus.FAILED,r.getStatus());assertFalse(r.getExecutionContext().containsKey("reconciliationRunId"));
        assertEquals(100,jdbc.queryForObject("select collected_count from pg_transaction_collection_run where id=?",Integer.class,cid(c)));
        LocalDate runningDate=date(31);Batch completed=run("REC09-PREVIOUS-COMPLETED",runningDate,List.of());
        JobExecution running=jobRepository.createJobExecution("rec-test-running-"+nonce,parameters(runningDate,"REC09-running"));
        long run=collectionService.start(running.getJobInstance().getInstanceId(),running.getId(),runningDate);running.getExecutionContext().putLong("collectionRunId",run);
        JobExecution blocked=launcher.run(reconciliationJob,parameters(runningDate,"REC09-no-fallback"));capture("REC09-RUNNING",running,blocked);
        assertEquals(BatchStatus.FAILED,blocked.getStatus());assertFalse(blocked.getExecutionContext().containsKey("reconciliationRunId"));
        assertEquals("RUNNING",jdbc.queryForObject("select status from pg_transaction_collection_run where id=?",String.class,run));
    }
}
