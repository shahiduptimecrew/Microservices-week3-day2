package com.example.order.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Synchronous REST clients for the downstream services.
 * <p>
 * {@code @LoadBalanced} adds Spring Cloud LoadBalancer to the builder: a URL such as
 * {@code http://PRODUCT-SERVICE/...} is resolved to one of the instances registered in Eureka
 * (cached locally, round-robin). There is no hard-coded host or port anywhere.
 * <p>
 * Every client has a connect and a read timeout. Without them, a hanging downstream service
 * would block Order Service threads forever.
 */
@Configuration
@EnableConfigurationProperties(ClientProperties.class)
public class RestClientConfig {

    /**
     * Plain builder for everything else, in particular the Eureka client itself.
     * Without this {@code @Primary} bean, Eureka would pick up the load-balanced builder below and
     * try to resolve its own server URL (localhost) as a service id: registration would fail.
     */
    @Bean
    @Primary
    @Scope("prototype")
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    @Scope("prototype")
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient productRestClient(@LoadBalanced RestClient.Builder builder, ClientProperties properties) {
        return build(builder, properties.product());
    }

    @Bean
    RestClient paymentRestClient(@LoadBalanced RestClient.Builder builder, ClientProperties properties) {
        return build(builder, properties.payment());
    }

    private static RestClient build(RestClient.Builder builder, ClientProperties.Endpoint endpoint) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(endpoint.connectTimeout());
        requestFactory.setReadTimeout(endpoint.readTimeout());
        return builder.clone()
                .baseUrl(endpoint.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
