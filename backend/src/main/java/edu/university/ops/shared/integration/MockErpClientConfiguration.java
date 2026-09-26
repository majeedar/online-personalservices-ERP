package edu.university.ops.shared.integration;

import edu.university.ops.shared.monitoring.CorrelationId;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * HTTP mode: a {@link RestClient} for the mock-erp container with timeouts and
 * correlation-ID propagation (AGENT.md §26, §53).
 */
@Configuration
@ConditionalOnProperty(name = "ops.integration.mode", havingValue = "http")
public class MockErpClientConfiguration {

    @Bean
    RestClient mockErpClient(IntegrationProperties properties) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.mockErpUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = CorrelationId.current();
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationId.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    ExternalSystemControl httpExternalSystemControl(RestClient mockErpClient) {
        return new ExternalSystemControl() {
            @Override
            public Map<ExternalSystem, Health> status() {
                Map<ExternalSystem, Health> result = new EnumMap<>(ExternalSystem.class);
                Map<?, ?> remote;
                try {
                    remote = mockErpClient.get().uri("/mock/admin/status").retrieve().body(Map.class);
                } catch (RestClientException e) {
                    remote = Map.of();
                }
                for (ExternalSystem s : ExternalSystem.values()) {
                    result.put(s, "UP".equals(remote == null ? null : remote.get(s.name())) ? Health.UP : Health.DOWN);
                }
                return result;
            }

            @Override
            public void simulateOutage(ExternalSystem system, boolean down) {
                mockErpClient.post().uri("/mock/admin/outage/{system}?down={down}", system.name(), down)
                        .retrieve().toBodilessEntity();
            }
        };
    }
}
