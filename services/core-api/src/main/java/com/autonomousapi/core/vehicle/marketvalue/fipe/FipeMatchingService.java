package com.autonomousapi.core.vehicle.marketvalue.fipe;

import com.autonomousapi.core.vehicle.VehicleType;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolve marca/modelo/ano do nosso cadastro para o código de catálogo da FIPE — o
 * "problema de matching à parte" citado na spec 08 item 2. Sem uma chave natural comum
 * entre os dois catálogos, a estratégia é: nome normalizado (sem acento/pontuação) +
 * match único. Ambíguo ou sem candidato = não resolve (fica manual) — a barra da spec é
 * "match confiável", não "algum match"; preferimos não preencher a arriscar preço errado.
 */
@Component
public class FipeMatchingService {

    /**
     * Só carro e moto têm categoria FIPE inequívoca. Van/caminhão/ônibus não mapeiam 1:1
     * para "carros" ou "caminhoes" da FIPE (o critério de corte deles não bate com o nosso
     * enum) — ficam de fora do auto-match, permanecem lançamento manual.
     */
    public Optional<String> tipoFipe(VehicleType tipo) {
        if (tipo == null) {
            return Optional.empty();
        }
        return switch (tipo) {
            case CARRO -> Optional.of("carros");
            case MOTO -> Optional.of("motos");
            default -> Optional.empty();
        };
    }

    public Optional<FipeMarca> matchMarca(List<FipeMarca> marcas, String brand) {
        String alvo = normalizar(brand);
        List<FipeMarca> candidatos = marcas.stream()
                .filter(m -> normalizar(m.nome()).contains(alvo))
                .toList();
        return candidatos.size() == 1 ? Optional.of(candidatos.get(0)) : Optional.empty();
    }

    public Optional<FipeModeloResumo> matchModelo(List<FipeModeloResumo> modelos, String model) {
        String alvo = normalizar(model);

        List<FipeModeloResumo> exatos =
                modelos.stream().filter(m -> normalizar(m.nome()).equals(alvo)).toList();
        if (exatos.size() == 1) {
            return Optional.of(exatos.get(0));
        }

        // Nome da FIPE costuma ser "<modelo base> <trim/motorização...>" — prefixo único do
        // nosso modelo (com espaço depois, pra não casar "CG" com "CG160" e "CGX" juntos).
        List<FipeModeloResumo> prefixados = modelos.stream()
                .filter(m -> normalizar(m.nome()).startsWith(alvo + " "))
                .toList();
        return prefixados.size() == 1 ? Optional.of(prefixados.get(0)) : Optional.empty();
    }

    /**
     * Escolhe o primeiro ano cujo código comece pelo ano-modelo. Quando existe mais de um
     * combustível para o mesmo ano (ex. "2022-1" gasolina, "2022-3" diesel), pega o
     * primeiro da lista — heurística v1, documentada: a diferença de preço entre
     * combustíveis costuma ser bem menor que entre versões/trims, então o risco de erro
     * grosseiro é baixo.
     */
    public Optional<FipeAno> matchAno(List<FipeAno> anos, Integer modelYear) {
        if (modelYear == null) {
            return Optional.empty();
        }
        String prefixo = modelYear + "-";
        return anos.stream().filter(a -> a.codigo().startsWith(prefixo)).findFirst();
    }

    static String normalizar(String s) {
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
    }
}
