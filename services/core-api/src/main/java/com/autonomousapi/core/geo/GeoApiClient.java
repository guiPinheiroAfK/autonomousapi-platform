package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.ChargingStationsResponse;
import com.autonomousapi.core.geo.dto.DrivingEventsResponse;
import com.autonomousapi.core.geo.dto.GeoChargingStationsResponse;
import com.autonomousapi.core.geo.dto.GeoPlace;
import com.autonomousapi.core.geo.dto.GeoRouteResponse;
import com.autonomousapi.core.geo.dto.GpsPingRequest;
import com.autonomousapi.core.geo.dto.PlaceResponse;
import com.autonomousapi.core.geo.dto.RouteResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

    /**
     * Encaminha um ping de GPS bruto do app mobile pro geo-api (spec 01: mobile nunca chama
     * geo-api direto — core-api é o único orquestrador). Lança em qualquer falha; quem chama
     * decide se enfileira de novo no mobile (fila offline já trata isso do lado do app).
     */
    public void ingestGpsPing(GpsPingRequest ping) {
        client.post()
                .uri("/internal/v1/gps/pings")
                .header("X-Service-Token", serviceToken)
                .body(ping)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * Estações de recarga elétrica (spec 06, item 1). RNF011 aplicado aqui também: falha de
     * rede/timeout/geo-api fora do ar vira resposta "indisponível" (lista vazia,
     * providerAvailable=false), nunca propaga exceção pro controller — a tela do usuário não
     * pode quebrar por causa de um provedor terceiro instável.
     */
    public ChargingStationsResponse chargingStations(Double lat, Double lon, Double radiusKm) {
        try {
            GeoChargingStationsResponse response = client
                    .get()
                    .uri(builder -> {
                        builder.path("/internal/v1/charging-stations");
                        if (lat != null) builder.queryParam("lat", lat);
                        if (lon != null) builder.queryParam("lon", lon);
                        if (radiusKm != null) builder.queryParam("radius_km", radiusKm);
                        return builder.build();
                    })
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(GeoChargingStationsResponse.class);
            return response != null ? response.toPublic() : ChargingStationsResponse.indisponivel();
        } catch (Exception ex) {
            return ChargingStationsResponse.indisponivel();
        }
    }

    /**
     * Componentes de avaliação automática de motorista (spec 06, item 3). Chamado só pelo
     * job diário {@code DriverAutoRatingJob} — falha vira "sem dado" (pingCount=0), o job
     * trata isso como amostra insuficiente e não lança rating pra aquela viagem, em vez de
     * propagar exceção e derrubar o processamento das outras viagens do lote.
     */
    public DrivingEventsResponse drivingEvents(UUID vehicleId, Instant from, Instant to) {
        try {
            DrivingEventsResponse response = client
                    .get()
                    .uri(builder -> builder
                            .path("/internal/v1/driving-events")
                            .queryParam("vehicle_id", vehicleId)
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(DrivingEventsResponse.class);
            return response != null ? response : DrivingEventsResponse.vazio();
        } catch (Exception ex) {
            return DrivingEventsResponse.vazio();
        }
    }

    /**
     * Rota ponto-a-ponto (spec 02). O geo-api já responde 200 com {@code available=false}
     * quando o motor está fora do ar; o try/catch aqui cobre a camada de baixo (o próprio
     * geo-api inacessível), para a tela receber sempre a mesma forma de resposta em vez de
     * um 500 vazado.
     */
    public RouteResponse route(double fromLat, double fromLon, double toLat, double toLon) {
        try {
            GeoRouteResponse response = client
                    .get()
                    .uri(builder -> builder
                            .path("/internal/v1/route")
                            .queryParam("from_lat", fromLat)
                            .queryParam("from_lon", fromLon)
                            .queryParam("to_lat", toLat)
                            .queryParam("to_lon", toLon)
                            .build())
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(GeoRouteResponse.class);
            return response != null
                    ? response.toPublic()
                    : RouteResponse.indisponivel("Serviço de roteamento não respondeu.");
        } catch (Exception ex) {
            return RouteResponse.indisponivel("Serviço de roteamento indisponível no momento.");
        }
    }

    /**
     * Endereço -> coordenada, restrito à área do piloto. Falha vira lista vazia: para quem
     * digita no campo de busca, "não achei" e "o geocoder caiu" levam à mesma ação prática
     * (tentar outro termo), e um erro na tela só atrapalharia.
     */
    public List<PlaceResponse> geocode(String query) {
        try {
            List<GeoPlace> lugares = client
                    .get()
                    .uri(builder -> builder.path("/internal/v1/geocode").queryParam("q", query).build())
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return lugares != null ? lugares.stream().map(GeoPlace::toPublic).toList() : List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }
}
