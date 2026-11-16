package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.*;
import com.sh.aicommerce.entity.*;
import com.sh.aicommerce.enums.payment.PaymentStatus;
import com.sh.aicommerce.payment.dto.request.PaymentRequestDto;
import com.sh.aicommerce.toss.dto.transaction.*;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import com.sh.aicommerce.transactionReconciliation.service.PgTransactionReconciliationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.*;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
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

@EnabledIfEnvironmentVariable(named="PAYMENT_OPERATIONS_SANDBOX",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false","logging.level.root=OFF",
        "logging.level.org.hibernate.type.descriptor.sql=OFF","logging.level.org.elasticsearch.client=OFF"})
@ContextConfiguration(initializers=PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentOperationsSandboxTest {
    @Autowired JobLauncher launcher;
    @Autowired @Qualifier("pgTransactionCollectionJob") Job collectionJob;
    @Autowired @Qualifier("pgTransactionReconciliationJob") Job reconciliationJob;
    @SpyBean TossPaymentClient client;
    @SpyBean PgTransactionReconciliationService reconciliation;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate transaction;
    @Autowired TestRestTemplate http;
    @Autowired ApplicationContext context;
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final String nonce=UUID.randomUUID().toString().replace("-","");
    private static final Path EVIDENCE=Path.of("docs/payment-operations-evidence.jsonl");
    private LocalDate date(int delta){return LocalDate.of(3000,1,1).plusDays(Integer.remainderUnsigned(nonce.hashCode(),100000)+delta);}
    record Run(JobExecution c,JobExecution r,long cid,long rid,LocalDate date) {}
    private JobParameters params(LocalDate date){return new JobParametersBuilder().addString("targetDate",date.toString()).addString("testRun","OPS-"+UUID.randomUUID()).toJobParameters();}
    private TransactionResponseDto dto(LocalDate date,String suffix,String status){return new TransactionResponseDto("ops-test","OPS-T-"+nonce+suffix,"OPS-P-"+nonce+suffix,"OPS-"+nonce,"카드",null,false,null,status,date.atTime(12,0).atOffset(ZoneOffset.ofHours(9)),"KRW",350000L);}
    private void mockPages(List<TransactionResponseDto> data){doAnswer(call->new TransactionHistoryResponseDto(call.getArgument(2)==null?data:List.of())).when(client).transactionHistory(any(LocalDateTime.class),any(LocalDateTime.class),nullable(String.class),anyInt());}
    private Run run(String label,LocalDate date,List<TransactionResponseDto> data) throws Exception {
        mockPages(data);JobExecution c=launcher.run(collectionJob,params(date));assertEquals(BatchStatus.COMPLETED,c.getStatus());
        JobExecution r=launcher.run(reconciliationJob,params(date));Run run=new Run(c,r,c.getExecutionContext().getLong("collectionRunId"),r.getExecutionContext().getLong("reconciliationRunId"),date);capture(label,run);return run;
    }
    private void write(Map<String,?> data) throws Exception {Files.writeString(EVIDENCE,mapper.writeValueAsString(data)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
    private void capture(String label,Run r) throws Exception {
        write(Map.of("scenario",label,"time",OffsetDateTime.now().toString(),"targetDate",r.date.toString(),"collectionRunId",r.cid,"runId",r.rid,"collectionJobExecutionId",r.c.getId(),"reconciliationJobExecutionId",r.r.getId(),"jobStatus",r.r.getStatus(),"run",jdbc.queryForMap("select id,status,pg_count,processed_count,matched_count,mismatch_count,excluded_count,internal_checked_count,internal_only_count,error_code,error_message from pg_transaction_reconciliation_run where id=?",r.rid)));
    }
    private void missingLedger(LocalDate date) throws Exception {
        PaymentRequestDto request=mapper.readValue("{\"paymentMethod\":\"SAVED_CARD\",\"savedCardId\":1}",PaymentRequestDto.class);
        transaction.executeWithoutResult(status->{
            String key="OPS-LP-"+nonce;Orders order=Orders.createOrder(em.getReference(Member.class,1L),1L,350000,"OPS-FIXTURE-"+UUID.randomUUID());
            Payment p=Payment.createPayment(request,350000);ReflectionTestUtils.setField(p,"status",PaymentStatus.PAID);ReflectionTestUtils.setField(p,"paymentKey",key);order.setPayment(p);em.persist(order);em.flush();
            em.persist(PaymentLedger.createApproval(p,350000,"KRW",key,"OPS-LT-"+nonce,date.atTime(12,0).atOffset(ZoneOffset.ofHours(9))));em.flush();
        });
    }
    private JsonNode get(String path){var response=http.getForEntity(path,JsonNode.class);assertEquals(HttpStatus.OK,response.getStatusCode());assertNotNull(response.getBody());return response.getBody();}
    @Test @Order(1) void OPS01_bidirectionalCountersAndApi() throws Exception {
        LocalDate d=date(1);missingLedger(d);Run r=run("OPS01",d,List.of(dto(d,"A","DONE"),dto(d,"B","DONE")));assertEquals(BatchStatus.COMPLETED,r.r.getStatus());
        JsonNode list=get("/api/admin/batch/list?from="+d+"&to="+d+"&size=10");JsonNode row=list.path("content").get(0);
        assertEquals(r.rid,row.path("id").asLong());assertEquals(2,row.path("mismatchCount").asLong());assertEquals(1,row.path("internalOnlyCount").asLong());assertEquals(3,row.path("totalMismatchCount").asLong());
        JsonNode mismatches=get("/api/admin/batch/reconciliation/"+r.rid+"/mismatches?size=10");assertEquals(3,mismatches.path("totalElements").asLong());
        JsonNode results=get("/api/admin/batch/reconciliation/"+r.rid+"/results?status=MISMATCHED&size=10");assertEquals(3,results.path("totalElements").asLong());
        int internal=0;for(JsonNode item:results.path("content"))if(item.path("pgTransactionId").isNull()){
            internal++;assertTrue(item.path("pgAmount").isNull());assertTrue(item.path("pgSnapshot").isNull());assertTrue(item.path("internalSnapshot").isObject());assertEquals("PG_TRANSACTION_NOT_FOUND",item.path("issues").get(0).path("code").asText());
        }assertEquals(1,internal);
        write(Map.of("scenario","OPS01-HTTP","targetDate",d.toString(),"runId",r.rid,"list",list,"mismatches",mismatches,"results",results));
        var html=http.getForEntity("/api/admin/batch",String.class);assertEquals(HttpStatus.OK,html.getStatusCode());assertTrue(html.getBody().contains("전체 불일치"));
    }
    @Test @Order(2) void OPS02_balancedAndMismatchedFinalization() throws Exception {
        LocalDate d=date(2);missingLedger(d);Run good=run("OPS02-VALID",d,List.of(dto(d,"GOOD","IN_PROGRESS")));assertEquals(BatchStatus.COMPLETED,good.r.getStatus());
        assertEquals(1,jdbc.queryForObject("select internal_only_count from pg_transaction_reconciliation_run where id=?",Integer.class,good.rid));
        for(String column:List.of("pg_count","matched_count")){
            doAnswer(call->{jdbc.update("update pg_transaction_reconciliation_run set "+column+"="+column+"+1 where id=?",call.getArgument(0,Long.class));return call.callRealMethod();})
                    .when(reconciliation).finish(anyLong(),eq(true),isNull(),isNull());
            LocalDate date=date(column.equals("pg_count")?3:4);Run bad=run("OPS02-BAD-"+column,date,List.of(dto(date,column,"IN_PROGRESS")));
            assertEquals(BatchStatus.FAILED,bad.r.getStatus());assertEquals("FAILED",jdbc.queryForObject("select status from pg_transaction_reconciliation_run where id=?",String.class,bad.rid));
            assertEquals("RECONCILIATION_COUNT_MISMATCH",jdbc.queryForObject("select error_code from pg_transaction_reconciliation_run where id=?",String.class,bad.rid));
            assertTrue(jdbc.queryForObject("select error_message from pg_transaction_reconciliation_run where id=?",String.class,bad.rid).contains("processedCount=1"));
            reset(reconciliation);
        }
    }
    @Test @Order(3) void OPS03_reexecutionIdentityAndCollectionPolicy() throws Exception {
        LocalDate d=date(5);Run first=run("OPS03-FIRST",d,List.of(dto(d,"SAME","IN_PROGRESS")));
        assertThrows(JobInstanceAlreadyCompleteException.class,()->launcher.run(collectionJob,first.c.getJobParameters()));
        assertThrows(JobInstanceAlreadyCompleteException.class,()->launcher.run(reconciliationJob,first.r.getJobParameters()));
        JobExecution second=launcher.run(reconciliationJob,params(d));assertEquals(BatchStatus.COMPLETED,second.getStatus());long secondRun=second.getExecutionContext().getLong("reconciliationRunId");
        assertNotEquals(first.rid,secondRun);assertNotEquals(first.r.getId(),second.getId());
        assertEquals(first.cid,jdbc.queryForObject("select collection_run_id from pg_transaction_reconciliation_run where id=?",Long.class,secondRun));
        capture("OPS03-NEW-RECONCILIATION",new Run(first.c,second,first.cid,secondRun,d));
        JobExecution recollect=launcher.run(collectionJob,params(d));assertEquals(BatchStatus.FAILED,recollect.getStatus());long failedCollection=recollect.getExecutionContext().getLong("collectionRunId");
        assertEquals(0,jdbc.queryForObject("select count(*) from pg_transaction where collection_run_id=?",Integer.class,failedCollection));
        assertEquals(1,jdbc.queryForObject("select count(*) from pg_transaction where collection_run_id=?",Integer.class,first.cid));
        write(Map.of("scenario","OPS03-IDENTITY","sameCompletedCollectionRejected",true,"sameCompletedReconciliationRejected",true,"firstRunId",first.rid,"newRunId",secondRun,"newRecollectionRunId",failedCollection,"newRecollectionJobExecutionId",recollect.getId(),"newRecollectionStatus",recollect.getStatus()));
    }
    @Test @Order(4) void OPS04_sourceAndSchedulerAudit() throws Exception {
        String client=Files.readString(Path.of("src/main/java/com/sh/aicommerce/toss/service/TossPaymentClient.java"));
        assertTrue(client.contains("[테스트] 취소 완료 후 응답 타임아웃 재현"));
        assertFalse(context.containsBean("org.springframework.context.annotation.internalScheduledAnnotationProcessor"));
        List<String> schedulers=new ArrayList<>();
        try(var paths=Files.walk(Path.of("src/main/java/com/sh/aicommerce"))){
            for(Path p:paths.filter(x->x.toString().endsWith("Scheduler.java")).toList()){
                if(!p.toString().contains("payment")&&!p.toString().contains("orderCancellation")&&!p.toString().contains("batch/scheduler"))continue;
                for(String line:Files.readAllLines(p))if(line.contains("@Scheduled"))schedulers.add(p.getFileName()+": "+line.trim());
            }
        }
        write(Map.of("scenario","OPS04-AUDIT","testContextAutomaticScheduling",false,"cancelTimeoutInjection","restored to original active state per session agreement","schedulerSource",schedulers));
    }
}
