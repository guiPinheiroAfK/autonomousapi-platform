package com.autonomousapi.core.vehicle.marketvalue.fipe;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente da API pública da Tabela FIPE (spec 06, item 2: "usar API pública/terceirizada
 * de consulta FIPE, não manter tabela própria"). Chamada só pelo {@code VehicleFipeSyncJob},
 * nunca em tempo real numa requisição de usuário — spec é explícito sobre isso.
 */
@Component
public class FipeClient {

    private final RestClient client;

    public FipeClient(@Value("${app.fipe.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public List<FipeMarca> marcas(String tipo) {
        List<FipeMarca> body = client.get()
                .uri("/{tipo}/marcas", tipo)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : List.of();
    }

    public List<FipeModeloResumo> modelos(String tipo, String marcaCodigo) {
        FipeModelosResponse body = client.get()
                .uri("/{tipo}/marcas/{marca}/modelos", tipo, marcaCodigo)
                .retrieve()
                .body(FipeModelosResponse.class);
        return body != null && body.modelos() != null ? body.modelos() : List.of();
    }

    public List<FipeAno> anos(String tipo, String marcaCodigo, String modeloCodigo) {
        List<FipeAno> body = client.get()
                .uri("/{tipo}/marcas/{marca}/modelos/{modelo}/anos", tipo, marcaCodigo, modeloCodigo)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return body != null ? body : List.of();
    }

    public FipeValor valor(String tipo, String marcaCodigo, String modeloCodigo, String anoCodigo) {
        return client.get()
                .uri("/{tipo}/marcas/{marca}/modelos/{modelo}/anos/{ano}", tipo, marcaCodigo, modeloCodigo, anoCodigo)
                .retrieve()
                .body(FipeValor.class);
    }
}
