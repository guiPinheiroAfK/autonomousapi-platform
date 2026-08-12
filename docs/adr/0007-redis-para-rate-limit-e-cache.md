# ADR 0007 — Redis para rate limit de login e cache de agregado

**Status:** aceito
**Data:** 2026-08-12

## Contexto

Dois problemas concretos no core-api:

1. **`POST /v1/auth/login` não tinha limite nenhum.** Dava para tentar senha à vontade
   contra qualquer e-mail. Isso é buraco de segurança presente, não risco futuro.
2. O dashboard recalcula agregados de custo de toda a frota a cada visita, e o gestor
   alterna entre telas com frequência.

## Decisão

Adotar Redis para as duas coisas.

### Por que não em memória

Um contador em memória funcionaria com **uma** instância. Com duas réplicas, o limite
efetivo dobra e um atacante só precisa distribuir as tentativas entre elas — o limite vira
teatro. Como rate limit só vale se valer para o serviço inteiro, o estado precisa ser
compartilhado. O mesmo raciocínio vale para o cache: com N réplicas e cache local, são N
cópias divergindo, cada uma expirando na sua hora.

Redis é o menor componente que resolve isso e não guarda nada insubstituível aqui: contador
de janela e agregado cacheado são descartáveis por definição. Por isso o serviço no compose
não tem volume — perder no restart só significa recontar ou recalcular.

### Decisões de implementação

- **Janela fixa, não deslizante.** O pior caso é permitir até 2x o limite na virada de duas
  janelas — irrelevante para conter força bruta, e evita manter um sorted set por chave.
- **TTL definido só na primeira tentativa da janela.** Renovar a cada tentativa deixaria um
  atacante contínuo empurrando a expiração para sempre.
- **Duas chaves independentes: por e-mail e por IP.** A primeira impede martelar uma conta
  específica de vários IPs; a segunda impede varrer muitos e-mails do mesmo lugar.
- **Chave de cache é o `tenantId`.** Nunca o principal inteiro: usuários do mesmo tenant
  devem compartilhar a entrada, e tenants diferentes JAMAIS podem colidir — uma chave sem
  tenant vazaria dado entre clientes.
- **TTL de cache curto (60s), com invalidação em escrita.** O dashboard não precisa ser
  transacional, mas não pode mostrar total velho depois que o gestor lançou um custo.

### Falha do Redis não pode virar falha do serviço

Os dois usos **degradam**, não quebram:

- Rate limiter com Redis fora **libera** a requisição (loga warning). Bloquear o login
  inteiro porque o Redis reiniciou seria trocar risco de abuso por indisponibilidade certa.
- Cache com Redis fora vai direto ao banco, via `CacheErrorHandler`.

> Cuidado que custou um teste vermelho: declarar um `@Bean CacheErrorHandler` **não** o
> registra no interceptor de cache. A classe de configuração precisa implementar
> `CachingConfigurer` e sobrescrever `errorHandler()`. Na primeira versão só havia o bean, e
> o teste de integração (sem Redis no ambiente) quebrou com `RedisConnectionFailureException`
> exatamente no endpoint que o cache deveria apenas acelerar.

## Consequências

- Mais um serviço no `infra/docker-compose.yml`. Sem ele, a aplicação sobe e funciona —
  só perde rate limit e cache.
- Timeout de conexão curto (300ms) porque o Redis aqui é acessório: se estiver lento, é
  melhor seguir sem ele do que arrastar a requisição do usuário.
- O ganho de cache medido no ambiente de demonstração foi modesto (16ms → 10ms), porque com
  65 lançamentos a query já é rápida. O valor real aparece com histórico grande — o cache
  entra agora principalmente para o padrão já estar no lugar quando isso acontecer.
