package com.autonomousapi.core.routeplan;

/** ROTA (padrão, multi-parada) ou TRANSFER (trajeto único A→B com valor combinado —
 *  exatamente 2 paradas, origem/COLETA e destino/ENTREGA). Mesmo modelo de dados pros
 *  dois; só a renderização no app do motorista muda (cartão único vs. lista de paradas). */
public enum RouteCategoria {
    ROTA,
    TRANSFER
}
