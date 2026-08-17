package com.autonomousapi.core.routeplan;

import com.google.ortools.Loader;
import com.google.ortools.constraintsolver.Assignment;
import com.google.ortools.constraintsolver.FirstSolutionStrategy;
import com.google.ortools.constraintsolver.RoutingIndexManager;
import com.google.ortools.constraintsolver.RoutingModel;
import com.google.ortools.constraintsolver.RoutingSearchParameters;
import com.google.ortools.constraintsolver.main;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TSP de caminho aberto (não volta ao início) sobre uma matriz de distância, via Google
 * OR-Tools (spec 02: "nunca implementar o solver do zero"). Usado por
 * {@code RoutePlanService#suggestOrder} no lugar do nearest-neighbor greedy da v1.
 *
 * <p>Truque padrão para "caminho aberto" no OR-Tools (que nativamente resolve ciclos): um nó
 * fantasma, ligado a todo mundo com custo zero, marcado como único destino possível — o
 * solver então minimiza só as arestas reais, sem custo de "voltar para o início".
 */
@Component
public class OrToolsRouteOptimizer {

    private static final Logger log = LoggerFactory.getLogger(OrToolsRouteOptimizer.class);

    @PostConstruct
    void carregarBibliotecaNativa() {
        Loader.loadNativeLibraries();
    }

    /**
     * @param distanciasM matriz N×N de distância em metros, ordem correspondente à lista de
     *                    paradas original.
     * @return ordem dos índices (0..N-1) que minimiza a distância total do caminho começando
     *         no índice 0; {@code null} se o solver não encontrar solução (o chamador decide
     *         o fallback).
     */
    public List<Integer> ordenar(double[][] distanciasM) {
        int n = distanciasM.length;
        if (n <= 1) {
            List<Integer> unico = new ArrayList<>();
            if (n == 1) unico.add(0);
            return unico;
        }

        int noFantasma = n;
        RoutingIndexManager manager =
                new RoutingIndexManager(n + 1, 1, new int[] {0}, new int[] {noFantasma});
        RoutingModel routing = new RoutingModel(manager);

        int transitCallbackIndex = routing.registerTransitCallback((fromIndex, toIndex) -> {
            int fromNode = manager.indexToNode(fromIndex);
            int toNode = manager.indexToNode(toIndex);
            if (fromNode == noFantasma || toNode == noFantasma) {
                return 0;
            }
            return Math.round(distanciasM[fromNode][toNode]);
        });
        routing.setArcCostEvaluatorOfAllVehicles(transitCallbackIndex);

        RoutingSearchParameters searchParameters = main.defaultRoutingSearchParameters().toBuilder()
                .setFirstSolutionStrategy(FirstSolutionStrategy.Value.PATH_CHEAPEST_ARC)
                .build();

        Assignment solution = routing.solveWithParameters(searchParameters);
        if (solution == null) {
            log.warn("OR-Tools não encontrou solução para matriz {}x{} — chamador decide o fallback.", n, n);
            return null;
        }

        List<Integer> ordem = new ArrayList<>();
        long index = routing.start(0);
        while (!routing.isEnd(index)) {
            int node = manager.indexToNode(index);
            if (node != noFantasma) {
                ordem.add(node);
            }
            index = solution.value(routing.nextVar(index));
        }
        return ordem;
    }
}
