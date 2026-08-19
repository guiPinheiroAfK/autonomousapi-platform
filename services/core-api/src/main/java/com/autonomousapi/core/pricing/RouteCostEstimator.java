package com.autonomousapi.core.pricing;

import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fórmula de custo estimado v1 — só combustível/energia (spec 09): distância × (1/consumo) ×
 * preço de referência. {@code valorSugerido} soma a margem por tenant sobre o custo estimado.
 *
 * Branch explícito por {@code tipoCombustivel} (lido de {@code vehicle.atributos.combustivel},
 * mesma chave que {@code DemoDataSeeder} já usa): combustão lê {@code consumoMedioKmPorLitro}
 * e o preço por litro; elétrico lê {@code kmPorKwh} e o preço por kWh. Sem esse branch, um
 * veículo elétrico calcularia custo errado silenciosamente lendo o campo/preço de combustão.
 *
 * {@link #estimar} devolve {@link Optional#empty()} (nunca lança) quando falta qualquer
 * pré-requisito — tipo de combustível não cadastrado, consumo não preenchido, sem preço de
 * referência pro tenant — porque custo estimado é informação complementar, nunca deveria
 * derrubar um preview de rota ou a criação de um TRANSFER.
 */
@Component
public class RouteCostEstimator {

    public static final String PRICING_FORMULA_VERSION = "v1";
    private static final String CHAVE_TIPO_COMBUSTIVEL = "combustivel";
    private static final String TIPO_ELETRICO = "eletrico";

    private final FuelPriceReferenceRepository fuelPrices;
    private final TenantRepository tenants;

    public RouteCostEstimator(FuelPriceReferenceRepository fuelPrices, TenantRepository tenants) {
        this.fuelPrices = fuelPrices;
        this.tenants = tenants;
    }

    public record Estimate(BigDecimal custoEstimado, BigDecimal valorSugerido) {
    }

    public Optional<Estimate> estimar(UUID tenantId, Vehicle vehicle, double distanciaKm) {
        Map<String, Object> atributos = vehicle.getAtributos();
        Object tipoCombustivelObj = atributos.get(CHAVE_TIPO_COMBUSTIVEL);
        if (tipoCombustivelObj == null) {
            return Optional.empty();
        }
        String tipoCombustivel = tipoCombustivelObj.toString();

        Object consumoObj = TIPO_ELETRICO.equals(tipoCombustivel)
                ? atributos.get("kmPorKwh")
                : atributos.get("consumoMedioKmPorLitro");
        if (consumoObj == null) {
            return Optional.empty();
        }
        BigDecimal consumo = new BigDecimal(consumoObj.toString());
        if (consumo.signum() <= 0) {
            return Optional.empty();
        }

        Optional<FuelPriceReference> precoRef =
                fuelPrices.findByTenantIdAndTipoCombustivel(tenantId, tipoCombustivel);
        if (precoRef.isEmpty()) {
            return Optional.empty();
        }

        BigDecimal custoEstimado = BigDecimal.valueOf(distanciaKm)
                .divide(consumo, 6, RoundingMode.HALF_UP)
                .multiply(precoRef.get().getPreco())
                .setScale(2, RoundingMode.HALF_UP);

        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        BigDecimal valorSugerido = custoEstimado
                .multiply(BigDecimal.ONE.add(tenant.getMargemPadrao()))
                .setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new Estimate(custoEstimado, valorSugerido));
    }
}
