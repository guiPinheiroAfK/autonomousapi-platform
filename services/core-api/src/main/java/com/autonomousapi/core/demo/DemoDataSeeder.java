package com.autonomousapi.core.demo;

import com.autonomousapi.core.billing.BillingSource;
import com.autonomousapi.core.billing.Subscription;
import com.autonomousapi.core.billing.SubscriptionRepository;
import com.autonomousapi.core.billing.SubscriptionStatus;
import com.autonomousapi.core.driver.Driver;
import com.autonomousapi.core.driver.DriverRepository;
import com.autonomousapi.core.driver.DriverStatus;
import com.autonomousapi.core.tenant.Tenant;
import com.autonomousapi.core.tenant.TenantRepository;
import com.autonomousapi.core.user.Role;
import com.autonomousapi.core.user.User;
import com.autonomousapi.core.user.UserRepository;
import com.autonomousapi.core.vehicle.Vehicle;
import com.autonomousapi.core.vehicle.VehicleRepository;
import com.autonomousapi.core.vehicle.VehicleStatus;
import com.autonomousapi.core.vehicle.VehicleType;
import com.autonomousapi.core.expense.ExpenseCategory;
import com.autonomousapi.core.expense.ExpenseEntry;
import com.autonomousapi.core.expense.ExpenseEntryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dado de demonstração — evolução do pitch (specs/00-visao-geral.md): a primeira
 * frota real na plataforma é o segmento #2 do pitch, "empresas de entrega/locação/
 * transporte" (cliente pagante, hoje), aqui personificado como a RotaCerta.
 *
 * PARA REMOVER TUDO ISSO: apague este arquivo (pacote inteiro {@code demo/}). Não
 * há nenhum outro ponto do código que dependa dele — só roda com o profile Spring
 * "demo" explicitamente ativado (nunca em produção nem no dev padrão):
 *
 *   SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
 *   # ou, via docker-compose: CORE_PROFILES=demo docker compose ... up
 *
 * Idempotente: não roda de novo se já existir algum tenant (evita duplicar ao reiniciar).
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private final TenantRepository tenants;
    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final ExpenseEntryRepository expenses;
    private final SubscriptionRepository subscriptions;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            TenantRepository tenants,
            UserRepository users,
            VehicleRepository vehicles,
            DriverRepository drivers,
            ExpenseEntryRepository expenses,
            SubscriptionRepository subscriptions,
            PasswordEncoder passwordEncoder) {
        this.tenants = tenants;
        this.users = users;
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.expenses = expenses;
        this.subscriptions = subscriptions;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenants.count() > 0) return;

        Tenant rotaCerta = tenants.save(new Tenant("RotaCerta Entregas Expressas"));

        users.save(new User(
                rotaCerta.getId(), "demo@rotacerta.com.br",
                passwordEncoder.encode("demo12345"), Role.GESTOR_FROTA));

        // ACTIVE direto, sem passar pelo trial: demo é ambiente permanente de
        // demonstração, não deve nunca ser bloqueado pelo SubscriptionGate.
        Subscription demoSub = new Subscription(rotaCerta.getId(), BillingSource.WEB_STRIPE, "demo-customer");
        demoSub.applyStripeUpdate(null, SubscriptionStatus.ACTIVE, null);
        subscriptions.save(demoSub);

        seedVehicles(rotaCerta.getId());
        seedDrivers(rotaCerta.getId());
    }

    private void seedVehicles(UUID tenantId) {
        // plate, brand, model, ano, odômetro, status, próxima manutenção (data,km)
        // — data/km nulos = sem manutenção agendada (não entra em nenhum alerta).
        record V(String plate, String brand, String model, int year, int km, VehicleStatus status,
                VehicleType tipo, LocalDate proxData, Integer proxKm) {}

        LocalDate today = LocalDate.now();
        List<V> defs = List.of(
                new V("RTC1A23", "Fiat", "Fiorino", 2022, 32000, VehicleStatus.ATIVO, VehicleType.VAN, null, 40000),
                new V("RTC1B45", "Fiat", "Strada", 2023, 18500, VehicleStatus.ATIVO, VehicleType.CARRO,
                        today.plusDays(60), null),
                new V("RTC1C67", "Volkswagen", "Saveiro", 2021, 54200, VehicleStatus.ATIVO, VehicleType.CARRO,
                        null, 55000),
                new V("RTC1D89", "Renault", "Kangoo", 2020, 71300, VehicleStatus.ATIVO, VehicleType.VAN,
                        today.plusDays(10), null),
                new V("RTC1E12", "Fiat", "Doblo", 2019, 88900, VehicleStatus.MANUTENCAO, VehicleType.VAN, null, null),
                new V("RTC1F34", "Hyundai", "HR", 2022, 41200, VehicleStatus.ATIVO, VehicleType.CAMINHAO,
                        today.minusDays(5), null),
                new V("RTC1G56", "Iveco", "Daily", 2021, 62700, VehicleStatus.ATIVO, VehicleType.CAMINHAO, null, null),
                new V("RTC1H78", "Volkswagen", "Delivery Express", 2023, 15400, VehicleStatus.ATIVO,
                        VehicleType.CAMINHAO, today.plusDays(120), null),
                new V("RTC1I90", "Mercedes-Benz", "Sprinter", 2020, 95600, VehicleStatus.MANUTENCAO, VehicleType.VAN,
                        null, null),
                new V("RTC1J12", "Honda", "CG 160", 2023, 8700, VehicleStatus.ATIVO, VehicleType.MOTO, null, 9000),
                new V("RTC1K34", "Yamaha", "Factor 125", 2022, 21300, VehicleStatus.ATIVO, VehicleType.MOTO,
                        null, null),
                new V("RTC1L56", "Fiat", "Fiorino", 2018, 132000, VehicleStatus.INATIVO, VehicleType.VAN,
                        null, null));

        for (V d : defs) {
            Vehicle v = new Vehicle(tenantId, d.plate(), d.brand(), d.model(), d.year(), d.km());
            v.update(d.plate(), d.brand(), d.model(), d.year(), d.km(), d.status(), d.tipo(), d.proxData(), d.proxKm(),
                    atributosDe(d.brand(), d.model()));
            vehicles.save(v);

            // Histórico de custo só nos veículos operacionais (ATIVO/MANUTENCAO) — reflete
            // custo real de operação, não de um veículo já desativado da frota.
            if (d.status() != VehicleStatus.INATIVO) {
                seedCostHistory(tenantId, v.getId(), d.plate(), d.km());
            }
        }
    }

    /**
     * Atributos que variam por tipo de veículo (ADR 0008). É exatamente o caso que motivou o
     * jsonb: moto tem cilindrada, elétrico teria autonomia e conector, utilitário tem
     * capacidade de carga — nenhum desses faz sentido como coluna preenchida para todo mundo.
     */
    private static Map<String, Object> atributosDe(String marca, String modelo) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        boolean ehMoto = "Honda".equals(marca) || "Yamaha".equals(marca);

        if (ehMoto) {
            attrs.put("categoria", "motocicleta");
            attrs.put("cilindradas", modelo.contains("160") ? 162 : 125);
            attrs.put("combustivel", "flex");
            attrs.put("consumoMedioKmPorLitro", 32.0); // spec 09 — dado manual, gestor preenche no cadastro
        } else {
            attrs.put("categoria", "utilitario");
            attrs.put("combustivel", "diesel");
            attrs.put("capacidadeCargaKg",
                    modelo.contains("Sprinter") || modelo.contains("Daily") ? 1500 : 650);
            attrs.put("consumoMedioKmPorLitro", 9.5);
        }
        return attrs;
    }

    /**
     * Cada veículo recebe uma quantidade e um valor de lançamento diferentes — derivados
     * do próprio odômetro/placa (determinístico, sem lib de aleatoriedade) — para o
     * histórico não parecer um template copiado igual em todo veículo.
     */
    private void seedCostHistory(UUID tenantId, UUID vehicleId, String plate, int odometerKm) {
        record C(int diasAtras, ExpenseCategory categoria, String valor, String descricao) {}

        List<C> pool = List.of(
                new C(88, ExpenseCategory.COMBUSTIVEL, "215.40", "Abastecimento Posto Ipiranga"),
                new C(74, ExpenseCategory.COMBUSTIVEL, "198.90", "Abastecimento Posto Shell BR-116"),
                new C(61, ExpenseCategory.MANUTENCAO, "380.00", "Troca de óleo e filtro"),
                new C(49, ExpenseCategory.PEDAGIO, "48.50", "Pedágio Rodovia Anhanguera"),
                new C(37, ExpenseCategory.COMBUSTIVEL, "227.60", "Abastecimento Posto Ipiranga"),
                new C(26, ExpenseCategory.MANUTENCAO, "165.00", "Alinhamento e balanceamento"),
                new C(14, ExpenseCategory.LAVAGEM, "35.00", "Lavagem e higienização"),
                new C(4, ExpenseCategory.COMBUSTIVEL, "241.10", "Abastecimento Posto Shell BR-116"));

        // Fator de custo proporcional ao uso (~0.7x a 1.2x) e quantidade de lançamentos
        // (4 a 8) variando por veículo — mesma ideia, números diferentes por veículo.
        double factor = 0.7 + (odometerKm % 50_000) / 100_000.0;
        int count = 4 + Math.abs(plate.hashCode()) % (pool.size() - 3);

        for (C c : pool.subList(pool.size() - count, pool.size())) {
            BigDecimal valor = new BigDecimal(c.valor())
                    .multiply(BigDecimal.valueOf(factor))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal litros = c.categoria() == ExpenseCategory.COMBUSTIVEL
                    ? valor.divide(BigDecimal.valueOf(6.2), 3, java.math.RoundingMode.HALF_UP)
                    : null;
            expenses.save(new ExpenseEntry(
                    tenantId, vehicleId, c.categoria(), valor, c.descricao(),
                    LocalDate.now().minusDays(c.diasAtras()), litros, litros != null ? odometerKm : null));
        }
    }

    private void seedDrivers(UUID tenantId) {
        // name, cnh, phone, status, validade da CNH — nula = não entra no alerta de CNH.
        record D(String name, String cnh, String phone, DriverStatus status, LocalDate cnhValidade) {}

        LocalDate today = LocalDate.now();
        List<D> defs = List.of(
                new D("Eduardo Ramos", "11223344556", "11987651234", DriverStatus.ATIVO, today.plusDays(8)),
                new D("Juliana Martins", "22334455667", "11987652345", DriverStatus.ATIVO, today.plusDays(200)),
                new D("Anderson Souza", "33445566778", "11987653456", DriverStatus.ATIVO, today.minusDays(2)),
                new D("Patrícia Lima", "44556677889", "11987654567", DriverStatus.ATIVO, null),
                new D("Thiago Nogueira", "55667788990", "11987655678", DriverStatus.ATIVO, today.plusDays(25)),
                new D("Camila Duarte", "66778899001", "11987656789", DriverStatus.ATIVO, today.plusDays(300)),
                new D("Roberto Alves", "77889900112", "11987657890", DriverStatus.INATIVO, null));

        for (D d : defs) {
            Driver driver = new Driver(tenantId, d.name(), d.cnh(), d.phone());
            driver.update(d.name(), d.cnh(), d.phone(), d.status(), d.cnhValidade(), null);
            drivers.save(driver);
        }
    }
}
