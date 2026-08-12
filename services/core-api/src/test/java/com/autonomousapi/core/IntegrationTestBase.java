package com.autonomousapi.core;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes que precisam de um Postgres de verdade.
 *
 * Por que existe: teste unitário com repositório mockado não executa SQL nenhum, então não
 * pega erro que só existe no banco — migration quebrada, JPQL inválido, tipo que estoura o
 * range da coluna. Um bug assim (LocalDate.MIN, que vira data fora do range de `date` do
 * Postgres) passou por 24 testes verdes e só apareceu em runtime.
 *
 * Duas formas de fornecer o banco, nessa ordem:
 *
 * 1. Variável CORE_TEST_DB_URL (+ CORE_TEST_DB_USER / CORE_TEST_DB_PASSWORD). É o que o CI
 *    usa, via service container do GitHub Actions, e serve para qualquer ambiente onde o
 *    Testcontainers não consegue falar com o daemon — caso real: Docker Desktop no Windows
 *    recusando o handshake pelo named pipe mesmo com o Docker no ar.
 * 2. Sem essa variável, sobe um Postgres via Testcontainers (zero config para quem clona).
 *
 * O container é estático: sobe uma vez por execução da suíte, não por classe de teste.
 */
@SpringBootTest
public abstract class IntegrationTestBase {

    private static final String URL_EXTERNA = System.getenv("CORE_TEST_DB_URL");

    private static PostgreSQLContainer<?> postgres;

    private static synchronized PostgreSQLContainer<?> container() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16");
            postgres.start(); // Ryuk derruba no fim da suíte; não há stop() manual.
        }
        return postgres;
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        if (URL_EXTERNA != null && !URL_EXTERNA.isBlank()) {
            registry.add("spring.datasource.url", () -> URL_EXTERNA);
            registry.add("spring.datasource.username", () -> envOu("CORE_TEST_DB_USER", "autonomousapi"));
            registry.add("spring.datasource.password", () -> envOu("CORE_TEST_DB_PASSWORD", "autonomousapi"));
        } else {
            registry.add("spring.datasource.url", () -> container().getJdbcUrl());
            registry.add("spring.datasource.username", () -> container().getUsername());
            registry.add("spring.datasource.password", () -> container().getPassword());
        }
    }

    private static String envOu(String chave, String padrao) {
        String valor = System.getenv(chave);
        return valor == null || valor.isBlank() ? padrao : valor;
    }
}
