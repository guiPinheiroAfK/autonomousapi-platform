<!--
Título da PR no padrão de commit: tipo(escopo): descrição
Escopos válidos: web, mobile, core-api, geo-api, contracts, infra, specs
-->

## O que mudou


## Por quê
<!-- o problema/decisão por trás da mudança, não o "o-quê" -->


## Como testar
<!-- passos para o revisor validar localmente -->


## Definition of Done
<!-- Linkar a spec relevante e marcar os itens da DoD cobertos por esta PR.
     Ex.: specs/01-arquitetura.md#definition-of-done-arquitetura-fase-1 -->
- [ ] Cobre item(ns) da DoD da spec: `specs/__.md`
- [ ] Sem segredo commitado (chaves/credenciais via secrets manager)
- [ ] Migrations versionadas (se tocou banco)
- [ ] Contrato OpenAPI atualizado (se mudou interface entre serviços)
- [ ] Testes adicionados/atualizados
- [ ] CI verde (lint + testes + build do escopo afetado)

## Checklist de revisão
- [ ] Branch nomeada `<tipo>/<escopo>-<descrição>` e sai de `develop`
- [ ] 1+ aprovação de outra pessoa do time
