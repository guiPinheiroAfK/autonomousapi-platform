package com.autonomousapi.core.routeplan;

import com.autonomousapi.core.geo.GeoApiClient;
import com.autonomousapi.core.geo.dto.DistanceMatrixRequest;
import com.autonomousapi.core.geo.dto.DistanceMatrixResponse;
import com.autonomousapi.core.routeplan.dto.StopInput;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Matriz de distância real (metros) entre paradas, para o solver VRP (spec 02, "Evolução
 * pendente: distância real via OSRM /table"). Duas salvaguardas exigidas pelo spec junto
 * dessa troca:
 *
 * <ul>
 *   <li><b>Fallback com log</b> — se o {@code /table} do geo-api cair ou devolver algum par
 *       sem ligação viária, cai para a distância haversine (linha reta) usada na v1, mas
 *       sempre registrado via {@link #log}, nunca silencioso.
 *   <li><b>Cache por conjunto de pontos</b> — evita recalcular a matriz do zero a cada ajuste
 *       de "sugerir ordem" na mesma tela. Em memória, TTL curto, mesmo padrão single-instance
 *       de {@code TypingIndicatorService} (Redis é a evolução natural se precisar de múltiplas
 *       instâncias, ADR 0007 já traz Redis como dependência do repo).
 * </ul>
 */
@Component
public class RouteMatrixService {

    private static final Logger log = LoggerFactory.getLogger(RouteMatrixService.class);
    private static final double RAIO_TERRA_KM = 6371;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final GeoApiClient geoApiClient;
    private final Map<String, CachedMatrix> cache = new ConcurrentHashMap<>();

    public RouteMatrixService(GeoApiClient geoApiClient) {
        this.geoApiClient = geoApiClient;
    }

    public record Matriz(double[][] distanciasM, String fonte) {
    }

    private record CachedMatrix(Matriz matriz, Instant expiraEm) {
    }

    /**
     * {@code pontos} precisa já vir resolvido (label/lat/lon definidos — ver
     * {@code RoutePlanService#resolveStops}). Ordem da matriz de saída é a mesma da lista de
     * entrada.
     */
    public Matriz obter(List<StopInput> pontos) {
        if (pontos.size() < 2) {
            return new Matriz(new double[pontos.size()][pontos.size()], "TRIVIAL");
        }

        String chave = chaveDoConjunto(pontos);
        CachedMatrix cached = cache.get(chave);
        if (cached != null && cached.expiraEm().isAfter(Instant.now())) {
            return cached.matriz();
        }

        Matriz resultado = buscarNoGeoApi(pontos);
        cache.put(chave, new CachedMatrix(resultado, Instant.now().plus(CACHE_TTL)));
        return resultado;
    }

    private Matriz buscarNoGeoApi(List<StopInput> pontos) {
        DistanceMatrixResponse resposta = geoApiClient.distanceMatrix(pontos.stream()
                .map(p -> new DistanceMatrixRequest.Point(p.lat(), p.lon()))
                .toList());

        if (!resposta.available() || contemCelulaAusente(resposta.distancesM(), pontos.size())) {
            log.warn(
                    "OSRM /table indisponível ou incompleto (motivo={}) — usando fallback"
                            + " haversine para {} pontos.",
                    resposta.unavailableReason(),
                    pontos.size());
            return new Matriz(matrizHaversine(pontos), "HAVERSINE_FALLBACK");
        }

        double[][] metros = new double[pontos.size()][pontos.size()];
        for (int i = 0; i < pontos.size(); i++) {
            for (int j = 0; j < pontos.size(); j++) {
                metros[i][j] = resposta.distancesM().get(i).get(j);
            }
        }
        return new Matriz(metros, "OSRM_TABLE");
    }

    /** Qualquer célula nula (par sem ligação viária) derruba a matriz inteira para o fallback
     *  — mais simples e mais seguro do que tentar remendar só a célula ausente. */
    private boolean contemCelulaAusente(List<List<Double>> distancias, int n) {
        if (distancias == null || distancias.size() != n) {
            return true;
        }
        for (List<Double> linha : distancias) {
            if (linha == null || linha.size() != n || linha.stream().anyMatch(v -> v == null)) {
                return true;
            }
        }
        return false;
    }

    private double[][] matrizHaversine(List<StopInput> pontos) {
        int n = pontos.size();
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = i == j
                        ? 0
                        : haversineMetros(
                                pontos.get(i).lat(), pontos.get(i).lon(),
                                pontos.get(j).lat(), pontos.get(j).lon());
            }
        }
        return m;
    }

    private static double haversineMetros(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return RAIO_TERRA_KM * 1000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Chave estável por conjunto de pontos: ordem importa (é a ordem que sai na resposta),
     *  então não ordenamos — só concatenamos lat/lon com precisão fixa. */
    private String chaveDoConjunto(List<StopInput> pontos) {
        return pontos.stream()
                .map(p -> String.format("%.6f,%.6f", p.lat(), p.lon()))
                .collect(Collectors.joining("|"));
    }
}
