package com.autonomousapi.core.geo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Único ponto de acesso ao geo-api (spec 01). Autentica com token de SERVIÇO
 * (não token de usuário), para auditar as chamadas internas separadamente (ADR/spec 01).
 * Nenhum outro componente deve chamar o geo-api diretamente.
 */
@Component
public class GeoApiClient {

    private final RestClient client;
    private final String serviceToken;

    public GeoApiClient(
            @Value("${app.geo-api.base-url}") String baseUrl,
            @Value("${app.geo-api.service-token}") String serviceToken) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.serviceToken = serviceToken;
    }

    /** Health interno do geo-api. Retorna false em qualquer falha (rede, timeout, 5xx). */
    public boolean isHealthy() {
        try {
            client.get()
                    .uri("/internal/v1/health")
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
