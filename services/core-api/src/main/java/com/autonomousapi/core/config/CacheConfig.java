package com.autonomousapi.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cache de agregado no Redis (ADR 0007).
 *
 * TTL curto (60s) de propósito: o dashboard não precisa ser transacional, mas também não
 * pode mostrar um número visivelmente velho depois que o gestor lançou um custo. Um minuto
 * corta a rajada de recálculo de quem fica alternando entre telas, sem parecer travado.
 *
 * O cache é serializado em JSON, não com serialização Java: assim o valor no Redis é
 * legível para depurar e não quebra a cada mudança de classe.
 */
/*
 * IMPLEMENTA CachingConfigurer de propósito: declarar apenas um @Bean CacheErrorHandler
 * NÃO o registra no interceptor de cache do Spring — ele fica ignorado e a falha de Redis
 * volta a estourar na requisição. Isso não foi teoria: a primeira versão desta classe só
 * declarava o bean, e o teste de integração quebrou com RedisConnectionFailureException
 * justamente no endpoint que o cache deveria apenas acelerar.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String CACHE_COST_TREND = "costTrend";

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        /*
         * O JSON gravado PRECISA carregar a informação de tipo. Sem ela o valor volta do
         * Redis como LinkedHashMap em vez de List<MonthlyCostResponse>, e a resposta estoura
         * com "Could not write JSON: ClassCastException" — só no cache HIT, nunca no miss.
         *
         * DefaultTyping.EVERYTHING, e não o NON_FINAL usado pelo mapper padrão do
         * GenericJackson2JsonRedisSerializer: nossos DTOs são records, que são classes
         * FINAL. Com NON_FINAL o Jackson pula justamente eles na hora de escrever o tipo, e
         * a leitura falha com "expected VALUE_STRING: need ... that contains type id".
         *
         * O validador restringe a desserialização polimórfica aos nossos DTOs e a tipos
         * básicos; liberar Object.class inteiro abriria espaço para gadget chain caso
         * alguém conseguisse escrever no Redis.
         */
        BasicPolymorphicTypeValidator validador = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.autonomousapi.core.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.lang.")
                .build();

        ObjectMapper mapper = JsonMapper.builder()
                // DTO cacheado hoje só tem String/BigDecimal, mas se um com data entrar no
                // cache sem este módulo o Jackson quebra — barato deixar registrado.
                .addModule(new JavaTimeModule())
                .activateDefaultTyping(validador, ObjectMapper.DefaultTyping.EVERYTHING, JsonTypeInfo.As.PROPERTY)
                .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }

    /**
     * Redis fora do ar não pode derrubar endpoint que só queria acelerar. Com este handler,
     * falha de cache vira log e a chamada segue direto para o banco — o comportamento que
     * o serviço tinha antes de existir cache.
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, org.springframework.cache.Cache cache, Object key) {
                log.warn("Falha ao ler cache '{}' (seguindo para o banco): {}", cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCachePutError(
                    RuntimeException ex, org.springframework.cache.Cache cache, Object key, Object value) {
                log.warn("Falha ao gravar cache '{}': {}", cache.getName(), ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, org.springframework.cache.Cache cache, Object key) {
                log.warn("Falha ao invalidar cache '{}': {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
