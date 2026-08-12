package com.autonomousapi.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.autonomousapi.core.vehicle.cost.dto.MonthlyCostResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Trava a serialização do cache.
 *
 * Regressão real: a primeira versão do CacheConfig montava o ObjectMapper sem
 * activateDefaultTyping. Gravava no Redis sem informação de tipo, e na leitura o valor
 * voltava como LinkedHashMap em vez de List&lt;MonthlyCostResponse&gt; — a resposta então
 * estourava com "Could not write JSON: ClassCastException", **apenas no cache hit**.
 *
 * O que deixou o bug passar: a verificação manual cronometrou a segunda chamada com
 * `curl -o /dev/null` e olhou só o tempo, nunca o corpo. Este teste força a ida e volta
 * completa e compara o objeto reconstruído, que é o que aquela medição não fez.
 */
class CacheSerializationTest {

    private final SerializationPair<Object> par =
            new CacheConfig().cacheConfiguration().getValueSerializationPair();

    /** Faz a ida e volta pelo mesmo par de serialização que o cache usa em produção. */
    private Object idaEVolta(Object valor) {
        return par.read(par.write(valor));
    }

    @Test
    void listaDeDtoVoltaComOTipoCerto() {
        List<MonthlyCostResponse> original = List.of(
                new MonthlyCostResponse("2026-03", new BigDecimal("0")),
                new MonthlyCostResponse("2026-08", new BigDecimal("2482.84")));

        Object reconstruido = idaEVolta(original);

        assertInstanceOf(List.class, reconstruido);
        List<?> lista = (List<?>) reconstruido;
        assertInstanceOf(
                MonthlyCostResponse.class,
                lista.get(0),
                "sem informação de tipo no JSON isso volta como LinkedHashMap e quebra a resposta");
        assertEquals(original, reconstruido);
    }

    @Test
    void bigDecimalNaoPerdeValorNaIdaEVolta() {
        // Valor monetário virando double perderia centavos silenciosamente.
        List<MonthlyCostResponse> original =
                List.of(new MonthlyCostResponse("2026-07", new BigDecimal("4403.44")));

        Object reconstruido = idaEVolta(original);

        MonthlyCostResponse item = (MonthlyCostResponse) ((List<?>) reconstruido).get(0);
        assertEquals(0, new BigDecimal("4403.44").compareTo(item.total()));
    }
}
