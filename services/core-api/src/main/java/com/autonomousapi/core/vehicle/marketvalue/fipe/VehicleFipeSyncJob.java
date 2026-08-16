package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.VehicleType;
import com.autonomousapi.core.vehicle.marketvalue.VehicleMarketValue;
import com.autonomousapi.core.vehicle.marketvalue.VehicleMarketValueRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consulta FIPE automática (spec 06, item 2): resolve marca/modelo/ano do veículo pro
 * catálogo da FIPE e lança um novo {@link VehicleMarketValue} quando o match é confiável
 * (ver {@link FipeMatchingService}). Roda uma vez por semana — "nunca calculado/chamado em
 * tempo real na tela do usuário" é regra explícita da spec.
 *
 * Lançamento manual pelo gestor continua funcionando do mesmo jeito e tem prioridade: se
 * já existe um valor recente, o job não sobrescreve.
 */
@Component
public class VehicleFipeSyncJob {

    private static final Logger logger = LoggerFactory.getLogger(VehicleFipeSyncJob.class);

    /** Não reconsulta um veículo que já tem valor lançado (manual ou automático) recente. */
    private static final int DIAS_PARA_REATUALIZAR = 30;

    private final VehicleRepository vehicles;
    private final VehicleMarketValueRepository marketValues;
    private final FipeClient fipeClient;
    private final FipeMatchingService matching;

    public VehicleFipeSyncJob(
            VehicleRepository vehicles,
            VehicleMarketValueRepository marketValues,
            FipeClient fipeClient,
            FipeMatchingService matching) {
        this.vehicles = vehicles;
        this.marketValues = marketValues;
        this.fipeClient = fipeClient;
        this.matching = matching;
    }

    /** Toda segunda às 03:00 (horário do servidor) — fora de horário comercial. */
    @Scheduled(cron = "0 0 3 * * MON")
    public void run() {
        List<Vehicle> candidatos = vehicles.findAllByTipoIn(List.of(VehicleType.CARRO, VehicleType.MOTO));
        int atualizados = 0;
        for (Vehicle vehicle : candidatos) {
            try {
                if (sincronizar(vehicle)) {
                    atualizados++;
                }
            } catch (Exception ex) {
                // Um veículo com nome estranho ou catálogo indisponível não pode derrubar o
                // job inteiro — cada veículo é uma tentativa independente.
                logger.warn("Falha ao sincronizar FIPE do veículo {}: {}", vehicle.getId(), ex.getMessage());
            }
        }
        logger.info("Sincronização FIPE: {} veículo(s) atualizado(s) de {} candidato(s)", atualizados, candidatos.size());
    }

    private boolean sincronizar(Vehicle vehicle) {
        if (!precisaAtualizar(vehicle.getId())) {
            return false;
        }

        Optional<String> tipoFipe = matching.tipoFipe(vehicle.getTipo());
        if (tipoFipe.isEmpty()) {
            return false;
        }

        Optional<FipeMarca> marca = matching.matchMarca(fipeClient.marcas(tipoFipe.get()), vehicle.getBrand());
        if (marca.isEmpty()) {
            return false;
        }

        Optional<FipeModeloResumo> modelo = matching.matchModelo(
                fipeClient.modelos(tipoFipe.get(), marca.get().codigo()), vehicle.getModel());
        if (modelo.isEmpty()) {
            return false;
        }

        Optional<FipeAno> ano = matching.matchAno(
                fipeClient.anos(tipoFipe.get(), marca.get().codigo(), String.valueOf(modelo.get().codigo())),
                vehicle.getModelYear());
        if (ano.isEmpty()) {
            return false;
        }

        FipeValor valor = fipeClient.valor(
                tipoFipe.get(), marca.get().codigo(), String.valueOf(modelo.get().codigo()), ano.get().codigo());
        if (valor == null || valor.valor() == null) {
            return false;
        }

        BigDecimal valorFipe = parseValor(valor.valor());
        marketValues.save(new VehicleMarketValue(vehicle.getId(), valorFipe, LocalDate.now(), valor.codigoFipe()));
        return true;
    }

    private boolean precisaAtualizar(java.util.UUID vehicleId) {
        return marketValues
                .findFirstByVehicleIdOrderByDataReferenciaDesc(vehicleId)
                .map(v -> ChronoUnit.DAYS.between(v.getDataReferencia(), LocalDate.now()) >= DIAS_PARA_REATUALIZAR)
                .orElse(true);
    }

    /** "R$ 85.608,00" → 85608.00 — formato brasileiro (ponto de milhar, vírgula decimal). */
    static BigDecimal parseValor(String valor) {
        String somenteNumeros = valor.replaceAll("[^0-9.,]", "");
        String semSeparadorDeMilhar = somenteNumeros.replace(".", "").replace(",", ".");
        return new BigDecimal(semSeparadorDeMilhar);
    }
}
