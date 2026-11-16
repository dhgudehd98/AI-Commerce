package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.paymentLedger.repository.PaymentLedgerRepository;
import com.sh.aicommerce.toss.dto.transaction.*;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.transaction.repository.PgTransactionRepository;
import com.sh.aicommerce.transactionReconciliation.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in synthetic batch fixtures only; no real PG requests or inventory reservations. */
@EnabledIfEnvironmentVariable(named="PAYMENT_REVERSE_SANDBOX",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false","logging.level.root=OFF",
        "logging.level.org.hibernate.type.descriptor.sql=OFF","logging.level.org.elasticsearch.client=OFF",
        "logging.level.com.sh.aicommerce.batch.transactionReconciliation.reader.InternalReconciliationReader=INFO",
        "logging.level.com.sh.aicommerce.batch.transactionReconciliation.processor.InternalReconciliationProcessor=INFO",
        "logging.level.com.sh.aicommerce.batch.transactionReconciliation.writer.InternalReconciliationWriter=INFO"})
@ContextConfiguration(initializers=PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentReverseReconciliationSandboxTest {
    @Autowired JobLauncher launcher;
    @Autowired @Qualifier("pgTransactionCollectionJob") Job collectionJob;
    @Autowired @Qualifier("pgTransactionReconciliationJob") Job reconciliationJob;
    @SpyBean TossPaymentClient client;
    @SpyBean PgTransactionReconciliationResultRepository results;
    @SpyBean PgTransactionReconciliationIssueRepository issues;
    @Autowired PgTransactionRepository pg;
    @Autowired PaymentLedgerRepository ledgers;
    @Autowired TransactionTemplate transaction;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final String nonce=UUID.randomUUID().toString().replace("-","");
    private static final Path EVIDENCE=Path.of("docs/payment-reverse-evidence.jsonl");
    record Fixture(long ledgerId,long paymentId,String paymentKey,String transactionKey,OffsetDateTime occurredAt) {}
    record Run(JobExecution collection,JobExecution reconciliation,long collectionId,long runId) {}
    private LocalDate date(){return LocalDate.of(2600,1,1).plusDays(Integer.remainderUnsigned(nonce.hashCode(),100000));}
    private Fixture ledger(OffsetDateTime occurredAt) throws Exception {
        PaymentRequestDto request=mapper.readValue("{\"paymentMethod\":\"SAVED_CARD\",\"savedCardId\":1}",PaymentRequestDto.class);
        return transaction.execute(status->{
            String key="REV-P-"+UUID.randomUUID().toString().replace("-","");String tx="REV-T-"+UUID.randomUUID().toString().replace("-","");
            Orders order=Orders.createOrder(em.getReference(Member.class,1L),1L,350000,"REV-FIXTURE-"+UUID.randomUUID());
            Payment payment=Payment.createPayment(request,350000);ReflectionTestUtils.setField(payment,"status",PaymentStatus.PAID);ReflectionTestUtils.setField(payment,"paymentKey",key);ReflectionTestUtils.setField(payment,"transactionKey",tx);
            order.setPayment(payment);em.persist(order);em.flush();
            PaymentLedger ledger=PaymentLedger.createApproval(payment,350000,"KRW",key,tx,occurredAt);em.persist(ledger);em.flush();
            return new Fixture(ledger.getId(),payment.getId(),key,tx,occurredAt);
        });
    }
    private TransactionResponseDto dto(Fixture f,String key){return new TransactionResponseDto("rev-test",key,f.paymentKey,"REV-"+nonce,"카드",null,false,null,"DONE",f.occurredAt,"KRW",350000L);}
    private JobParameters parameters(LocalDate date,String label){return new JobParametersBuilder().addString("targetDate",date.toString()).addString("testRun",label+"-"+UUID.randomUUID()).toJobParameters();}
    private JobExecution collect(LocalDate date,List<TransactionResponseDto> data,String label) throws Exception {
        doAnswer(call->new TransactionHistoryResponseDto(call.getArgument(2)==null?data:List.of())).when(client)
                .transactionHistory(any(LocalDateTime.class),any(LocalDateTime.class),nullable(String.class),anyInt());
        JobExecution c=launcher.run(collectionJob,parameters(date,label));assertEquals(BatchStatus.COMPLETED,c.getStatus());return c;
    }
    private Run reconcile(String label,LocalDate date,List<TransactionResponseDto> data) throws Exception {
        JobExecution c=collect(date,data,label);return reconcile(label,date,c);
    }
    private Run reconcile(String label,LocalDate date,JobExecution c) throws Exception {
        JobExecution r=launcher.run(reconciliationJob,parameters(date,label));long cid=c.getExecutionContext().getLong("collectionRunId"),rid=r.getExecutionContext().getLong("reconciliationRunId");
        capture(label,c,r,cid,rid);return new Run(c,r,cid,rid);
    }
    private long count(Run r,String column){return jdbc.queryForObject("select "+column+" from pg_transaction_reconciliation_run where id=?",Long.class,r.runId);}
    private void successful(Run r,long checked,long only){assertEquals(BatchStatus.COMPLETED,r.reconciliation.getStatus());assertEquals(checked,count(r,"internal_checked_count"));assertEquals(only,count(r,"internal_only_count"));}
    private void capture(String label,JobExecution c,JobExecution r,long cid,long rid) throws Exception {
        Map<String,Object> e=new LinkedHashMap<>();e.put("scenario",label);e.put("time",OffsetDateTime.now().toString());e.put("targetDate",r.getJobParameters().getString("targetDate"));
        e.put("collectionRunId",cid);e.put("runId",rid);e.put("collectionJobExecutionId",c.getId());e.put("reconciliationJobExecutionId",r.getId());e.put("jobStatus",r.getStatus());
        e.put("run",jdbc.queryForMap("select status,pg_count,processed_count,matched_count,mismatch_count,excluded_count,internal_checked_count,internal_only_count,error_code,error_message from pg_transaction_reconciliation_run where id=?",rid));
        e.put("steps",r.getStepExecutions().stream().map(s->Map.of("name",s.getStepName(),"status",s.getStatus(),"readCount",s.getReadCount(),"writeCount",s.getWriteCount(),"commitCount",s.getCommitCount(),"rollbackCount",s.getRollbackCount())).toList());
        e.put("results",jdbc.queryForList("select id,pg_transaction_id,payment_ledger_id,status,transaction_type,amount_diff,pg_snapshot,internal_snapshot from pg_transaction_reconciliation_result where reconciliation_run_id=? order by id",rid));
        e.put("issues",jdbc.queryForList("select i.result_id,i.code,i.pg_value,i.internal_value from pg_transaction_reconciliation_issue i join pg_transaction_reconciliation_result r on r.id=i.result_id where r.reconciliation_run_id=?",rid));
        Files.writeString(EVIDENCE,mapper.writeValueAsString(e)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    @Test @Order(1) void REV01_sameKeyNoDuplicateResult() throws Exception {
        LocalDate d=date();Fixture f=ledger(d.atTime(12,0).atOffset(ZoneOffset.ofHours(9)));Run r=reconcile("REV01",d,List.of(dto(f,f.transactionKey)));
        successful(r,1,0);assertTrue(pg.existsByCollectionRunIdAndTransactionKey(r.collectionId,f.transactionKey));
        assertEquals(1,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=?",Integer.class,r.runId));assertEquals(1,count(r,"matched_count"));
    }
    @Test @Order(2) void REV02_differentKeyProducesInternalOnlyResult() throws Exception {
        LocalDate d=date();Fixture f=ledger(d.atTime(12,0).atOffset(ZoneOffset.ofHours(9)));Run r=reconcile("REV02",d,List.of(dto(f,"REV-DIFFERENT-"+nonce)));
        successful(r,1,1);assertFalse(pg.existsByCollectionRunIdAndTransactionKey(r.collectionId,f.transactionKey));
        assertEquals(1,count(r,"mismatch_count"));assertEquals(2,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=?",Integer.class,r.runId));
        assertEquals(1,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result r join pg_transaction_reconciliation_issue i on i.result_id=r.id where r.reconciliation_run_id=? and r.pg_transaction_id is null and r.pg_snapshot is null and r.amount_diff is null and r.internal_snapshot is not null and i.code='PG_TRANSACTION_NOT_FOUND'",Integer.class,r.runId));
    }
    @Test @Order(3) void REV03_matchingKeyOnlyInOtherCollection() throws Exception {
        LocalDate d=date();Fixture f=ledger(d.atTime(12,0).atOffset(ZoneOffset.ofHours(9)));
        JobExecution other=collect(d,List.of(dto(f,f.transactionKey)),"REV03-other");long otherId=other.getExecutionContext().getLong("collectionRunId");
        Run r=reconcile("REV03",d,List.of());successful(r,1,1);
        assertTrue(pg.existsByCollectionRunIdAndTransactionKey(otherId,f.transactionKey));assertFalse(pg.existsByCollectionRunIdAndTransactionKey(r.collectionId,f.transactionKey));
        assertNotEquals(otherId,r.collectionId);assertEquals(r.collectionId,jdbc.queryForObject("select collection_run_id from pg_transaction_reconciliation_run where id=?",Long.class,r.runId));
        Files.writeString(EVIDENCE,mapper.writeValueAsString(Map.of("scenario","REV03-COLLECTION-SCOPE","otherCollectionRunId",otherId,"selectedCollectionRunId",r.collectionId,"otherExists",true,"selectedExists",false))+"\n",StandardOpenOption.APPEND);
    }
    @Test @Order(4) void REV04_kstOccurredAtBoundaries() throws Exception {
        LocalDate d=date();OffsetDateTime start=d.atStartOfDay().atOffset(ZoneOffset.ofHours(9)),end=start.plusDays(1);
        Fixture atStart=ledger(start),atLast=ledger(end.minusSeconds(1)),atEnd=ledger(end),before=ledger(start.minusSeconds(1)),old=ledger(start.minusDays(5));
        Run r=reconcile("REV04",d,List.of());successful(r,2,2);
        Set<Long> selected=new HashSet<>(jdbc.queryForList("select payment_ledger_id from pg_transaction_reconciliation_result where reconciliation_run_id=?",Long.class,r.runId));
        assertEquals(Set.of(atStart.ledgerId,atLast.ledgerId),selected);
        assertEquals(start.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime().toString().replace('T',' '),jdbc.queryForObject("select DATE_FORMAT(occurred_at,'%Y-%m-%d %H:%i') from payment_ledger where id=?",String.class,atStart.ledgerId));
        Files.writeString(EVIDENCE,mapper.writeValueAsString(Map.of("scenario","REV04-BOUNDARIES","targetDate",d.toString(),"included",List.of(atStart.ledgerId,atLast.ledgerId),"excluded",List.of(atEnd.ledgerId,before.ledgerId,old.ledgerId),"utcStart",start.toInstant().toString()))+"\n",StandardOpenOption.APPEND);
    }
    @Test @Order(5) void REV05_zeroPgStillReadsLedger() throws Exception {
        LocalDate d=date();ledger(d.atTime(12,0).atOffset(ZoneOffset.ofHours(9)));Run r=reconcile("REV05",d,List.of());successful(r,1,1);
        assertEquals(0,count(r,"pg_count"));assertEquals(0,count(r,"processed_count"));assertEquals(2,r.reconciliation.getStepExecutions().size());
    }
    @Test @Order(6) void REV06_resultAndIssueSaveFailuresRollbackChunk() throws Exception {
        for(String failedAt:List.of("RESULT","ISSUE")){
            LocalDate d=date().plusDays(failedAt.equals("RESULT")?1:2);
            Fixture found=ledger(d.atTime(12,0).atOffset(ZoneOffset.ofHours(9)));ledger(d.atTime(13,0).atOffset(ZoneOffset.ofHours(9)));
            if(failedAt.equals("RESULT"))doAnswer(call->{
                PgTransactionReconciliationResult value=call.getArgument(0);
                if(value.getPgTransaction()==null)throw new DataAccessResourceFailureException("sandbox reverse result save failure");
                // This Spring Data method is abstract on the proxy. Persist new forward results in the same real transaction.
                assertNull(value.getId());em.persist(value);return value;
            }).when(results).save(any(PgTransactionReconciliationResult.class));
            else doThrow(new DataAccessResourceFailureException("sandbox reverse issue save failure")).when(issues).save(any(PgTransactionReconciliationIssue.class));
            Run r=reconcile("REV06-"+failedAt,d,List.of(dto(found,found.transactionKey)));
            assertEquals(BatchStatus.FAILED,r.reconciliation.getStatus());assertEquals("FAILED",jdbc.queryForObject("select status from pg_transaction_reconciliation_run where id=?",String.class,r.runId));
            assertEquals(0,count(r,"internal_checked_count"));assertEquals(0,count(r,"internal_only_count"));
            assertEquals(1,count(r,"processed_count"));assertEquals(1,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=?",Integer.class,r.runId));
            assertEquals(0,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_result where reconciliation_run_id=? and pg_transaction_id is null",Integer.class,r.runId));
            assertEquals(0,jdbc.queryForObject("select count(*) from pg_transaction_reconciliation_issue i join pg_transaction_reconciliation_result r on r.id=i.result_id where r.reconciliation_run_id=?",Integer.class,r.runId));
            reset(results,issues);
        }
        assertEquals(2,jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=DATABASE() and table_name='pg_transaction_reconciliation_result' and column_name in ('pg_transaction_id','pg_snapshot') and is_nullable='YES'",Integer.class));
        assertTrue(jdbc.queryForObject("select column_type from information_schema.columns where table_schema=DATABASE() and table_name='pg_transaction_reconciliation_issue' and column_name='code'",String.class).contains("PG_TRANSACTION_NOT_FOUND"));
    }
}
