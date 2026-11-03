package com.sh.aicommerce.common.client;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class TossHttpClientConfig {

    @Value("${toss.payment.http.connect-timeout}")
    private Duration connectTimeout;

    @Value("${toss.payment.http.response-timeout}")
    private Duration responseTimeout;

    @Value("${toss.payment.http.connection-request-timeout}")
    private Duration connectionRequestTimeout;

    @Value("${toss.payment.http.idle-timeout}")
    private Duration idleTimeout;

    @Bean(destroyMethod = "close")
    public CloseableHttpClient tossPaymentHttpClient(
            @Value("${toss.payment.http.payment.max-connections}") int maxConnections,
            @Value("${toss.payment.http.payment.max-connections-per-route}") int maxConnectionsPerRoute
    ) {
        return createHttpClient(maxConnections, maxConnectionsPerRoute);
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient tossCancelHttpClient(
            @Value("${toss.payment.http.cancel.max-connections}") int maxConnections,
            @Value("${toss.payment.http.cancel.max-connections-per-route}") int maxConnectionsPerRoute
    ) {
        return createHttpClient(maxConnections, maxConnectionsPerRoute);
    }

    @Bean
    public RestTemplate tossPaymentRestTemplate(
            RestTemplateBuilder builder,
            @Qualifier("tossPaymentHttpClient") CloseableHttpClient httpClient
    ) {
        return createRestTemplate(builder, httpClient);
    }

    @Bean
    public RestTemplate tossCancelRestTemplate(
            RestTemplateBuilder builder,
            @Qualifier("tossCancelHttpClient") CloseableHttpClient httpClient
    ) {
        return createRestTemplate(builder, httpClient);
    }

    private CloseableHttpClient createHttpClient(int maxConnections, int maxConnectionsPerRoute) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(timeout(connectTimeout))
                .setSocketTimeout(timeout(responseTimeout))
                .build();

        // 각 클라이언트가 독립된 풀을 소유합니다. 승인 포화가 취소·조회 연결을 점유하지 않습니다.
        var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnTotal(maxConnections)
                .setMaxConnPerRoute(maxConnectionsPerRoute)
                .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(timeout(connectionRequestTimeout))
                .setResponseTimeout(timeout(responseTimeout))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // 재시도는 기존 업무 계층의 멱등성·상태 검증을 통해서만 수행합니다.
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(idleTimeout.toMillis()))
                .build();
    }

    private RestTemplate createRestTemplate(RestTemplateBuilder builder, CloseableHttpClient httpClient) {
        return builder.requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient)).build();
    }

    private Timeout timeout(Duration duration) {
        return Timeout.ofMilliseconds(duration.toMillis());
    }
}
