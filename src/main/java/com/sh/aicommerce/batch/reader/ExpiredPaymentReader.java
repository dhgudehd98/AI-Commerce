package com.sh.aicommerce.batch.reader;

import com.sh.aicommerce.entity.Payment;
import com.sh.aicommerce.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@RequiredArgsConstructor
@Slf4j
public class ExpiredPaymentReader{

    public JpaPagingItemReader<Long> expiredPaymentRead(
            EntityManagerFactory entityManagerFactory,
            LocalDateTime expiredAt
    ) {
        JpaPagingItemReader<Long> reader = new JpaPagingItemReader<>();

        reader.setName("expiredPaymentReader");
        reader.setEntityManagerFactory(entityManagerFactory);
        reader.setQueryString("""
        select p.id
        from Payment p
        join p.order o
        where p.status = 'CONFIRMING'
        and o.status = 'CREATED'
        and p.updatedAt <= :expiredAt
        order by p.id asc
        """
        );

        Map<String, Object> param = new HashMap<>();

        param.put("expiredAt", expiredAt);
        reader.setParameterValues(param);

        return reader;
    }
}