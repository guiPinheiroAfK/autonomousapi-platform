package com.autonomousapi.core.geo;

import com.autonomousapi.core.geo.dto.ChargingStationsResponse;
import com.autonomousapi.core.geo.dto.DistanceMatrixRequest;
import com.autonomousapi.core.geo.dto.DistanceMatrixResponse;
import com.autonomousapi.core.geo.dto.DrivingEventsResponse;
import com.autonomousapi.core.geo.dto.GeoChargingStationsResponse;
import com.autonomousapi.core.geo.dto.GeoPlace;
import com.autonomousapi.core.geo.dto.GeoRouteResponse;
import com.autonomousapi.core.geo.dto.GpsPingBatchAccepted;
import com.autonomousapi.core.geo.dto.GpsPingBatchRequest;
import com.autonomousapi.core.geo.dto.GpsPingRequest;
import com.autonomousapi.core.geo.dto.PlaceResponse;
import com.autonomousapi.core.geo.dto.RouteResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Mesmo padrão de cache em memória do {@link com.autonomousapi.core.routeplan.RouteMatrixService}
     *  (TTL curto, single-instance) — só o resultado de SUCESSO entra no cache: se o provedor
     *  caiu e a chamada degradou pra lista vazia/indisponível, isso nunca é cacheado, senão o
     *  "fora do ar" ficaria congelado pro TTL inteiro mesmo depois do provedor voltar. */
    private static final Duration GEOCODE_CACHE_TTL = Duration.ofHours(1);
    private static final Duration CHARGING_STATIONS_CACHE_TTL = Duration.ofMinutes(5);

    private final Map<String, CachedValue<List<PlaceResponse>>> geocodeCache = new ConcurrentHashMap<>();
    private final Map<String, CachedValue<ChargingStationsResponse>> chargingStationsCache = new ConcurrentHashMap<>();

    private record CachedValue<T>(T value, Instant expiraEm) {
        boolean valido() {
            return expiraEm.isAfter(Instant.now());
        }
    }

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
     * Versão em lote de {@link #ingestGpsPing} (ADR 0019, pré-requisito A) — usada por
     * {@code TripService#submitPings} pra encaminhar o lote inteiro que o app manda numa
     * chamada só, em vez de uma requisição HTTP por ping. Diferente de {@link
     * #ingestGpsPing}, degrada em vez de lançar: em caso de falha devolve 0 aceitos, e quem
     * chama já tem o mesmo contrato pra decidir o quê fazer (fila offline do app reenvia o
     * lote inteiro — ver ADR 0019, "Anexo — contrato da ingestão em lote").
     */
    public int ingestGpsPingBatch(List<GpsPingRequest> pings) {
        try {
            GpsPingBatchAccepted response = client
                    .post()
                    .uri("/internal/v1/gps/pings/batch")
                    .header("X-Service-Token", serviceToken)
                    .body(new GpsPingBatchRequest(pings))
                    .retrieve()
                    .body(GpsPingBatchAccepted.class);
            return response != null ? response.accepted() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * Estações de recarga elétrica (spec 06, item 1). RNF011 aplicado aqui também: falha de
     * rede/timeout/geo-api fora do ar vira resposta "indisponível" (lista vazia,
     * providerAvailable=false), nunca propaga exceção pro controller — a tela do usuário não
     * pode quebrar por causa de um provedor terceiro instável.
     */
    public ChargingStationsResponse chargingStations(Double lat, Double lon, Double radiusKm) {
        String chave = lat + ":" + lon + ":" + radiusKm;
        CachedValue<ChargingStationsResponse> cached = chargingStationsCache.get(chave);
        if (cached != null && cached.valido()) {
            return cached.value();
        }

        ChargingStationsResponse resultado;
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
            resultado = response != null ? response.toPublic() : ChargingStationsResponse.indisponivel();
        } catch (Exception ex) {
            resultado = ChargingStationsResponse.indisponivel();
        }

        if (resultado.providerAvailable()) {
            chargingStationsCache.put(
                    chave, new CachedValue<>(resultado, Instant.now().plus(CHARGING_STATIONS_CACHE_TTL)));
        }
        return resultado;
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
     * Matriz de distância/duração real entre N pontos (spec 02, "Evolução pendente"),
     * consumida pelo solver VRP ({@code RouteMatrixService}). Mesmo padrão de degradação do
     * {@link #route}: geo-api inacessível vira {@code available=false} em vez de propagar
     * exceção — quem chama decide o fallback (heurística haversine), não este cliente.
     */
    public DistanceMatrixResponse distanceMatrix(List<DistanceMatrixRequest.Point> points) {
        try {
            DistanceMatrixResponse response = client
                    .post()
                    .uri("/internal/v1/table")
                    .header("X-Service-Token", serviceToken)
                    .body(new DistanceMatrixRequest(points))
                    .retrieve()
                    .body(DistanceMatrixResponse.class);
            return response != null
                    ? response
                    : DistanceMatrixResponse.indisponivel("Serviço de roteamento não respondeu.");
        } catch (Exception ex) {
            return DistanceMatrixResponse.indisponivel("Serviço de roteamento indisponível no momento.");
        }
    }

    /**
     * Endereço -> coordenada, restrito à área do piloto. Falha vira lista vazia: para quem
     * digita no campo de busca, "não achei" e "o geocoder caiu" levam à mesma ação prática
     * (tentar outro termo), e um erro na tela só atrapalharia.
     */
    public List<PlaceResponse> geocode(String query) {
        String chave = query.trim().toLowerCase();
        CachedValue<List<PlaceResponse>> cached = geocodeCache.get(chave);
        if (cached != null && cached.valido()) {
            return cached.value();
        }

        List<PlaceResponse> resultado;
        try {
            List<GeoPlace> lugares = client
                    .get()
                    .uri(builder -> builder.path("/internal/v1/geocode").queryParam("q", query).build())
                    .header("X-Service-Token", serviceToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            resultado = lugares != null ? lugares.stream().map(GeoPlace::toPublic).toList() : List.of();
        } catch (Exception ex) {
            resultado = List.of();
        }

        // Lista vazia nunca entra em cache: não dá pra distinguir "não achei esse endereço" de
        // "o Nominatim caiu" só pelo resultado — melhor sempre tentar de novo do que arriscar
        // congelar uma falha temporária pelo TTL inteiro.
        if (!resultado.isEmpty()) {
            geocodeCache.put(chave, new CachedValue<>(resultado, Instant.now().plus(GEOCODE_CACHE_TTL)));
        }
        return resultado;
    }
}
