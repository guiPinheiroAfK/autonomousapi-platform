package com.autonomousapi.core.vehicle.marketvalue.fipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.autonomousapi.core.vehicle.VehicleType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FipeMatchingServiceTest {

    private final FipeMatchingService service = new FipeMatchingService();

    @Test
    void tipoFipeSoResolveCarroEMoto() {
        assertEquals(Optional.of("carros"), service.tipoFipe(VehicleType.CARRO));
        assertEquals(Optional.of("motos"), service.tipoFipe(VehicleType.MOTO));
        assertTrue(service.tipoFipe(VehicleType.VAN).isEmpty());
        assertTrue(service.tipoFipe(VehicleType.CAMINHAO).isEmpty());
        assertTrue(service.tipoFipe(VehicleType.ONIBUS).isEmpty());
        assertTrue(service.tipoFipe(null).isEmpty());
    }

    @Test
    void encontraMarcaPorNomeComVariacaoDeSigla() {
        List<FipeMarca> marcas = List.of(
                new FipeMarca("59", "VW - VolksWagen"), new FipeMarca("21", "Fiat"), new FipeMarca("7", "BMW"));

        Optional<FipeMarca> resultado = service.matchMarca(marcas, "Volkswagen");

        assertEquals("59", resultado.orElseThrow().codigo());
    }

    @Test
    void naoResolveMarcaQuandoAmbiguo() {
        List<FipeMarca> marcas = List.of(new FipeMarca("1", "Fiat"), new FipeMarca("2", "Fiat Professional"));

        // "FIAT" é substring de ambas normalizado — ambíguo, não resolve.
        assertTrue(service.matchMarca(marcas, "Fiat").isEmpty());
    }

    @Test
    void encontraModeloPorPrefixoUnico() {
        List<FipeModeloResumo> modelos = List.of(
                new FipeModeloResumo(1, "STRADA FREEDOM 1.3 FLEX 8V CD"),
                new FipeModeloResumo(2, "SAVEIRO ROBUST 1.6 MSI FLEX 8V CS"));

        Optional<FipeModeloResumo> resultado = service.matchModelo(modelos, "Strada");

        assertEquals(1, resultado.orElseThrow().codigo());
    }

    @Test
    void encontraModeloPorNomeExato() {
        List<FipeModeloResumo> modelos = List.of(new FipeModeloResumo(5, "HR"), new FipeModeloResumo(6, "HR LONGO"));

        Optional<FipeModeloResumo> resultado = service.matchModelo(modelos, "HR");

        assertEquals(5, resultado.orElseThrow().codigo());
    }

    @Test
    void naoResolveModeloQuandoNenhumCandidato() {
        List<FipeModeloResumo> modelos = List.of(new FipeModeloResumo(1, "GOL 1.0 FLEX 8V"));

        assertTrue(service.matchModelo(modelos, "Onix").isEmpty());
    }

    @Test
    void encontraAnoPeloPrefixoDoAnoModelo() {
        List<FipeAno> anos = List.of(new FipeAno("2023-1", "2023 Gasolina"), new FipeAno("2022-3", "2022 Diesel"));

        Optional<FipeAno> resultado = service.matchAno(anos, 2022);

        assertEquals("2022-3", resultado.orElseThrow().codigo());
    }

    @Test
    void naoResolveAnoQuandoNulo() {
        List<FipeAno> anos = List.of(new FipeAno("2022-3", "2022 Diesel"));

        assertTrue(service.matchAno(anos, null).isEmpty());
    }
}
