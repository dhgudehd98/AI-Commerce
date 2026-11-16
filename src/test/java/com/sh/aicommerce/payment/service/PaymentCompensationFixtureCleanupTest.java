package com.sh.aicommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sh.aicommerce.event.payment.compensation.PaymentCompensationEventListener;
import com.sh.aicommerce.payment.dto.request.CardBillingPaymentRequestDto;
import com.sh.aicommerce.paymentCompensation.service.*;
import com.sh.aicommerce.toss.service.TossPaymentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.AopTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** One-off retained audit cleanup for the eight explicitly identified failed-harness fixtures. */
@EnabledIfEnvironmentVariable(named="PAYMENT_COMPENSATION_CLEANUP",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.hibernate.ddl-auto=none","spring.jpa.show-sql=false","logging.level.root=OFF",
        "logging.level.org.hibernate.type.descriptor.sql=OFF","logging.level.org.elasticsearch.client=OFF"})
@ContextConfiguration(initializers=PaymentCancellationSandboxTest.TestEnvironment.class)
@Import(PaymentCancellationSandboxTest.ManualExecution.class)
class PaymentCompensationFixtureCleanupTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentTxService approval;
    @Autowired PaymentCompensationTxService tx;
    @Autowired PaymentCompensationService compensation;
    @SpyBean PaymentCompensationEventListener listener;
    @SpyBean TossPaymentClient client;

    @Test void releaseOnlyTheEightUnapprovedSetupFixtures() throws Exception {
        PaymentCompensationEventListener target=AopTestUtils.getUltimateTargetObject(listener);
        doNothing().when(target).handlePaymentCompensation(any());
        ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
        for(long id=13;id<=20;id++) {
            Map<String,Object> row=jdbc.queryForMap("select o.order_number,p.payment_id,p.status,p.payment_key,a.id attempt_id from orders o join payment p on p.order_id=o.order_id join payment_attempts a on a.payment_id=p.payment_id where o.order_id=?",id);
            String number=(String)row.get("order_number");assertTrue(number.startsWith("COM"));
            assertNull(row.get("payment_key"));
            assertEquals(0,jdbc.queryForObject("select count(*) from payment_ledger where payment_id=?",Integer.class,row.get("payment_id")));
            if("CANCELED".equals(row.get("status")))continue;
            assertEquals("READY",row.get("status"));
            CardBillingPaymentRequestDto request=mapper.readValue(mapper.writeValueAsString(Map.of("orderNumber",number,"paymentId",row.get("payment_id"),"paymentAttemptId",row.get("attempt_id"),"savedCardId",1)),CardBillingPaymentRequestDto.class);
            long task=tx.preparePaymentCompensation(approval.adjustPaymentAction(1L,request).cardRequestDto());
            compensation.compensationProcess(task);
            assertEquals("CANCELED",jdbc.queryForObject("select status from payment where payment_id=?",String.class,row.get("payment_id")));
            assertEquals("PAYMENT_NOT_FOUND",jdbc.queryForObject("select result from payment_compensation where id=?",String.class,task));
            Files.writeString(Path.of("docs/payment-compensation-cleanup.jsonl"),mapper.writeValueAsString(Map.of("orderId",id,"orderNumber",number,"compensationId",task,"result","PAYMENT_NOT_FOUND","stage","SETUP_FIXTURE_CLEANED"))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        }
        verify(client,never()).cardBilling(any());verify(client,never()).cancel(any());
    }
}
